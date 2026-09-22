package com.lugg.mod.ai.building;

import com.lugg.mod.LuggMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Queue;

/**
 * Менеджер фоновой постройки как 3D принтер: строит блок за блоком слой за слоем снизу вверх.
 * Работает даже если меню закрыто, игрок может летать вокруг и смотреть.
 */
public class BuildManager {
    public static final BuildManager INSTANCE = new BuildManager();

    private Queue<BlockPlacement> pendingBlocks = new ArrayDeque<>();
    private int totalBlocks = 0;
    private int placedBlocks = 0;
    private double blocksPerSecond = 20; // скорость по умолчанию: 20 блоков/сек
    private long lastPlaceTime = 0;
    private boolean building = false;
    private String buildName = "";
    private long buildStartTime = 0;

    private BuildManager() {}

    /**
     * Начинает новую постройку из списка действий setblock/fill.
     * Сортирует все блоки по Y (снизу вверх), потом по X/Z как 3D принтер.
     */
    public void startBuilding(String name, List<BlockPlacement> blocks) {
        // Останавливаем предыдущую если есть
        cancelBuild();

        // Сортируем блоки слой за слоем снизу вверх как у 3D принтера
        List<BlockPlacement> safeBlocks = new ArrayList<>();
        for (BlockPlacement bp : blocks) {
            // Просто проверяем что координаты не безумные далеко от нуля
            if (Math.abs(bp.pos.getX()) > 30_000_000 || Math.abs(bp.pos.getY()) > 320 || Math.abs(bp.pos.getZ()) > 30_000_000) continue;
            if (bp.pos.getY() < -64) continue; // ниже коренной породы не строим
            safeBlocks.add(bp);
        }
        // Ограничим общий размер чтобы не крашить игру
        if (safeBlocks.size() > 15000) {
            safeBlocks = safeBlocks.subList(0, 15000);
        }
        safeBlocks.sort(Comparator.comparingInt((BlockPlacement b) -> b.pos.getY())
                .thenComparingInt(b -> b.pos.getX())
                .thenComparingInt(b -> b.pos.getZ()));

        pendingBlocks.addAll(safeBlocks);
        totalBlocks = safeBlocks.size();
        placedBlocks = 0;
        building = true;
        buildName = name;
        buildStartTime = System.currentTimeMillis();
        lastPlaceTime = 0;

        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(
                    Text.literal(String.format("§e[3D Принтер] Начинаю строить '%s' всего %d блоков.", name, totalBlocks)),
                    false);
        }
        LuggMod.LOGGER.info("[Build] Начинаем постройку '{}', всего блоков: {}", name, totalBlocks);
    }

    /**
     * Вызывается каждый клиентский тик чтобы поставить очередной блок если пора.
     */
    public void tick() {
        if (!building || pendingBlocks.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || client.getNetworkHandler() == null || client.player == null) return;

        long now = System.currentTimeMillis();
        long intervalMs = blocksPerSecond > 0 ? (long)(1000.0 / blocksPerSecond) : 1;

        // На низких скоростях ставим 1 блок за интервал, на высоких несколько за тик
        int blocksToPlaceThisTick;
        if (blocksPerSecond <= 20) {
            blocksToPlaceThisTick = (now - lastPlaceTime >= intervalMs) ? 1 : 0;
        } else {
            // Для высоких скоростей считаем сколько блоков накопилось по времени
            double elapsedSec = (now - lastPlaceTime) / 1000.0;
            blocksToPlaceThisTick = Math.min(pendingBlocks.size(), (int)(elapsedSec * blocksPerSecond) + 1);
        }

        for (int i = 0; i < blocksToPlaceThisTick && !pendingBlocks.isEmpty(); i++) {
            BlockPlacement block = pendingBlocks.poll();
            // Отправляем команду на установку блока относительно игрока в момент начала постройки
            String command = String.format("setblock %d %d %d %s",
                    block.pos.getX(), block.pos.getY(), block.pos.getZ(), block.blockName);
            client.getNetworkHandler().sendChatCommand(command);
            placedBlocks++;
        }

        if (blocksToPlaceThisTick > 0) {
            lastPlaceTime = now;
        }

        // Проверяем закончили ли
        if (pendingBlocks.isEmpty()) {
            building = false;
            long totalTimeSec = (System.currentTimeMillis() - buildStartTime) / 1000;
            client.player.sendMessage(
                    Text.literal(String.format("§a[3D Принтер] ✅ Постройка '%s' завершена! Построено %d блоков за %d секунд.",
                            buildName, totalBlocks, totalTimeSec)),
                    false);
            LuggMod.LOGGER.info("[Build] Постройка завершена за {} сек", totalTimeSec);
        }
    }

    public void setSpeed(double blocksPerSecond) {
        this.blocksPerSecond = Math.max(0.5, Math.min(1000, blocksPerSecond));
    }

    public double getSpeed() {
        return blocksPerSecond;
    }

    public boolean isBuilding() {
        return building;
    }

    public double getProgressPercent() {
        return totalBlocks == 0 ? 0 : (placedBlocks * 100.0 / totalBlocks);
    }

    public int getPlacedBlocks() {
        return placedBlocks;
    }

    public int getRemainingBlocks() {
        return pendingBlocks.size();
    }

    public String getEstimatedTimeRemaining() {
        if (!building || blocksPerSecond <= 0) return "...";
        double remainingSeconds = pendingBlocks.size() / blocksPerSecond;
        if (remainingSeconds < 60) return String.format("%.0f сек", remainingSeconds);
        if (remainingSeconds < 3600) return String.format("%.1f мин", remainingSeconds / 60);
        return String.format("%.1f ч", remainingSeconds / 3600);
    }

    public String getBuildName() {
        return buildName;
    }

    public void cancelBuild() {
        building = false;
        pendingBlocks.clear();
        totalBlocks = 0;
        placedBlocks = 0;
        if (MinecraftClient.getInstance().player != null) {
            MinecraftClient.getInstance().player.sendMessage(Text.literal("§c[3D Принтер] Постройка отменена"), false);
        }
    }

    public static class BlockPlacement {
        public final BlockPos pos;
        public final String blockName;
        public BlockPlacement(BlockPos pos, String blockName) {
            this.pos = pos;
            this.blockName = blockName;
        }
    }
}
