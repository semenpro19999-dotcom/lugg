package com.lugg.mod.ai.actions;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.lugg.mod.LuggMod;
import com.lugg.mod.ai.building.BuildManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Результат парсинга ответа ИИ: список запланированных действий и текст для показа пользователю.
 */
public class ParsedPlan {
    public String narration = "";
    public String buildName = "Постройка ИИ";
    public final List<BuildManager.BlockPlacement> blocks = new ArrayList<>();
    public final List<InstantAction> instantActions = new ArrayList<>();
    public String rawAiText = "";
    public boolean parsedSuccessfully = false;
    public String errorMessage = "";

    public int getTotalBlocks() { return blocks.size(); }
    public int getTotalItems() {
        int count = 0;
        for (InstantAction a : instantActions) if ("give".equals(a.type)) count++;
        return count;
    }
    public int getTotalSummons() {
        int count = 0;
        for (InstantAction a : instantActions) if ("summon".equals(a.type)) count++;
        return count;
    }
    public int getTotalMessages() {
        int count = 0;
        for (InstantAction a : instantActions) if ("say".equals(a.type) || "voice".equals(a.type) || "narration".equals(a.type)) count++;
        return count;
    }

    public static class InstantAction {
        public final String type;
        public final JsonObject params;
        public final BlockPos origin;
        public InstantAction(String type, JsonObject params, BlockPos origin) {
            this.type = type;
            this.params = params;
            this.origin = origin;
        }
    }
}
