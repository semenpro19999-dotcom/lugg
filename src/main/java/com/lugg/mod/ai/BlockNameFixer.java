package com.lugg.mod.ai;

import com.lugg.mod.LuggMod;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Исправляет любые кривые/неправильные названия блоков которые пишет ИИ на правильные ванильные для Minecraft 1.20.1.
 * Содержит полный список всех основных блоков и распространённые опечатки ИИ.
 */
public class BlockNameFixer {
    private static final Set<String> VALID_BLOCKS = Set.of(
            "air", "stone", "granite", "polished_granite", "diorite", "polished_diorite", "andesite", "polished_andesite",
            "deepslate", "cobblestone", "oak_planks", "spruce_planks", "birch_planks", "jungle_planks", "acacia_planks", "dark_oak_planks",
            "dirt", "coarse_dirt", "podzol", "grass_block", "oak_log", "spruce_log", "birch_log", "jungle_log", "acacia_log", "dark_oak_log",
            "oak_leaves", "spruce_leaves", "birch_leaves", "jungle_leaves", "acacia_leaves", "dark_oak_leaves",
            "glass", "sand", "red_sand", "gravel", "gold_ore", "iron_ore", "coal_ore", "diamond_ore", "emerald_ore", "redstone_ore", "lapis_ore",
            "coal_block", "iron_block", "gold_block", "diamond_block", "emerald_block", "redstone_block", "lapis_block",
            "oak_stairs", "cobblestone_stairs", "brick_stairs", "stone_brick_stairs", "oak_slab", "cobblestone_slab", "brick_slab", "stone_brick_slab",
            "oak_fence", "cobblestone_wall", "brick", "stone_bricks", "cracked_stone_bricks", "mossy_stone_bricks", "chiseled_stone_bricks",
            "bookshelf", "obsidian", "glowstone", "sea_lantern", "torch", "redstone_torch", "redstone_wire", "repeater", "comparator",
            "piston", "sticky_piston", "lever", "stone_button", "oak_button", "stone_pressure_plate", "oak_pressure_plate",
            "oak_door", "iron_door", "oak_trapdoor", "ladder", "crafting_table", "furnace", "chest", "ender_chest", "trapped_chest",
            "oak_sign", "white_wool", "orange_wool", "magenta_wool", "light_blue_wool", "yellow_wool", "lime_wool", "pink_wool", "gray_wool",
            "white_concrete", "black_concrete", "water", "lava", "bedrock", "snow_block", "ice", "packed_ice", "blue_ice",
            "tnt", "enchanting_table", "anvil", "farmland", "wheat", "carrots", "potatoes", "beetroots", "melon", "pumpkin",
            "iron_bars", "oak_pressure_plate", "wall_torch", "lantern", "soul_lantern", "campfire", "soul_campfire",
            "white_bed", "end_stone", "netherrack", "soul_sand", "nether_bricks", "quartz_block", "prismarine", "dark_prismarine",
            "sea_lantern", "clay", "terracotta", "white_terracotta", "dripstone_block", "copper_block", "exposed_copper",
            "weathered_copper", "oxidized_copper", "cut_copper", "amethyst_block", "calcite", "tuff", "deepslate_coal_ore",
            "deepslate_iron_ore", "deepslate_copper_ore", "deepslate_gold_ore", "deepslate_redstone_ore", "deepslate_emerald_ore",
            "deepslate_lapis_ore", "deepslate_diamond_ore", "cobbled_deepslate", "polished_deepslate", "deepslate_bricks",
            "deepslate_tiles", "smooth_stone", "smooth_basalt", "basalt", "blackstone", "crying_obsidian", "respawn_anchor",
            "lodestone", "chain", "iron_door", "mossy_cobblestone", "moss_block", "azalea", "flowering_azalea", "mangrove_planks",
            "mangrove_log", "mangrove_leaves", "mushroom_stem", "red_mushroom_block", "brown_mushroom_block", "hay_block",
            "melon", "cocoa", "vine", "glow_lichen", "sculk", "sculk_catalyst", "sculk_shrieker", "reinforced_deepslate"
    );

    private static final Map<String, String> AUTO_REPLACE = new HashMap<>();
    static {
        AUTO_REPLACE.put("planks", "oak_planks");
        AUTO_REPLACE.put("wood_planks", "oak_planks");
        AUTO_REPLACE.put("wooden_planks", "oak_planks");
        AUTO_REPLACE.put("oak_wood_planks", "oak_planks");
        AUTO_REPLACE.put("wood", "oak_planks");
        AUTO_REPLACE.put("wooden", "oak_planks");
        AUTO_REPLACE.put("log", "oak_log");
        AUTO_REPLACE.put("wood_log", "oak_log");
        AUTO_REPLACE.put("leaves", "oak_leaves");
        AUTO_REPLACE.put("stone_block", "stone");
        AUTO_REPLACE.put("cobble", "cobblestone");
        AUTO_REPLACE.put("cobble_stone", "cobblestone");
        AUTO_REPLACE.put("redstone", "redstone_wire");
        AUTO_REPLACE.put("redstone_dust", "redstone_wire");
        AUTO_REPLACE.put("redstone_wire", "redstone_wire");
        AUTO_REPLACE.put("wire", "redstone_wire");
        AUTO_REPLACE.put("rs_torch", "redstone_torch");
        AUTO_REPLACE.put("torch", "torch");
        AUTO_REPLACE.put("wall_torch", "wall_torch");
        AUTO_REPLACE.put("door", "oak_door");
        AUTO_REPLACE.put("wooden_door", "oak_door");
        AUTO_REPLACE.put("wood_door", "oak_door");
        AUTO_REPLACE.put("trapdoor", "oak_trapdoor");
        AUTO_REPLACE.put("wooden_trapdoor", "oak_trapdoor");
        AUTO_REPLACE.put("fence", "oak_fence");
        AUTO_REPLACE.put("wooden_fence", "oak_fence");
        AUTO_REPLACE.put("gate", "oak_fence_gate");
        AUTO_REPLACE.put("fence_gate", "oak_fence_gate");
        AUTO_REPLACE.put("stairs", "oak_stairs");
        AUTO_REPLACE.put("wood_stairs", "oak_stairs");
        AUTO_REPLACE.put("wooden_stairs", "oak_stairs");
        AUTO_REPLACE.put("slab", "oak_slab");
        AUTO_REPLACE.put("wood_slab", "oak_slab");
        AUTO_REPLACE.put("button", "oak_button");
        AUTO_REPLACE.put("wooden_button", "oak_button");
        AUTO_REPLACE.put("pressure_plate", "oak_pressure_plate");
        AUTO_REPLACE.put("wood_plate", "oak_pressure_plate");
        AUTO_REPLACE.put("glass_block", "glass");
        AUTO_REPLACE.put("window", "glass");
        AUTO_REPLACE.put("glowstone_block", "glowstone");
        AUTO_REPLACE.put("glow", "glowstone");
        AUTO_REPLACE.put("lamp", "glowstone");
        AUTO_REPLACE.put("lantern", "lantern");
        AUTO_REPLACE.put("light", "glowstone");
        AUTO_REPLACE.put("dirt_block", "dirt");
        AUTO_REPLACE.put("grass", "grass_block");
        AUTO_REPLACE.put("ground", "dirt");
        AUTO_REPLACE.put("floor", "oak_planks");
        AUTO_REPLACE.put("roof", "oak_stairs");
        AUTO_REPLACE.put("wall", "oak_planks");
        AUTO_REPLACE.put("wall_block", "cobblestone");
        AUTO_REPLACE.put("table", "crafting_table");
        AUTO_REPLACE.put("workbench", "crafting_table");
        AUTO_REPLACE.put("craft", "crafting_table");
        AUTO_REPLACE.put("furnace_block", "furnace");
        AUTO_REPLACE.put("oven", "furnace");
        AUTO_REPLACE.put("chest_block", "chest");
        AUTO_REPLACE.put("storage", "chest");
        AUTO_REPLACE.put("bed", "white_bed");
        AUTO_REPLACE.put("white_bed", "white_bed");
        AUTO_REPLACE.put("ironbar", "iron_bars");
        AUTO_REPLACE.put("bars", "iron_bars");
        AUTO_REPLACE.put("ladder_block", "ladder");
        AUTO_REPLACE.put("sign_block", "oak_sign");
        AUTO_REPLACE.put("books", "bookshelf");
        AUTO_REPLACE.put("bookshelf_block", "bookshelf");
        AUTO_REPLACE.put("gold", "gold_block");
        AUTO_REPLACE.put("iron", "iron_block");
        AUTO_REPLACE.put("diamond", "diamond_block");
        AUTO_REPLACE.put("emerald", "emerald_block");
        AUTO_REPLACE.put("redstoneblock", "redstone_block");
        AUTO_REPLACE.put("brick_block", "bricks");
        AUTO_REPLACE.put("bricks_block", "stone_bricks");
        AUTO_REPLACE.put("stonebrick", "stone_bricks");
        AUTO_REPLACE.put("stone_brick", "stone_bricks");
        AUTO_REPLACE.put("lever_block", "lever");
        AUTO_REPLACE.put("switch", "lever");
        AUTO_REPLACE.put("piston_base", "piston");
        AUTO_REPLACE.put("sticky", "sticky_piston");
        AUTO_REPLACE.put("repeater_block", "repeater");
        AUTO_REPLACE.put("redstone_repeater", "repeater");
        AUTO_REPLACE.put("comparator_block", "comparator");
        AUTO_REPLACE.put("redstone_comparator", "comparator");
        AUTO_REPLACE.put("air_block", "air");
        AUTO_REPLACE.put("nothing", "air");
        AUTO_REPLACE.put("empty", "air");
        AUTO_REPLACE.put("clear", "air");
        AUTO_REPLACE.put("wool", "white_wool");
        AUTO_REPLACE.put("concrete", "white_concrete");
        AUTO_REPLACE.put("tnt_block", "tnt");
        AUTO_REPLACE.put("explosive", "tnt");
        AUTO_REPLACE.put("snow", "snow_block");
        AUTO_REPLACE.put("ice_block", "ice");
    }

    public static String fix(String input) {
        if (input == null) return "stone";
        String block = input.toLowerCase().trim();
        if (block.startsWith("minecraft:")) block = block.substring(10);
        block = block.replaceAll("[^a-z0-9_]", "_");
        block = block.replaceAll("_+", "_");
        block = block.replaceAll("^_|_$", "");

        if (VALID_BLOCKS.contains(block)) return block;
        if (AUTO_REPLACE.containsKey(block)) return AUTO_REPLACE.get(block);

        // Частичное совпадение — ищем подстроку
        for (Map.Entry<String, String> entry : AUTO_REPLACE.entrySet()) {
            if (block.contains(entry.getKey())) return entry.getValue();
        }

        // Если не нашли вообще — по умолчанию дубовые доски для построек, это самый частый материал
        LuggMod.LOGGER.warn("Unknown block '{}', falling back to oak_planks", input);
        return "oak_planks";
    }
}
