package com.lugg.mod.ai;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ItemNameFixer {
    private static final Set<String> VALID = Set.of(
            "diamond_sword","iron_sword","stone_sword","golden_sword","netherite_sword",
            "diamond_pickaxe","iron_pickaxe","stone_pickaxe","wooden_pickaxe","golden_pickaxe","netherite_pickaxe",
            "diamond_axe","iron_axe","stone_axe","wooden_axe","golden_axe","netherite_axe",
            "diamond_shovel","iron_shovel","stone_shovel","wooden_shovel","golden_shovel","netherite_shovel",
            "diamond_hoe","iron_hoe","stone_hoe","wooden_hoe","golden_hoe","netherite_hoe",
            "bow","crossbow","trident","shield","elytra","fishing_rod",
            "diamond_helmet","diamond_chestplate","diamond_leggings","diamond_boots",
            "iron_helmet","iron_chestplate","iron_leggings","iron_boots",
            "netherite_helmet","netherite_chestplate","netherite_leggings","netherite_boots",
            "leather_helmet","leather_chestplate","leather_leggings","leather_boots",
            "golden_helmet","golden_chestplate","golden_leggings","golden_boots",
            "apple","golden_apple","enchanted_golden_apple","bread","cooked_beef","steak","cooked_porkchop",
            "porkchop","carrot","potato","baked_potato","beetroot","melon_slice",
            "oak_planks","cobblestone","stone","glass","torch","ladder","crafting_table","furnace","chest",
            "redstone","redstone_torch","repeater","comparator","piston","sticky_piston","lever","observer",
            "tnt","diamond","emerald","iron_ingot","gold_ingot","coal","stick","redstone_block",
            "water_bucket","lava_bucket","bucket","flint_and_steel","compass","clock","map","book",
            "potion","splash_potion","arrow","tipped_arrow","spectral_arrow",
            "diamond_block","iron_block","gold_block","emerald_block","coal_block",
            "white_bed","oak_door","oak_trapdoor","oak_fence","oak_sign","oak_stairs","oak_slab"
    );

    private static final Map<String,String> FIX = new HashMap<>();
    static {
        FIX.put("sword","diamond_sword"); FIX.put("pickaxe","diamond_pickaxe"); FIX.put("axe","diamond_axe");
        FIX.put("shovel","diamond_shovel"); FIX.put("helmet","diamond_helmet"); FIX.put("chestplate","diamond_chestplate");
        FIX.put("leggings","diamond_leggings"); FIX.put("boots","diamond_boots"); FIX.put("gold_apple","golden_apple");
        FIX.put("notch_apple","enchanted_golden_apple"); FIX.put("food","cooked_beef"); FIX.put("meat","cooked_beef");
        FIX.put("food","cooked_beef"); FIX.put("steak","cooked_beef"); FIX.put("gold_apple","golden_apple");
    }

    public static String fix(String input) {
        if (input == null) return "diamond";
        String s = input.toLowerCase().trim().replace("minecraft:","").replaceAll("[^a-z0-9_]","_").replaceAll("_+","_").replaceAll("^_|_$","");
        if (VALID.contains(s)) return s;
        for (Map.Entry<String,String> e : FIX.entrySet()) {
            if (s.contains(e.getKey())) return e.getValue();
        }
        return "diamond";
    }
}
