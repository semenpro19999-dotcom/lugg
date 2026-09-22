package com.lugg.mod.ai;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
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

            // Список разных бесплатных провайдеров и моделей для перебора
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
                state.log("§e[" + (i+1) + "/" + providers.size() + "] " + p.name + "...");

                try {
                    List<Map<String, String>> messages = List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", prompt)
                    );

                    HttpResponse<String> response;
                    if (p.usePost) {
                        String body = GSON.toJson(Map.of(
                                "model", p.model,
                                "messages", messages,
                                "stream", false,
                                "temperature", 0.6,
                                "max_tokens", 2048
                        ));
                        HttpRequest.Builder rb = HttpRequest.newBuilder()
                                .uri(URI.create(p.url))
                                .header("Content-Type", "application/json")
                                .header("User-Agent", "Mozilla/5.0")
                                .header("Accept", "application/json")
                                .timeout(Duration.ofSeconds(35));
                        // Для OmniRoute публичный бесплатный ключ, для Pollinations заголовки сайта
                        if (p.url.contains("omniroute.ai")) {
                            rb.header("Authorization", "Bearer omni-78c32bf3c1");
                        } else {
                            rb.header("Origin", "https://pollinations.ai").header("Referer", "https://pollinations.ai/");
                        }
                        HttpRequest req = rb.POST(HttpRequest.BodyPublishers.ofString(body)).build();
                        response = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                    } else {
                        String full = systemPrompt + "\n\nЗАПРОС: " + prompt + "\nОТВЕЧАЙ ТОЛЬКО ЧИСТЫМ JSON!";
                        String enc = java.net.URLEncoder.encode(full, java.nio.charset.StandardCharsets.UTF_8);
                        HttpRequest req = HttpRequest.newBuilder()
                                .uri(URI.create(p.url + enc + "?model=" + p.model + "&json=true&seed=" + System.nanoTime()))
                                .header("User-Agent", "Mozilla/5.0")
                                .timeout(Duration.ofSeconds(30))
                                .GET().build();
                        response = HTTP_CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
                    }

                    state.log("§7HTTP статус: " + response.statusCode());

                    if (response.statusCode() == 200) {
                        String content = response.body();
                        state.log("§7Ответ " + content.length() + " байт");

                        if (p.usePost && content.trim().startsWith("{")) {
                            try {
                                JsonObject obj = GSON.fromJson(content, JsonObject.class);
                                if (obj.has("choices")) {
                                    content = obj.getAsJsonArray("choices").get(0).getAsJsonObject()
                                            .getAsJsonObject("message").get("content").getAsString();
                                }
                            } catch (Exception ignored) {}
                        }

                        content = content.trim()
                                .replaceAll("^```json\\s*", "")
                                .replaceAll("\\s*```$", "")
                                .trim();

                        int start = content.indexOf('{');
                        int end = content.lastIndexOf('}');
                        if (end > start && content.contains("\"actions\"")) {
                            content = content.substring(start, end+1);
                            state.log("§a✅ Найден валидный JSON с действиями!");
                            ParsedPlan plan = ActionParser.parse(content);
                            if (plan.parsedSuccessfully && (!plan.blocks.isEmpty() || !plan.instantActions.isEmpty())) {
                                state.log("§a✅ План готов! Блоков: " + plan.getTotalBlocks() + ", предметов: " + plan.getTotalItems());
                                state.readyPlan = plan;
                                state.requestInProgress = false;
                                state.finishedSuccessfully = true;
                                notifyDone();
                                return;
                            } else {
                                state.log("§cНет действий в плане");
                            }
                        } else {
                            state.log("§cНет JSON с actions, превью: " + content.substring(0, Math.min(80, content.length())).replace("\n", " "));
                        }
                    }
                    Thread.sleep(500);
                } catch (Exception e) {
                    state.log("§cОшибка: " + e.getMessage());
                    LuggMod.LOGGER.warn("[FreeAI] {}: {}", p.name, e.getMessage());
                }
            }

            if (ModConfig.getInstance().hasApiKey()) {
                state.log("§eПробую OpenRouter с твоим ключом...");
                OpenRouterClient.sendRequest(taskType, prompt, msg -> {
                    MinecraftClient mc = MinecraftClient.getInstance();
                    if (msg.startsWith("§a✅")) {
                        ParsedPlan plan = ActionParser.parse(msg.substring(3));
                        if (plan.parsedSuccessfully && (!plan.blocks.isEmpty() || !plan.instantActions.isEmpty())) {
                            state.log("§a✅ OpenRouter ответил!");
                            state.readyPlan = plan;
                            state.requestInProgress = false;
                            state.finishedSuccessfully = true;
                            notifyDone();
                            return;
                        }
                    }
                    state.log("§cOpenRouter не сработал");
                    state.requestInProgress = false;
                    state.failed = true;
                    notifyFail();
                });
                return;
            }

            state.log("§c❌ Все бесплатные серверы не ответили корректно. Попробуй позже, или добавь ключ OpenRouter.");
            state.requestInProgress = false;
            state.failed = true;
            notifyFail();
        }, "lugg-freeai").start();
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
        Provider(String n, String u, String m, boolean post) { name=n; url=u; model=m; usePost=post; }
    }
}
