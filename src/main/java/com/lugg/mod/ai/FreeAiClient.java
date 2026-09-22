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

public class FreeAiClient {
    private static final String POLLINATIONS_URL = "https://text.pollinations.ai/openai";
    private static final Gson GSON = new Gson();
    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final List<String> MODELS = List.of(
            "openai", "gpt-4o-mini", "mistral-large", "llama", "deepseek-r1"
    );

    public static void sendRequest(String taskType, String prompt) {
        AiRequestState state = AiRequestState.INSTANCE;
        state.reset();
        state.requestInProgress = true;
        state.taskType = taskType;
        state.prompt = prompt;
        state.requestStartTime = System.currentTimeMillis();

        new Thread(() -> {
            String systemPrompt = OpenRouterModels.getSystemPromptForTask(taskType);
            state.log("§a=== Консоль запроса к ИИ ===");
            state.log("§7Задача: " + taskType);
            state.log("§7Запрос: " + prompt.substring(0, Math.min(100, prompt.length())));
            state.log("");

            for (int attempt = 0; attempt < MODELS.size(); attempt++) {
                String model = MODELS.get(attempt);
                state.log("§e[" + (attempt+1) + "/" + MODELS.size() + "] Пробую модель: " + model + "...");

                try {
                    List<Message> messages = List.of(
                            new Message("system", systemPrompt),
                            new Message("user", prompt)
                    );
                    String body = GSON.toJson(new ChatRequest(model, messages, false));

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create(POLLINATIONS_URL))
                            .header("Content-Type", "application/json")
                            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/125.0.0.0")
                            .header("Accept", "application/json")
                            .header("Origin", "https://pollinations.ai")
                            .header("Referer", "https://pollinations.ai/")
                            .timeout(Duration.ofSeconds(45))
                            .POST(HttpRequest.BodyPublishers.ofString(body))
                            .build();

                    state.log("§7Отправляю запрос...");
                    HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
                    state.log("§7Ответ HTTP: " + response.statusCode());

                    if (response.statusCode() == 200) {
                        String content = response.body();
                        state.log("§7Получен ответ длиной " + content.length() + " символов, парсю...");

                        try {
                            ChatResponse resp = GSON.fromJson(content, ChatResponse.class);
                            if (resp != null && resp.choices != null && !resp.choices.isEmpty()) {
                                content = resp.choices.get(0).message.content;
                            }
                        } catch (Exception ex) {
                            state.log("§7Не OpenAI формат, пробуем как сырой текст");
                        }

                        content = content.trim()
                                .replaceAll("^```json\\s*", "")
                                .replaceAll("\\s*```$", "")
                                .trim();

                        int start = content.indexOf('{');
                        int end = content.lastIndexOf('}');
                        if (end > start && content.contains("\"actions\"")) {
                            content = content.substring(start, end+1);
                            state.log("§aНайден JSON с планом действий!");

                            ParsedPlan plan = ActionParser.parse(content);
                            if (plan.parsedSuccessfully && (!plan.blocks.isEmpty() || !plan.instantActions.isEmpty())) {
                                state.log("§a✅ План разобран! Блоков: " + plan.getTotalBlocks() + ", предметов: " + plan.getTotalItems() + ", сообщений: " + plan.getTotalMessages());
                                state.readyPlan = plan;
                                state.requestInProgress = false;
                                state.finishedSuccessfully = true;
                                notifyDone();
                                return;
                            } else {
                                state.log("§cПлан не содержит действий, пробую следующую модель...");
                            }
                        } else {
                            state.log("§cВ ответе нет JSON с actions, превью: " + content.substring(0, Math.min(100, content.length())).replace("\n", " "));
                        }
                    } else {
                        state.log("§cОшибка HTTP " + response.statusCode());
                    }
                    Thread.sleep(800);
                } catch (Exception e) {
                    state.log("§cОшибка запроса: " + e.getMessage());
                    LuggMod.LOGGER.warn("[FreeAI] Ошибка {}: {}", model, e.getMessage());
                }
            }

            // Пробуем OpenRouter если есть ключ
            if (ModConfig.getInstance().hasApiKey()) {
                state.log("§eПробую OpenRouter с твоим ключом...");
                OpenRouterClient.sendRequest(taskType, prompt, msg -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (msg.startsWith("§a✅")) {
                        ParsedPlan plan = ActionParser.parse(msg.substring(3));
                        if (plan.parsedSuccessfully && (!plan.blocks.isEmpty() || !plan.instantActions.isEmpty())) {
                            state.log("§a✅ OpenRouter вернул план!");
                            state.readyPlan = plan;
                            state.requestInProgress = false;
                            state.finishedSuccessfully = true;
                            notifyDone();
                            return;
                        }
                    }
                    state.log("§cOpenRouter не сработал: " + msg);
                    state.requestInProgress = false;
                    state.failed = true;
                    state.errorMessage = "Все ИИ модели недоступны";
                    notifyFail();
                });
                return;
            }

            state.log("§c❌ Все модели закончились, запрос не удался.");
            state.requestInProgress = false;
            state.failed = true;
            state.errorMessage = "Не удалось получить ответ от ИИ";
            notifyFail();
        }, "lugg-freeai").start();
    }

    private static void notifyDone() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.execute(() -> {
                mc.player.sendMessage(Text.literal("§a✅ ИИ закончил генерацию плана! Нажми J чтобы посмотреть план и выполнить."), false);
                mc.player.playSound(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                mc.player.playSound(SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 2f);
            });
        }
    }

    private static void notifyFail() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player != null) {
            mc.execute(() -> {
                mc.player.sendMessage(Text.literal("§c❌ ИИ не смог составить план. Нажми J чтобы попробовать снова."), false);
                mc.player.playSound(SoundEvents.ENTITY_VILLAGER_NO, 1f, 1f);
            });
        }
    }

    private static class Message { String role, content; Message(String r, String c){role=r;content=c;} }
    private static class ChatRequest { String model; java.util.List<Message> messages; boolean stream; ChatRequest(String m, java.util.List<Message> msg, boolean s){model=m;messages=msg;stream=s;} }
    private static class ChatResponse { java.util.List<Choice> choices; }
    private static class Choice { Message message; }
}
