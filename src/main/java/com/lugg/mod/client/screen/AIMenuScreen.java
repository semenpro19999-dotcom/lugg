package com.lugg.mod.client.screen;

import com.lugg.mod.ai.building.BuildManager;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

public class AIMenuScreen extends Screen {

    private static final int BUTTON_WIDTH = 280;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 4;
    private SpeedSlider speedSlider;

    public AIMenuScreen() {
        super(Text.literal("Lugg AI — ИИ-помощник в Minecraft"));
    }

    @Override
    protected void init() {
        int centerX = this.width / 2 - BUTTON_WIDTH / 2;
        int startY = this.height / 2 - 65;

        // Ползунок скорости 3D принтера
        double initialSpeed = BuildManager.INSTANCE.getSpeed();
        double sliderValue = Math.log10(initialSpeed / 0.5) / Math.log10(1000.0 / 0.5); // Логарифмическая шкала от 0.5 до 1000
        this.speedSlider = new SpeedSlider(centerX, startY - 40, BUTTON_WIDTH, BUTTON_HEIGHT, sliderValue);
        this.addDrawableChild(speedSlider);

        // 1. Построить с помощью ИИ
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("🏗  Построить что-то с помощью ИИ"),
                btn -> openRequestScreen("build", "Построить постройку")
        ).dimensions(centerX, startY, BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // 2. Сделать ИИ катсцену
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("🎬  Создать катсцену через ИИ"),
                btn -> openRequestScreen("cutscene", "Создать катсцену")
        ).dimensions(centerX, startY + (BUTTON_HEIGHT + SPACING), BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // 3. Добавить новый предмет через ИИ
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("🗡  Добавить новый предмет/блок/моба через ИИ"),
                btn -> openRequestScreen("item", "Создать новый предмет")
        ).dimensions(centerX, startY + 2*(BUTTON_HEIGHT + SPACING), BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // 4. Попросить ИИ что-то придумать
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("💡  Попросить ИИ что-то придумать"),
                btn -> openRequestScreen("idea", "Придумать идею")
        ).dimensions(centerX, startY + 3*(BUTTON_HEIGHT + SPACING), BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // 5. Попросить ИИ озвучить
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("🔊  Попросить ИИ озвучить текст"),
                btn -> openRequestScreen("voice", "Озвучить текст")
        ).dimensions(centerX, startY + 4*(BUTTON_HEIGHT + SPACING), BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // 6. Построить любой сложный механизм
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("⚙  Построить абсолютно любой механизм (даже очень сложный)"),
                btn -> openRequestScreen("mechanism", "Построить механизм")
        ).dimensions(centerX, startY + 5*(BUTTON_HEIGHT + SPACING), BUTTON_WIDTH, BUTTON_HEIGHT).build());

        // Кнопка отмены текущей постройки
        ButtonWidget cancelBtn = ButtonWidget.builder(
                Text.literal("⏹  Отменить текущую постройку"),
                btn -> {
                    BuildManager.INSTANCE.cancelBuild();
                }
        ).dimensions(centerX, startY + 7*(BUTTON_HEIGHT + SPACING), BUTTON_WIDTH/2 - 2, BUTTON_HEIGHT).build();
        cancelBtn.active = BuildManager.INSTANCE.isBuilding();
        this.addDrawableChild(cancelBtn);

        // Кнопка настроек API ключа
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("⚙  Настройки API ключа"),
                btn -> this.client.setScreen(new SettingsScreen(this))
        ).dimensions(centerX + BUTTON_WIDTH/2 + 2, startY + 7*(BUTTON_HEIGHT + SPACING), BUTTON_WIDTH/2 - 2, BUTTON_HEIGHT).build());
    }

    private void openRequestScreen(String taskType, String title) {
        this.client.setScreen(new AIRequestScreen(this, taskType, title));
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§l§6Lugg AI — ИИ-помощник с 3D принтером"), this.width / 2, 30, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§a✅ БЕСПЛАТНЫЙ РЕЖИМ РАБОТАЕТ СРАЗУ — КЛЮЧ НЕ НУЖЕН!"), this.width / 2, 45, 0x55FF55);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§c⚠ ОБЯЗАТЕЛЬНО ВКЛЮЧИ ЧИТЫ В МИРЕ! (Открыть для сети → Разрешить читы)"), this.width / 2, 58, 0xFF5555);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Меню можно ЗАКРЫТЬ во время стройки — летай вокруг и наблюдай за принтером!"), this.width / 2, 71, 0xAAAAAA);

        // Статус текущей постройки
        if (BuildManager.INSTANCE.isBuilding()) {
            int y = this.height - 50;
            String status = String.format("§6[3D Принтер] Печатается: '%s' | Блоков: %d/%d (%.1f%%) | Скорость: %.1f блок/сек | Осталось: %s",
                    BuildManager.INSTANCE.getBuildName(),
                    BuildManager.INSTANCE.getPlacedBlocks(),
                    BuildManager.INSTANCE.getPlacedBlocks() + BuildManager.INSTANCE.getRemainingBlocks(),
                    BuildManager.INSTANCE.getProgressPercent(),
                    BuildManager.INSTANCE.getSpeed(),
                    BuildManager.INSTANCE.getEstimatedTimeRemaining());
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(status), this.width / 2, y, 0xFFFFFF);

            // Прогресс бар
            int barW = 300;
            int barX = this.width/2 - barW/2;
            context.fill(barX, y+12, barX+barW, y+20, 0xFF555555);
            int fillW = (int)(barW * BuildManager.INSTANCE.getProgressPercent() / 100.0);
            context.fill(barX, y+12, barX+fillW, y+20, 0xFF55FF55);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        super.close();
    }

    private static class SpeedSlider extends SliderWidget {
        public SpeedSlider(int x, int y, int width, int height, double value) {
            super(x, y, width, height, Text.empty(), value);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            // Переводим логарифмическое значение обратно в скорость
            double speed = 0.5 * Math.pow(1000.0 / 0.5, this.value);
            this.setMessage(Text.literal(String.format("Скорость 3D принтера: %.1f блоков/сек", speed)));
        }

        @Override
        protected void applyValue() {
            double speed = 0.5 * Math.pow(1000.0 / 0.5, this.value);
            BuildManager.INSTANCE.setSpeed(speed);
        }
    }
}

