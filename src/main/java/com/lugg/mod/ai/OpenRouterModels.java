package com.lugg.mod.ai;

import java.util.List;

public class OpenRouterModels {
    public static final List<String> FREE_MODELS = List.of(
            "google/gemini-2.0-flash-exp:free",
            "google/gemma-3-27b-it:free",
            "meta-llama/llama-3.3-70b-instruct:free",
            "deepseek/deepseek-chat-v3-0324:free",
            "mistralai/mistral-small-3.1-24b-instruct:free",
            "qwen/qwen3-30b-a3b:free"
    );

    public static String getSystemPromptForTask(String taskType) {
        String rules = "СТРОГО: ОТВЕЧАЙ ТОЛЬКО ЧИСТЫМ JSON НИЧЕГО КРОМЕ JSON НИКАКИХ ОБЪЯСНЕНИЙ НИ ДО НИ ПОСЛЕ. Используй только ванильные блоки minecraft БЕЗ префикса minecraft:. Правильные названия блоков: oak_planks, stone, cobblestone, oak_log, oak_leaves, glass, glowstone, torch, redstone_wire, redstone_torch, oak_door, oak_stairs, oak_fence, crafting_table, furnace, chest, white_bed.";

        return switch (taskType) {
            case "build" -> rules + """
                     ЗАДАЧА: построить сооружение СРАЗУ перед игроком. Координата z=0 это 2 блока перед игроком.
                     Пример ОБЯЗАТЕЛЬНО:
                     {"narration":"Строю небольшой деревянный домик!","build_name":"Деревянный дом","actions":[{"type":"fill","x1":-2,"y1":0,"z1":0,"x2":2,"y2":0,"z2":4,"block":"oak_planks"},{"type":"fill","x1":-2,"y1":1,"z1":0,"x2":-2,"y2":3,"z2":4,"block":"oak_planks"},{"type":"fill","x1":2,"y1":1,"z1":0,"x2":2,"y2":3,"z2":4,"block":"oak_planks"},{"type":"fill","x1":-2,"y1":1,"z1":4,"x2":2,"y2":3,"z2":4,"block":"oak_planks"},{"type":"fill","x1":-1,"y1":4,"z1":0,"x2":1,"y2":4,"z2":4,"block":"oak_planks"},{"type":"fill","x1":-2,"y1":1,"z1":0,"x2":2,"y2":2,"z2":0,"block":"glass"},{"type":"setblock","x":0,"y":1,"z":-1,"block":"oak_door"},{"type":"setblock","x":0,"y":2,"z":2,"block":"torch"}]}
                    """;
            case "cutscene" -> rules + """
                     ЗАДАЧА: сделать короткую крутую катсцену прямо в игре.
                     Обязательно используй title для титров, playsound для атмосферных звуков, say для реплик рассказчика, wait для пауз между репликами. Не строй огромные объекты, максимум несколько эффектов.
                     Звуки: minecraft:entity.lightning_bolt.thunder, minecraft:music.creative, minecraft:entity.player.levelup, minecraft:ambient.cave, minecraft:block.note_block.pling
                     Пример: {"narration":"Запускаю эпичную катсцену!","build_name":"Катсцена","actions":[{"type":"title","text":"Древнее пророчество..."},{"type":"playsound","sound":"minecraft:ambient.cave"},{"type":"wait","ms":1500},{"type":"say","text":"Давным давно в этих землях жил великий воин..."},{"type":"wait","ms":3000},{"type":"title","text":"ТЫ - его наследник!"},{"type":"playsound","sound":"minecraft:entity.player.levelup"}]}
                    """;
            case "item" -> rules + """
                     ЗАДАЧА: создать/выдать запрошенный предмет игроку.
                     Правильные ID предметов: diamond_sword, netherite_pickaxe, golden_apple, enchanted_golden_apple, diamond_pickaxe, bow, arrow, trident, shield, elytra, diamond_chestplate, potion, apple, bread, cooked_beef.
                     Пример: {"narration":"Дарю тебе алмазный меч!","build_name":"Новый предмет","actions":[{"type":"title","text":"Ты получил легендарный меч!"},{"type":"playsound","sound":"minecraft:entity.player.levelup"},{"type":"give","item":"diamond_sword","count":1},{"type":"say","text":"Этот клинок выкован в глубинах Незера. Носи с честью!"}]}
                    """;
            case "voice" -> rules + """
                     ЗАДАЧА: озвучить текст игрока через реплики в чат.
                     Разбивай длинный текст на КОРОТКИЕ предложения по 5-8 слов отдельными действиями say, между ними делай wait паузы по 1000-2000 мс как в реальной речи.
                    """;
            case "idea" -> rules + """
                     ЗАДАЧА: придумать интересную идею для майнкрафта. Расскажи пару предложений через say, можно выдать стартовый набор предметов через give.
                    """;
            case "mechanism" -> rules + """
                     ЗАДАЧА: построить РАБОЧИЙ редстоун механизм перед игроком.
                     Блоки редстоуна ТОЛЬКО: redstone_wire (провод), redstone_torch (факел), repeater (повторитель), comparator, lever (рычаг), piston, sticky_piston, observer, hopper, dropper, dispenser, note_block.
                     ОБЯЗАТЕЛЬНО размещай рычаг чтобы игрок мог сам включать. Ставь повторители с правильной задержкой.
                     Пример простого механизма (автоматическая дверь): {"narration":"Строю автоматическую редстоун дверь!","build_name":"Авто-дверь","actions":[{"type":"fill","x1":-1,"y1":0,"z1":0,"x2":1,"y2":2,"z2":0,"block":"stone"},{"type":"setblock","x":-1,"y":1,"z":0,"block":"sticky_piston"},{"type":"setblock","x":1,"y":1,"z":0,"block":"sticky_piston"},{"type":"setblock","x":0,"y":0,"z":-1,"block":"oak_pressure_plate"},{"type":"setblock","x":-2,"y":1,"z":-1,"block":"redstone_torch"},{"type":"setblock","x":2,"y":1,"z":-1,"block":"redstone_torch"}]}
                    """;
            default -> rules;
        };
    }
}
