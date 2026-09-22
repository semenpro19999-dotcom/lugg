package com.lugg.mod.client;

import com.lugg.mod.ai.building.BuildManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

/**
 * HUD который показывает статус 3D печати ВСЕГДА на экране даже когда меню закрыто.
 * Игрок может летать вокруг стройки и видеть прогресс.
 */
public class BuildHudOverlay {
    public static void register() {
        HudRenderCallback.EVENT.register((DrawContext context, float tickDelta) -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (!BuildManager.INSTANCE.isBuilding() || client.player == null) return;

            int screenW = client.getWindow().getScaledWidth();
            // Показываем в левом нижнем углу как чат, не сильно мешает обзору
            int x = 5;
            int y = client.getWindow().getScaledHeight() - 40;

            String line1 = String.format("§6[3D Принтер] Печать: %s", BuildManager.INSTANCE.getBuildName());
            String line2 = String.format("§fБлоков: %d/%d (%.1f%%)",
                    BuildManager.INSTANCE.getPlacedBlocks(),
                    BuildManager.INSTANCE.getPlacedBlocks() + BuildManager.INSTANCE.getRemainingBlocks(),
                    BuildManager.INSTANCE.getProgressPercent());
            String line3 = String.format("§fСкорость: %.1f блок/сек | Осталось примерно: %s",
                    BuildManager.INSTANCE.getSpeed(),
                    BuildManager.INSTANCE.getEstimatedTimeRemaining());
            String hint = "§7[Нажми J чтобы изменить скорость или отменить]";

            // Полупрозрачный фон
            int maxWidth = Math.max(
                    Math.max(client.textRenderer.getWidth(line1), client.textRenderer.getWidth(line2)),
                    Math.max(client.textRenderer.getWidth(line3), client.textRenderer.getWidth(hint))
            );
            context.fill(x-2, y-2, x + maxWidth + 2, y + 12*4 + 2, 0x80000000);

            context.drawTextWithShadow(client.textRenderer, Text.literal(line1), x, y, 0xFFFFFF);
            context.drawTextWithShadow(client.textRenderer, Text.literal(line2), x, y + 12, 0xFFFFFF);
            context.drawTextWithShadow(client.textRenderer, Text.literal(line3), x, y + 24, 0xFFFFFF);
            context.drawTextWithShadow(client.textRenderer, Text.literal(hint), x, y + 36, 0xAAAAAA);
        });
    }
}
