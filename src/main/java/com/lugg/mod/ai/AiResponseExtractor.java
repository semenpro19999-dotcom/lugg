package com.lugg.mod.ai;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * Полностью автономный разбор «сырых» ответов от бесплатных ИИ.
 * <p>
 * Умеет вытащить план-JSON из любого мусора, который могут вернуть провайдеры:
 * <ul>
 *   <li>OpenAI-обёртки {@code {"choices":[{"message":{"content":...}}]}} (строка или массив частей)</li>
 *   <li>Двойное кодирование: строка-JSON, экранированный JSON {@code {\"actions\":...}}</li>
 *   <li>Markdown-блоки ```json ... ``` и текстовые пояснения вокруг</li>
 *   <li>Обрезанный из-за max_tokens JSON (закрывает скобки и кавычки)</li>
 *   <li>Ошибки в теле ответа при HTTP 200, включая 429 rate limit</li>
 *   <li>Альтернативные ключа вместо {@code actions}: steps, commands, plan…</li>
 * </ul>
 */
public final class AiResponseExtractor {

    private AiResponseExtractor() {}

    /** Результат разбора: planJson ИЛИ error (+ флаг rate limit). */
    public static final class Outcome {
        public final String planJson;
        public final String error;
        public final boolean rateLimited;
        public final String note;

        private Outcome(String planJson, String error, boolean rateLimited, String note) {
            this.planJson = planJson;
            this.error = error;
            this.rateLimited = rateLimited;
            this.note = note;
        }

        public static Outcome ok(String planJson, String note) {
            return new Outcome(planJson, null, false, note);
        }

        public static Outcome fail(String error, boolean rateLimited, String note) {
            return new Outcome(null, error, rateLimited, note);
        }

        public boolean isSuccess() {
            return planJson != null;
        }
    }

    // ============================================================
    // Публичная точка входа
    // ============================================================

    /**
     * Разбирает сырое тело ответа (HTTP 200) и возвращает JSON плана либо ошибку.
     */
    public static Outcome extract(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            return Outcome.fail("Пустой ответ сервера", false, "тело пустое");
        }
        String body = rawBody;

        // 1) Ошибки провайдера в теле (бывает и при HTTP 200)
        String bodyErr = detectBodyError(body);
        if (bodyErr != null) {
            boolean rate = looksLikeRateLimit(bodyErr) || looksLikeRateLimit(body);
            return Outcome.fail(bodyErr, rate, "ошибка в теле ответа");
        }

        // 2) Снимаем OpenAI-обёртку / другие известные конверты
        String unwrapped = unwrapEnvelope(body);
        String note = unwrapped != null ? "снята API-обёртка" : null;
        if (unwrapped != null) body = unwrapped;

        // 3) Многоступенчатая нормализация текста
        body = normalizeText(body);

        // 4) Поиск плана в тексте (включая лечение обрезанного JSON)
        String plan = findPlanJson(body);
        if (plan != null) {
            return Outcome.ok(plan, note != null ? note + ", JSON найден" : "JSON найден");
        }

        // 5) План не найден — может, это текст про лимиты
        if (looksLikeRateLimit(body)) {
            return Outcome.fail("Сервер вернул лимит запросов (rate limit)", true, "rate limit в тексте");
        }

        String preview = body.replace('\n', ' ').trim();
        if (preview.length() > 120) preview = preview.substring(0, 120) + "…";
        return Outcome.fail("ИИ не вернул JSON с действиями", false, "превью: " + preview);
    }

    /**
     * Универсальный поиск JSON-плана в произвольном тексте.
     * Используется и ActionParser, и FreeAiClient.
     */
    public static String findPlanJson(String text) {
        if (text == null || text.isBlank()) return null;

        String trimmed = text.trim();

        // a) Текст сам является JSON-строкой-контейнером или экранированным JSON
        String deep = unwrapStringLayers(trimmed);
        if (deep != null) trimmed = deep;

        // b) Прямо валидный объект/массив — ищем внутри действия
        String direct = tryAsPlan(trimmed);
        if (direct != null) return direct;

        // c) Собираем все сбалансированные {...} из текста и выбираем лучший
        List<String> candidates = extractBalancedObjects(trimmed);
        String best = pickBestCandidate(candidates);
        if (best != null) return best;

        // d) Экранированный JSON внутри текста: {\"actions\":...}
        if (trimmed.contains("\\\"actions\\\"") || trimmed.contains("\\\"steps\\\"")) {
            String unescaped = trimmed.replace("\\\"", "\"").replace("\\n", "\n").replace("\\t", " ");
            String recovered = tryAsPlan(unescaped);
            if (recovered != null) return recovered;
            candidates = extractBalancedObjects(unescaped);
            best = pickBestCandidate(candidates);
            if (best != null) return best;
        }

        // e) Лечение обрезанного JSON (не хватило токенов)
        int start = trimmed.indexOf('{');
        if (start >= 0) {
            String repaired = closeTruncatedJson(trimmed.substring(start));
            String recovered = tryAsPlan(repaired);
            if (recovered != null) return recovered;
        }
        return null;
    }

    // ============================================================
    // Конверты / API-обёртки
    // ============================================================

    /** Если тело — известная API-обёртка, возвращает содержимое (текст ответа модели). */
    private static String unwrapEnvelope(String body) {
        String b = body.trim();
        if (!b.startsWith("{") && !b.startsWith("[")) return null;

        JsonElement root;
        try {
            root = JsonParser.parseString(b);
        } catch (Exception e) {
            // Может быть обрезанный JSON-конверт — пробуем как есть без обёртки
            return null;
        }

        if (root.isJsonObject()) {
            JsonObject obj = root.getAsJsonObject();

            // choices[].message.content / choices[].text (OpenAI / Pollinations / OmniRoute)
            String fromChoices = extractFromChoices(obj);
            if (fromChoices != null) return fromChoices;

            // Прочие частые конверты
            for (String key : new String[]{"response", "output_text", "output", "text", "content", "message", "result", "data"}) {
                if (!obj.has(key)) continue;
                JsonElement el = obj.get(key);
                String s = elementToString(el);
                if (s != null && !s.isBlank()) return s;
            }
            return null;
        }
        if (root.isJsonArray()) {
            // Массив действий на верхнем уровне — оставляем вызывающему коду
            return b;
        }
        return null;
    }

    private static String extractFromChoices(JsonObject obj) {
        try {
            if (!obj.has("choices") || !obj.get("choices").isJsonArray()) return null;
            JsonArray choices = obj.getAsJsonArray("choices");
            if (choices.size() == 0) return null;
            JsonElement first = choices.get(0);
            if (!first.isJsonObject()) return null;
            JsonObject ch = first.getAsJsonObject();

            if (ch.has("message") && ch.get("message").isJsonObject()) {
                JsonObject msg = ch.getAsJsonObject("message");
                String content = elementToString(msg.has("content") ? msg.get("content") : null);
                if (content != null && !content.isBlank()) return content;
                // Иногда ответ лежит в reasoning / reasoning_content
                for (String key : new String[]{"reasoning_content", "reasoning"}) {
                    String r = elementToString(msg.has(key) ? msg.get(key) : null);
                    if (r != null && !r.isBlank()) return r;
                }
            }
            // Старый формат completions: choices[].text
            String text = elementToString(ch.has("text") ? ch.get("text") : null);
            if (text != null && !text.isBlank()) return text;
        } catch (Exception ignored) {}
        return null;
    }

    /**
     * Превращает JsonElement в текст: строку как есть, массив частей — склейка text-частей.
     */
    private static String elementToString(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;
        if (el.isJsonPrimitive()) {
            JsonPrimitive p = el.getAsJsonPrimitive();
            if (p.isString()) return p.getAsString();
            return p.toString();
        }
        if (el.isJsonArray()) {
            // content: [{type:"text", text:"..."}, ...]
            StringBuilder sb = new StringBuilder();
            for (JsonElement part : el.getAsJsonArray()) {
                if (part.isJsonObject()) {
                    JsonObject po = part.getAsJsonObject();
                    String piece = null;
                    for (String key : new String[]{"text", "content", "value"}) {
                        if (po.has(key) && po.get(key).isJsonPrimitive()) {
                            piece = po.get(key).getAsString();
                            break;
                        }
                    }
                    if (piece != null) sb.append(piece);
                } else if (part.isJsonPrimitive()) {
                    sb.append(part.getAsString());
                }
            }
            return sb.length() > 0 ? sb.toString() : null;
        }
        if (el.isJsonObject()) {
            // Иногда content — объект вида {"value": "..."} / сам план
            return el.toString();
        }
        return null;
    }

    // ============================================================
    // Ошибки и rate limit
    // ============================================================

    /** Если тело — объект с ошибкой (без плана), возвращает текст ошибки. */
    private static String detectBodyError(String body) {
        String b = body.trim();
        if (!b.startsWith("{")) {
            // Плоский текст-ошибка
            String lower = b.toLowerCase(Locale.ROOT);
            if (lower.startsWith("error") || lower.startsWith("rate limit")
                    || lower.contains("too many requests") || lower.startsWith("429")) {
                String t = b.replace('\n', ' ');
                return t.length() > 200 ? t.substring(0, 200) + "…" : t;
            }
            return null;
        }
        try {
            JsonElement root = JsonParser.parseString(b);
            if (!root.isJsonObject()) return null;
            JsonObject obj = root.getAsJsonObject();

            boolean hasPlanSignals = obj.has("choices") || hasActionsKey(obj);
            if (hasPlanSignals) return null;

            if (obj.has("error") && !obj.get("error").isJsonNull()) {
                String msg = elementToString(obj.get("error"));
                if (msg == null || msg.isBlank()) msg = obj.get("error").toString();
                String t = msg.replace('\n', ' ');
                return t.length() > 200 ? t.substring(0, 200) + "…" : t;
            }
            if (obj.has("message") && !obj.has("actions") && looksLikeErrorObject(obj)) {
                String t = obj.get("message").getAsString().replace('\n', ' ');
                return t.length() > 200 ? t.substring(0, 200) + "…" : t;
            }
        } catch (Exception ignored) {}
        return null;
    }

    private static boolean looksLikeErrorObject(JsonObject obj) {
        if (obj.has("status") && obj.get("status").isJsonPrimitive()) {
            String s = obj.get("status").getAsString().toLowerCase(Locale.ROOT);
            if (s.contains("error") || s.contains("fail")) return true;
        }
        return obj.has("code") && !obj.has("narration") && !hasActionsKey(obj);
    }

    public static boolean looksLikeRateLimit(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(Locale.ROOT);
        return t.contains("rate limit")
                || t.contains("too many requests")
                || t.contains("слишком много запросов")
                || t.contains("превышен лимит")
                || (t.contains("429") && (t.contains("limit") || t.contains("quota") || t.contains("лимит")))
                || t.contains("quota exceeded");
    }

    /** Секунды ожидания из заголовка Retry-After, если он есть. */
    public static long retryAfterSeconds(java.net.http.HttpResponse<?> response, long def) {
        return response.headers().firstValue("retry-after")
                .map(v -> {
                    try {
                        return Math.max(1L, Long.parseLong(v.trim()));
                    } catch (NumberFormatException e) {
                        return def;
                    }
                })
                .orElse(def);
    }

    // ============================================================
    // Нормализация текста
    // ============================================================

    private static String normalizeText(String text) {
        if (text == null) return "";
        String s = text.trim();

        // BOM
        if (!s.isEmpty() && s.charAt(0) == '﻿') s = s.substring(1).trim();

        // Markdown-блоки ```json ... ``` — многократно на случай вложенных
        for (int i = 0; i < 3; i++) {
            String next = stripCodeFence(s);
            if (next.equals(s)) break;
            s = next;
        }
        s = s.trim();

        // Кавычки-ёлочки → обычные (ИИ любит так писать)
        s = s.replace('\u201C', '"').replace('\u201D', '"')
             .replace('\u2018', '\'').replace('\u2019', '\'');

        // Прямо JSON внутри строки-литерала
        String unwrapped = unwrapStringLayers(s);
        if (unwrapped != null) s = unwrapped;

        return s;
    }

    private static String stripCodeFence(String s) {
        // Полный блок ```json ... ``` или ``` ... ```
        int fence = s.indexOf("```");
        if (fence < 0) return s;
        // Найдём закрывающий fence после первого переноса
        int contentStart = s.indexOf('\n', fence);
        if (contentStart < 0) contentStart = fence + 3;
        else contentStart += 1;
        int close = s.indexOf("```", contentStart);
        if (close > contentStart) {
            return s.substring(contentStart, close).trim();
        }
        // Открытый fence без закрытия — просто убираем префикс
        String withoutOpen = s.substring(fence + 3);
        int nl = withoutOpen.indexOf('\n');
        if (withoutOpen.startsWith("json")) {
            withoutOpen = nl >= 0 ? withoutOpen.substring(nl + 1) : withoutOpen.substring(4);
        }
        return withoutOpen.trim();
    }

    /**
     * Снимает слои строкового кодирования: "\"{...}\"" или "{\"actions\":...}".
     * Возвращает нормализованный текст или null если раскодировать нечего.
     */
    private static String unwrapStringLayers(String s) {
        String cur = s.trim();
        for (int depth = 0; depth < 4; depth++) {
            if (cur.startsWith("\"") && cur.endsWith("\"") && cur.length() > 1) {
                try {
                    String inner = JsonParser.parseString(cur).getAsString();
                    if (inner.equals(cur)) break;
                    cur = inner.trim();
                    continue;
                } catch (Exception e) {
                    break;
                }
            }
            break;
        }
        return cur.equals(s.trim()) ? null : cur;
    }

    // ============================================================
    // Поиск и отбор JSON-кандидатов
    // ============================================================

    /** Пробует распознать строку как JSON-план; null если не план. */
    private static String tryAsPlan(String candidate) {
        if (candidate == null) return null;
        String c = candidate.trim();
        if (c.isEmpty()) return null;
        if (!(c.startsWith("{") || c.startsWith("["))) return null;

        JsonElement parsed;
        try {
            parsed = JsonParser.parseString(c);
        } catch (Exception e) {
            // Лечим хвостовую запятую и пробуем ещё раз
            String fixed = c.replaceAll(",\\s*([}\\]])", "$1");
            if (fixed.equals(c)) return null;
            try {
                parsed = JsonParser.parseString(fixed);
                c = fixed;
            } catch (Exception e2) {
                return null;
            }
        }

        JsonElement planNode = findActionsNode(parsed);
        if (planNode == null) return null;

        // Нормализуем к объекту с ключом actions
        if (planNode.isJsonArray()) {
            JsonObject wrap = new JsonObject();
            wrap.add("actions", planNode);
            return wrap.toString();
        }
        if (planNode.isJsonObject()) {
            JsonObject obj = planNode.getAsJsonObject();
            if (obj.has("actions")) return obj.toString();
            // Альтернативный ключ — перекладываем в actions
            for (String key : ACTION_ALT_KEYS) {
                if (obj.has(key) && obj.get(key).isJsonArray()) {
                    JsonObject norm = deepCopy(obj);
                    norm.add("actions", norm.remove(key));
                    return norm.toString();
                }
            }
            return obj.toString();
        }
        return null;
    }

    private static final String[] ACTION_ALT_KEYS = {"steps", "commands", "plan", "build_actions", "task_actions"};

    private static JsonObject deepCopy(JsonObject obj) {
        return obj.deepCopy();
    }

    /**
     * Ищет в дереве JSON узел, содержащий массив действий.
     * Поддерживает варианты: actions / steps / commands / …, а также вложенность
     * вроде {"result": {"actions": [...]}} и корневой массив [{type:...}].
     */
    public static JsonElement findActionsNode(JsonElement el) {
        if (el == null || el.isJsonNull()) return null;

        if (el.isJsonArray()) {
            JsonArray arr = el.getAsJsonArray();
            if (looksLikeActionsArray(arr)) return el;
            // Массив-обёртка: ищем объект с actions внутри первого элементе/по всем
            for (JsonElement child : arr) {
                JsonElement found = findActionsNode(child);
                if (found != null) return found;
            }
            return null;
        }

        if (!el.isJsonObject()) return null;
        JsonObject obj = el.getAsJsonObject();

        if (obj.has("actions") && obj.get("actions").isJsonArray()) return obj;
        for (String key : ACTION_ALT_KEYS) {
            if (obj.has(key) && obj.get(key).isJsonArray()
                    && looksLikeActionsArray(obj.getAsJsonArray(key))) {
                return obj;
            }
        }

        // Углубляемся: обход в ширину по значениям
        List<JsonElement> children = new ArrayList<>();
        for (var entry : obj.entrySet()) {
            if (entry.getValue() != null && !entry.getValue().isJsonNull()
                    && (entry.getValue().isJsonObject() || entry.getValue().isJsonArray())) {
                children.add(entry.getValue());
            }
        }
        for (JsonElement child : children) {
            JsonElement found = findActionsNode(child);
            if (found != null) return found;
        }
        return null;
    }

    private static boolean looksLikeActionsArray(JsonArray arr) {
        if (arr.size() == 0) return false;
        for (JsonElement e : arr) {
            if (e.isJsonObject() && e.getAsJsonObject().has("type")) return true;
        }
        return false;
    }

    private static boolean hasActionsKey(JsonObject obj) {
        if (obj.has("actions")) return true;
        for (String key : ACTION_ALT_KEYS) if (obj.has(key)) return true;
        return false;
    }

    /** Достаёт все сбалансированные {...} из текста (с учётом строк и экранирования). */
    private static List<String> extractBalancedObjects(String text) {
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        int n = text.length();
        for (int i = 0; i < n; i++) {
            if (text.charAt(i) != '{') continue;
            int end = matchBrace(text, i);
            if (end > i) {
                out.add(text.substring(i, end + 1));
                i = end; // не извлекаем вложенные повторно снаружи
            }
        }
        return out;
    }

    /** Индекс закрывающей скобки для { на позиции start, либо -1. */
    private static int matchBrace(String text, int start) {
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;
            if (c == '{' || c == '[') stack.push(c);
            else if (c == '}' || c == ']') {
                if (stack.isEmpty()) return -1;
                char open = stack.pop();
                if ((c == '}' && open != '{') || (c == ']' && open != '[')) return -1;
                if (stack.isEmpty()) return i;
            }
        }
        return -1;
    }

    private static String pickBestCandidate(List<String> candidates) {
        String best = null;
        int bestScore = 0;
        for (String cand : candidates) {
            int score = scoreCandidate(cand);
            if (score > bestScore) {
                bestScore = score;
                best = cand;
            }
        }
        return bestScore >= 50 ? best : null;
    }

    private static int scoreCandidate(String cand) {
        int score = 0;
        // Валидность — обязательно
        String normalized = null;
        try {
            JsonElement el = JsonParser.parseString(cand);
            if (findActionsNode(el) != null) {
                normalized = cand;
                score += 100;
            }
        } catch (Exception e) {
            String repaired = closeTruncatedJson(cand);
            try {
                JsonElement el = JsonParser.parseString(repaired);
                if (findActionsNode(el) != null) {
                    normalized = repaired;
                    score += 70; // чуть меньше за ремонт
                }
            } catch (Exception e2) {
                return 0;
            }
        }
        if (normalized == null) return 0;
        if (normalized.contains("\"narration\"")) score += 10;
        if (normalized.contains("\"build_name\"")) score += 5;
        score += Math.min(normalized.length() / 500, 10);
        return score;
    }

    // ============================================================
    // Лечение обрезанного JSON
    // ============================================================

    /**
     * Закрывает незакрытые скобки/кавычки и убирает висящие запятые.
     * Работает для JSON, обрезанного по лимиту токенов.
     */
    public static String closeTruncatedJson(String s) {
        if (s == null) return null;
        Deque<Character> stack = new ArrayDeque<>();
        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (inString) {
                if (c == '\\') escaped = true;
                else if (c == '"') inString = false;
                continue;
            }
            if (c == '"') { inString = true; continue; }
            if (c == '{' || c == '[') stack.push(c);
            else if (c == '}' || c == ']') {
                if (!stack.isEmpty()) stack.pop();
            }
        }

        StringBuilder sb = new StringBuilder(s);
        if (inString) sb.append('"');
        sb = new StringBuilder(sb.toString().replaceAll(",\\s*$", ""));
        while (!stack.isEmpty()) {
            char open = stack.pop();
            trimDanglingSeparator(sb);
            sb.append(open == '{' ? '}' : ']');
        }
        trimDanglingSeparator(sb);
        return sb.toString();
    }

    /**
     * Убирает висящие разделители и оборванные ключи без значения в конце JSON:
     * {@code ..., "block":} → убрать ключ и двоеточие; {@code ..., "block"} (нет {@code :}) → убрать ключ;
     * завершённое значение вроде {@code "stone"} (перед ним {@code :}) — не трогаем.
     */
    private static void trimDanglingSeparator(StringBuilder sb) {
        for (int guard = 0; guard < 8; guard++) {
            int i = sb.length() - 1;
            while (i >= 0 && Character.isWhitespace(sb.charAt(i))) i--;
            if (i < 0) return;
            char c = sb.charAt(i);
            if (c == ',' || c == ':') {
                sb.setLength(i);
                continue;
            }
            if (c == '"') {
                int strStart = findStringStart(sb, i);
                if (strStart < 0) return;
                // После строки не должно быть ':' — иначе это ключ с продолжением (маловероятно в конце)
                int j = i + 1;
                while (j < sb.length() && Character.isWhitespace(sb.charAt(j))) j++;
                if (j < sb.length() && sb.charAt(j) == ':') return;

                // Перед строкой: если ':' — это ЗНАЧЕНИЕ, оставляем; если '{'/',' — ключ без значения, удаляем
                int k = strStart - 1;
                while (k >= 0 && Character.isWhitespace(sb.charAt(k))) k--;
                if (k >= 0 && sb.charAt(k) == ':') return; // законченное значение

                int cut = strStart;
                if (k >= 0 && sb.charAt(k) == ',') cut = k;
                sb.setLength(Math.max(0, cut));
                continue;
            }
            // Цифра/true/false/null — законченное значение
            return;
        }
    }

    /** Начало строки-литерала, заканчивающейся в позиции endQuote (индекс закрывающей "). */
    private static int findStringStart(StringBuilder sb, int endQuote) {
        for (int i = endQuote - 1; i >= 0; i--) {
            if (sb.charAt(i) != '"') continue;
            // Нечётное число бэкслеши перед кавычкой => она экранирована, это не начало
            int bs = 0;
            int k = i - 1;
            while (k >= 0 && sb.charAt(k) == '\\') { bs++; k--; }
            if ((bs & 1) == 0) return i;
        }
        return -1;
    }
}
