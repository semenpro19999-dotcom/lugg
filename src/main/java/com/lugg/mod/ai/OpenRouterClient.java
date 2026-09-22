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

    /**
     * Отправляет запрос в OpenRouter, автоматически перебирает все бесплатные модели если одна не работает/не отвечает.
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
            Exception lastError = null;
            int totalModels = modelsToTry.size();

            for (int i = 0; i < modelsToTry.size(); i++) {
                String model = modelsToTry.get(i);
                String msg = "§eПробую модель " + (i+1) + "/" + totalModels + ": " + model.split("/")[1].replace(":free", "") + "...";
                onStatusUpdate.accept(msg);
                state.log(msg);
                LuggMod.LOGGER.info("[AI] Пробую модель: {}", model);

                try {
                    // Строим тело запроса в формате OpenAI совместимом
                    List<Map<String, String>> messages = new ArrayList<>();
                    messages.add(Map.of("role", "system", "content", systemPrompt));
                    messages.add(Map.of("role", "user", "content", prompt));

                    Map<String, Object> body = Map.of(
                            "model", model,
                            "messages", messages,
                            "temperature", 0.7,
                            "max_tokens", 2048
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

                    if (response.statusCode() == 200) {
                        Map responseMap = GSON.fromJson(response.body(), Map.class);
                        List choices = (List) responseMap.get("choices");
                        if (choices != null && !choices.isEmpty()) {
                            Map choice = (Map) choices.get(0);
                            Map message = (Map) choice.get("message");
                            String content = (String) message.get("content");
                            if (content != null && !content.isBlank()) {
                                onStatusUpdate.accept("§a✅" + content);
                                LuggMod.LOGGER.info("[AI] Успешный ответ от {}", model);
                                return;
                            }
                        }
                        throw new RuntimeException("Пустой ответ от модели");
                    } else {
                        lastError = new RuntimeException("Ошибка HTTP " + response.statusCode() + ": " + response.body().substring(0, Math.min(200, response.body().length())));
                        LuggMod.LOGGER.warn("[AI] Модель {} вернула ошибку: {}", model, lastError.getMessage());
                    }
                } catch (Exception e) {
                    lastError = e;
                    LuggMod.LOGGER.warn("[AI] Модель {} не сработала: {}", model, e.getMessage());
                }
            }

            onStatusUpdate.accept("§c❌ Все " + totalModels + " бесплатных моделей не ответили! Последняя ошибка: " + (lastError != null ? lastError.getMessage() : "неизвестно") + "\nПроверь интернет и ключ API, попробуй позже.");
        }, "lugg-ai-request").start();
    }
}
