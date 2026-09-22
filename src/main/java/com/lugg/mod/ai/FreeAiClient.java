package com.lugg.mod.ai;

import com.google.gson.Gson;
import com.lugg.mod.LuggMod;
import com.lugg.mod.ai.actions.ActionParser;
import com.lugg.mod.ai.actions.ParsedPlan;
import com.lugg.mod.client.AiRequestState;
import com.lugg.mod.config.ModConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

public class FreeAiClient {
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    /** Максимальное время ожидания после 429, сек. */
    private static final long MAX_RATE_WAIT_SEC = 20;

    public static void sendRequest(String taskType, String prompt) {
        AiRequestState state = AiRequestState.INSTANCE;
        state.reset();
        state.requestInProgress = true;
        state.taskType = taskType;
        state.prompt = prompt;
        state.requestStartTime = System.currentTimeMillis();

        new Thread(() -> {
            String systemPrompt = OpenRouterModels.getSystemPromptForTask(taskType);
            state.log("§a=== Консоль запроса к бесплатным ИИ ===");
            state.log("§7Задача: " + taskType);
            state.log("§7Запрос: " + prompt.substring(0, Math.min(100, prompt.length())));
            state.log("");

            List<Provider> providers = List.of(
                    new Provider("OmniRoute Llama 3.1 8b", "https://api.omniroute.ai/v1/chat/completions", "llama-3.1-8b-instruct", true),
                    new Provider("OmniRoute Mistral 7b", "https://api.omniroute.ai/v1/chat/completions", "mistral-7b-instruct", true),
                    new Provider("Pollinations OpenAI (gpt4o-mini)", "https://text.pollinations.ai/openai", "openai", true),
                    new Provider("Pollinations Mistral Large", "https://text.pollinations.ai/openai", "mistral-large", true),
                    new Provider("Pollinations Llama 3.3", "https://text.pollinations.ai/openai", "llama", true),
                    new Provider("Pollinations GET fallback", "https://text.pollinations.ai/", "openai", false)
            );

            for (int i = 0; i < providers.size(); i++) {
                Provider p = providers.get(i);
                state.log("§e[" + (i + 1) + "/" + providers.size() + "] " + p.name + "...");

                String planJson = attemptProvider(state, p, systemPrompt, prompt);
                if (planJson != null) {
                    ParsedPlan plan = ActionParser.parse(planJson);
                    if (plan.parsedSuccessfully && (!plan.blocks.isEmpty() || !plan.instantActions.isEmpty())) {
                        state.log("§a✅ План готов! Блоков: " + plan.getTotalBlocks() + ", предметов: " + plan.getTotalItems());
                        state.readyPlan = plan;
                        state.requestInProgress = false;
                        state.finishedSuccessfully = true;
                        notifyDone();
                        return;
                    }
                    state.log("§cJSON получен, но план пустой или невалидный: " + shorten(plan.errorMessage, 100));
                }
                safeSleep(400);
            }

            if (ModConfig.getInstance().hasApiKey()) {
                state.log("§eПробую OpenRouter с твоим ключом...");
                OpenRouterClient.sendRequest(taskType, prompt, msg -> {
                    // Промежуточные статусы («Пробую модель…», 429 и т.д.) — только в лог
                    if (msg.startsWith("§a✅")) {
                        ParsedPlan plan = ActionParser.parse(msg.substring(3));
                        if (plan.parsedSuccessfully && (!plan.blocks.isEmpty() || !plan.instantActions.isEmpty())) {
                            state.log("§a✅ OpenRouter ответил!");
                            state.readyPlan = plan;
                            state.requestInProgress = false;
                            state.finishedSuccessfully = true;
                            notifyDone();
                        } else {
                            state.log("§cOpenRouter: JSON некорректен или план пустой");
                            state.requestInProgress = false;
                            state.failed = true;
                            notifyFail();
                        }
                        return;
                    }
                    if (msg.startsWith("§c❌")) {
                        state.log(msg);
                        state.requestInProgress = false;
                        state.failed = true;
                        notifyFail();
                        return;
                    }
                    state.log(msg);
                });
                return;
            }

            state.log("§c❌ Все бесплатные серверы не ответили корректно. Попробуй позже, или добавь ключ OpenRouter.");
            state.requestInProgress = false;
            state.failed = true;
            notifyFail();
        }, "lugg-freeai").start();
    }

    /**
     * Один провайдер: до 2 попыток (повтор на 429/5xx/сетевые сбои).
     * Возвращает planJson при успехе либо null.
     */
    private static String attemptProvider(AiRequestState state, Provider p, String systemPrompt, String prompt) {
        final int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                HttpResponse<String> response = send(p, systemPrompt, prompt);
                int status = response.statusCode();
                String body = response.body() == null ? "" : response.body();
                state.log("§7HTTP статус: " + status + (attempt > 1 ? " (повтор " + attempt + "/" + maxAttempts + ")" : ""));

                // ---- 429 / rate limit: жду и повторяю тот же провайдер ----
                boolean rate = status == 429 || AiResponseExtractor.looksLikeRateLimit(body);
                if (rate) {
                    long waitSec = Math.min(
                            AiResponseExtractor.retryAfterSeconds(response, 3L * attempt),
                            MAX_RATE_WAIT_SEC);
                    state.log("§e⚠ 429 — лимит запросов, жду " + waitSec + "с и повторяю...");
                    safeSleep(waitSec * 1000);
                    continue; // следующая попытка того же провайдера
                }

                // ---- Временная ошибка сервера: один тихий повтор ----
                if (status >= 500 && attempt < maxAttempts) {
                    state.log("§e⚠ Сервер недоступен (" + status + "), повтор через 2с...");
                    safeSleep(2000);
                    continue;
                }

                if (status != 200) {
                    state.log("§cОшибка провайдера: HTTP " + status + " " + shorten(body.replace('\n', ' '), 150));
                    return null;
                }

                state.log("§7Ответ " + body.length() + " байт");

                // ---- Полностью новый разбор ответа ИИ ----
                AiResponseExtractor.Outcome outcome = AiResponseExtractor.extract(body);
                if (outcome.note != null) {
                    state.log("§7" + outcome.note);
                }
                if (outcome.isSuccess()) {
                    state.log("§a✅ Найден JSON с действиями!");
                    return outcome.planJson;
                }

                // Неудача разбора при HTTP 200
                if (outcome.rateLimited) {
                    long waitSec = Math.min(3L * attempt, MAX_RATE_WAIT_SEC);
                    state.log("§e⚠ ИИ ответил про лимит запросов, жду " + waitSec + "с...");
                    safeSleep(waitSec * 1000);
                    continue;
                }
                state.log("§cРазбор не удался: " + shorten(outcome.error, 120));
                if (outcome.note != null) state.log("§7" + shorten(outcome.note, 160));
                return null; // 200 но мусор — повтор не поможет, идём к следующему

            } catch (java.net.http.HttpTimeoutException te) {
                state.log("§cТаймаут запроса" + (attempt < maxAttempts ? ", повторяю..." : ", нет ответа"));
                if (attempt < maxAttempts) { safeSleep(1000); continue; }
            } catch (Exception e) {
                String msg = e.getMessage() != null && !e.getMessage().isBlank()
                        ? e.getMessage()
                        : e.getClass().getSimpleName();
                state.log("§cОшибка: " + msg);
                LuggMod.LOGGER.warn("[FreeAI] {} попытка {}: {}", p.name, attempt, msg, e);
                if (attempt < maxAttempts) { safeSleep(1000); continue; }
            }
        }
        return null;
    }

    private static HttpResponse<String> send(Provider p, String systemPrompt, String prompt) throws Exception {
        if (p.usePost) {
            List<Map<String, String>> messages = List.of(
                    Map.of("role", "system", "content", systemPrompt),
                    Map.of("role", "user", "content", prompt)
            );
            String body = GSON.toJson(Map.of(
                    "model", p.model,
                    "messages", messages,
                    "stream", false,
                    "temperature", 0.6,
                    "max_tokens", 4096
            ));
            HttpRequest.Builder rb = HttpRequest.newBuilder()
                    .uri(URI.create(p.url))
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0")
                    .header("Accept", "application/json")
                    .timeout(Duration.ofSeconds(45));
            if (p.url.contains("omniroute.ai")) {
                rb.header("Authorization", "Bearer omni-78c32bf3c1");
            } else {
                rb.header("Origin", "https://pollinations.ai").header("Referer", "https://pollinations.ai/");
            }
            HttpRequest req = rb.POST(HttpRequest.BodyPublishers.ofString(body)).build();
            return HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        }

        String full = systemPrompt + "\n\nЗАПРОС: " + prompt + "\nОТВЕЧАЙ ТОЛЬКО ЧИСТЫМ JSON!";
        String enc = java.net.URLEncoder.encode(full, java.nio.charset.StandardCharsets.UTF_8);
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(p.url + enc + "?model=" + p.model + "&json=true&seed=" + System.nanoTime()))
                .header("User-Agent", "Mozilla/5.0")
                .timeout(Duration.ofSeconds(40))
                .GET().build();
        return HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
    }

    private static void safeSleep(long ms) {
        try { Thread.sleep(Math.max(0, ms)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static String shorten(String s, int max) {
        if (s == null) return "";
        String t = s.replace('\n', ' ');
        return t.length() > max ? t.substring(0, max) + "…" : t;
    }

    private static void notifyDone() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.execute(() -> {
            mc.player.sendMessage(Text.literal("§a✅ ИИ закончил! Нажми J чтобы посмотреть план и выполнить."), false);
            mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            mc.player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 2f);
        });
    }

    private static void notifyFail() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) mc.execute(() -> {
            mc.player.sendMessage(Text.literal("§c❌ ИИ не смог составить план. Нажми J чтобы попробовать снова."), false);
            mc.player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1f, 1f);
        });
    }

    private static class Provider {
        String name, url, model;
        boolean usePost;
        Provider(String n, String u, String m, boolean post) { name = n; url = u; model = m; usePost = post; }
    }
}
