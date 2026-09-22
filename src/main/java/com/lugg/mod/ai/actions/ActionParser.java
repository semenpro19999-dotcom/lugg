package com.lugg.mod.ai.actions;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import com.lugg.mod.LuggMod;
import com.lugg.mod.ai.BlockNameFixer;
import com.lugg.mod.ai.ItemNameFixer;
import com.lugg.mod.ai.building.BuildManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Очень устойчивый парсер ответа ИИ который вытаскивает JSON из любого текста,
 * даже если ИИ добавил объяснения до/после, использует кавычки-ёлочки, пропустил запятые и т.д.
 */
public class ActionParser {

    public static ParsedPlan parse(String aiResponse) {
        ParsedPlan plan = new ParsedPlan();
        plan.rawAiText = aiResponse;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) {
            plan.errorMessage = "Ты не в игре!";
            return plan;
        }

        String jsonStr = extractJsonObject(aiResponse);
        if (jsonStr == null) {
            plan.errorMessage = "ИИ не вернул план в формате JSON.";
            return plan;
        }

        try {
            JsonObject json = JsonParser.parseString(jsonStr).getAsJsonObject();

            if (json.has("narration")) plan.narration = json.get("narration").getAsString();
            if (json.has("build_name")) plan.buildName = json.get("build_name").getAsString();

            // Фиксируем позицию игрока В МОМЕНТ ПАРСИНГА (т.е. после того как ИИ ответил, ровно там где стоит игрок)
            BlockPos buildOrigin = client.player.getBlockPos().add(0, 0, 2);

            if (json.has("actions")) {
                JsonArray actionsArr = json.getAsJsonArray("actions");
                for (int i = 0; i < actionsArr.size(); i++) {
                    JsonObject act = actionsArr.get(i).getAsJsonObject();
                    if (!act.has("type")) continue;
                    String type = act.get("type").getAsString();

                    switch (type) {
                        case "setblock" -> {
                            if (act.has("x") && act.has("y") && act.has("z") && act.has("block")) {
                                int x = act.get("x").getAsInt();
                                int y = act.get("y").getAsInt();
                                int z = act.get("z").getAsInt();
                                String block = BlockNameFixer.fix(act.get("block").getAsString());
                                plan.blocks.add(new BuildManager.BlockPlacement(
                                        new BlockPos(buildOrigin.getX() + x, buildOrigin.getY() + y, buildOrigin.getZ() + z),
                                        block));
                            }
                        }
                        case "fill" -> {
                            if (act.has("x1") && act.has("y1") && act.has("z1") && act.has("x2") && act.has("y2") && act.has("z2") && act.has("block")) {
                                int x1 = act.get("x1").getAsInt();
                                int y1 = act.get("y1").getAsInt();
                                int z1 = act.get("z1").getAsInt();
                                int x2 = act.get("x2").getAsInt();
                                int y2 = act.get("y2").getAsInt();
                                int z2 = act.get("z2").getAsInt();
                                String block = BlockNameFixer.fix(act.get("block").getAsString());
                                int minX = Math.min(x1, x2), maxX = Math.max(x1, x2);
                                int minY = Math.min(y1, y2), maxY = Math.max(y1, y2);
                                int minZ = Math.min(z1, z2), maxZ = Math.max(z1, z2);

                                // Ограничиваем максимальный размер чтобы не повесить игру
                                int volume = (maxX-minX+1)*(maxY-minY+1)*(maxZ-minZ+1);
                                if (volume > 50000) {
                                    LuggMod.LOGGER.warn("Слишком большая fill область: {} блоков, ограничиваю", volume);
                                    maxY = minY + 30;
                                }

                                for (int bx = minX; bx <= maxX; bx++) {
                                    for (int by = minY; by <= maxY; by++) {
                                        for (int bz = minZ; bz <= maxZ; bz++) {
                                            plan.blocks.add(new BuildManager.BlockPlacement(
                                                    new BlockPos(buildOrigin.getX()+bx, buildOrigin.getY()+by, buildOrigin.getZ()+bz),
                                                    block));
                                        }
                                    }
                                }
                            }
                        }
                        default -> plan.instantActions.add(new ParsedPlan.InstantAction(type, act, buildOrigin));
                    }
                }
            }

            plan.parsedSuccessfully = true;
        } catch (JsonSyntaxException | IllegalStateException e) {
            plan.errorMessage = "Ошибка разбора ответа ИИ: " + e.getMessage() + "\nПопробуй отправить запрос ещё раз.";
            LuggMod.LOGGER.error("[Parser] Ошибка парсинга JSON: {}", jsonStr, e);
        }

        return plan;
    }

    /**
     * Выполняет все запланированные действия.
     */
    public static void executePlan(ParsedPlan plan) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || !plan.parsedSuccessfully) return;

        if (!plan.narration.isEmpty()) {
            client.player.sendMessage(Text.literal("§e[ИИ] " + plan.narration), false);
        }

        if (!plan.blocks.isEmpty()) {
            BuildManager.INSTANCE.startBuilding(plan.buildName, plan.blocks);
        }

        runInstantActions(plan.instantActions, 0);
    }

    private static void runInstantActions(java.util.List<ParsedPlan.InstantAction> actions, int index) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (index >= actions.size() || client.player == null) return;
        ParsedPlan.InstantAction a = actions.get(index);
        long delay = executeSingleInstant(a);

        new Thread(() -> {
            try { Thread.sleep(Math.max(0, delay)); }
            catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            client.execute(() -> runInstantActions(actions, index + 1));
        }).start();
    }

    private static long executeSingleInstant(ParsedPlan.InstantAction a) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.getNetworkHandler() == null) return 0;
        try {
            JsonObject act = a.params;
            return switch (a.type) {
                case "give" -> {
                    String item = ItemNameFixer.fix(act.get("item").getAsString());
                    int count = act.has("count") ? Math.max(1, Math.min(64, act.get("count").getAsInt())) : 1;
                    client.getNetworkHandler().sendChatCommand(String.format("give @p minecraft:%s %d", item, count));
                    yield 5L;
                }
                case "summon" -> {
                    String entity = act.get("entity").getAsString().toLowerCase()
                            .replace("minecraft:", "").replaceAll("[^a-z0-9_]", "_");
                    if (entity.isBlank()) entity = "cow";
                    int x = act.has("x") ? act.get("x").getAsInt() : 0;
                    int y = act.has("y") ? act.get("y").getAsInt() : 0;
                    int z = act.has("z") ? act.get("z").getAsInt() : 0;
                    client.getNetworkHandler().sendChatCommand(String.format("summon %s %d %d %d",
                            entity, a.origin.getX()+x, a.origin.getY()+y, a.origin.getZ()+z));
                    yield 15L;
                }
                case "say", "voice", "narration" -> {
                    String text = act.get("text").getAsString().replace("\"", "");
                    if (text.length() > 200) text = text.substring(0, 200);
                    client.player.sendMessage(Text.literal("§d[Рассказчик] §f" + text), false);
                    yield (long)Math.max(500, text.length() * 50);
                }
                case "title" -> {
                    String text = act.get("text").getAsString().replace("\"", "");
                    client.getNetworkHandler().sendChatCommand(String.format("title @p title {\"text\":\"%s\"}", text));
                    yield 2000L;
                }
                case "playsound" -> {
                    String sound = act.get("sound").getAsString().toLowerCase().replace("minecraft:", "");
                    if (!sound.contains(":")) sound = "minecraft:" + sound;
                    client.getNetworkHandler().sendChatCommand(String.format("playsound %s master @p ~ ~ ~ 1 1", sound));
                    yield 100L;
                }
                case "wait", "delay" -> {
                    long ms = act.has("ms") ? act.get("ms").getAsLong() : 1000L;
                    yield Math.max(100, Math.min(10000, ms));
                }
                case "teleport" -> {
                    double x = act.get("x").getAsDouble();
                    double y = act.get("y").getAsDouble();
                    double z = act.get("z").getAsDouble();
                    net.minecraft.util.math.Vec3d pos = client.player.getPos();
                    client.getNetworkHandler().sendChatCommand(String.format("tp @p %.1f %.1f %.1f", pos.x+x, pos.y+y, pos.z+z));
                    yield 100L;
                }
                default -> 0L;
            };
        } catch (Exception e) {
            LuggMod.LOGGER.error("Ошибка мгновенного действия", e);
            return 0;
        }
    }

    /**
     * Вытаскивает JSON объект из любого текста, даже если вокруг есть лишний текст.
     * Умеет находить сбалансированные фигурные скобки, игнорирует ```json блоки.
     */
    private static String extractJsonObject(String text) {
        // Сначала вырезаем ```json ... ```
        Pattern codeBlock = Pattern.compile("```(?:json)?\\s*(\\{.*?})\\s*```", Pattern.DOTALL);
        Matcher m = codeBlock.matcher(text);
        if (m.find()) return m.group(1);

        // Ищем первую { и соответствующую ей сбалансированную }
        int start = text.indexOf('{');
        if (start < 0) return null;

        Stack<Character> stack = new Stack<>();
        boolean inString = false;
        boolean escaped = false;
        for (int i = start; i < text.length(); i++) {
            char c = text.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') inString = !inString;
            if (inString) continue;

            if (c == '{' || c == '[') stack.push(c);
            else if (c == '}') {
                if (stack.isEmpty() || stack.pop() != '{') break;
                if (stack.isEmpty()) {
                    // Нашли сбалансированный блок
                    String candidate = text.substring(start, i+1);
                    // Проверяем что это валидный JSON
                    try {
                        JsonParser.parseString(candidate);
                        return candidate;
                    } catch (Exception ignored) {}
                }
            } else if (c == ']') {
                if (!stack.isEmpty() && stack.peek() == '[') stack.pop();
            }
        }
        return null;
    }

}
