package com.lugg.mod.ai;

import com.google.gson.Gson;
import com.lugg.mod.config.ModConfig;
import com.lugg.mod.LuggMod;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public class OpenRouterClient {
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final long MAX_RATE_WAIT_SEC = 20;

    /**
     * Отправляет запрос в OpenRouter, автоматически перебирает все бесплатные модели если одна не работает/не отвечает.
     * Умеет повторять на 429 (rate limit) с учётом Retry-After.
     * @param taskType тип задачи
     * @param prompt текст запроса от игрока
     * @param onStatusUpdate вызывается с статусом/ошибкой/финальным ответом для обновления UI
     */
    public static void sendRequest(String taskType, String prompt, Consumer<String> onStatusUpdate) {
        com.lugg.mod.client.AiRequestState state = com.lugg.mod.client.AiRequestState.INSTANCE;
        new Thread(() -> {
            ModConfig config = ModConfig.getInstance();
            if (!config.hasApiKey()) {
                onStatusUpdate.accept("§c❌ Сначала введи API ключ от OpenRouter! Зайди в настройки (кнопка ⚙ Настройки в главном меню)");
                return;
            }

            String systemPrompt = OpenRouterModels.getSystemPromptForTask(taskType);

            List<String> modelsToTry = OpenRouterModels.FREE_MODELS;
            int totalModels = modelsToTry.size();
            String lastErrorText = null;

            for (int i = 0; i < modelsToTry.size(); i++) {
                String model = modelsToTry.get(i);
                String shortName = model.contains("/") ? model.split("/")[1].replace(":free", "") : model;
                try {
                    String content = tryModel(state, model, shortName, systemPrompt, prompt, config, onStatusUpdate);
                    if (content != null) {
                        onStatusUpdate.accept("§a✅" + content);
                        LuggMod.LOGGER.info("[AI] Успешный ответ от {}", model);
                        return;
                    }
                    lastErrorText = "модель " + shortName + " не ответила (лимит/мусор)";
                } catch (Exception e) {
                    lastErrorText = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                    LuggMod.LOGGER.warn("[AI] Модель {} упала: {}", model, lastErrorText, e);
                }
            }

            onStatusUpdate.accept("§c❌ Все " + totalModels + " бесплатных моделей не ответили! Последняя ошибка: "
                    + (lastErrorText != null ? lastErrorText : "лимиты/сетевые сбои")
                    + "\nПроверь интернет и ключ API, попробуй позже.");
            state.log("§cOpenRouter: все модели исчерпаны");
        }, "lugg-ai-request").start();
    }

    /**
     * Один запрос к модели с повторами на 429/5xx.
     * @return контент ответа или null
     */
    private static String tryModel(AiRequestState state, String model, String shortName,
                                   String systemPrompt, String prompt,
                                   ModConfig config, Consumer<String> onStatusUpdate) {
        final int maxAttempts = 2;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            String msg = "§eПробую модель: " + shortName
                    + (attempt > 1 ? " (повтор " + attempt + ")" : "") + "...";
            onStatusUpdate.accept(msg);
            state.log(msg);

            try {
                List<Map<String, String>> messages = new ArrayList<>();
                messages.add(Map.of("role", "system", "content", systemPrompt));
                messages.add(Map.of("role", "user", "content", prompt));

                Map<String, Object> body = Map.of(
                        "model", model,
                        "messages", messages,
                        "temperature", 0.7,
                        "max_tokens", 4096
                );

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(API_URL))
                        .header("Authorization", "Bearer " + config.openrouterApiKey.trim())
                        .header("Content-Type", "application/json")
                        .header("User-Agent", "Minecraft-LuggMod/1.2")
                        .header("HTTP-Referer", "https://github.com/semenpro19999-dotcom/lugg")
                        .header("X-Title", "Lugg AI Minecraft Mod")
                        .timeout(Duration.ofSeconds(config.aiTimeoutSeconds))
                        .POST(HttpRequest.BodyPublishers.ofString(GSON.toJson(body)))
                        .build();

                HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                String respBody = response.body() == null ? "" : response.body();

                // ---- 429 / rate limit ----
                if (status == 429 || AiResponseExtractor.looksLikeRateLimit(respBody)) {
                    long waitSec = Math.min(
                            AiResponseExtractor.retryAfterSeconds(response, 3L * attempt),
                            MAX_RATE_WAIT_SEC);
                    state.log("§e⚠ 429 лимит на " + shortName + ", жду " + waitSec + "с...");
                    safeSleep(waitSec * 1000);
                    continue;
                }

                if (status >= 500 && attempt < maxAttempts) {
                    state.log("§e⚠ Сервер OpenRouter недоступен (" + status + "), повтор...");
                    safeSleep(2000);
                    continue;
                }

                if (status != 200) {
                    String snippet = respBody.replace('\n', ' ');
                    if (snippet.length() > 200) snippet = snippet.substring(0, 200) + "…";
                    state.log("§c" + shortName + ": HTTP " + status + " " + snippet);
                    LuggMod.LOGGER.warn("[AI] Модель {} вернула ошибку {}: {}", model, status, snippet);
                    return null;
                }

                // ---- Разбор ответа новым экстрактором (OpenAI-обёртка, ошибки в теле и т.д.) ----
                AiResponseExtractor.Outcome outcome = AiResponseExtractor.extract(respBody);
                if (outcome.isSuccess()) {
                    return outcome.planJson;
                }
                if (outcome.rateLimited && attempt < maxAttempts) {
                    state.log("§e⚠ Лимит запросов (" + shortName + "), повтор через 5с...");
                    safeSleep(5000);
                    continue;
                }
                state.log("§c" + shortName + ": " + (outcome.error != null ? outcome.error : "пустой ответ"));
                return null;

            } catch (java.net.http.HttpTimeoutException te) {
                state.log("§c" + shortName + ": таймаут");
                if (attempt < maxAttempts) { safeSleep(1000); continue; }
            } catch (Exception e) {
                String m = e.getMessage() != null && !e.getMessage().isBlank()
                        ? e.getMessage() : e.getClass().getSimpleName();
                state.log("§c" + shortName + ": " + m);
                LuggMod.LOGGER.warn("[AI] Модель {} не сработала: {}", model, m, e);
                if (attempt < maxAttempts) { safeSleep(1000); continue; }
            }
        }
        return null;
    }

    private static void safeSleep(long ms) {
        try { Thread.sleep(Math.max(0, ms)); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
