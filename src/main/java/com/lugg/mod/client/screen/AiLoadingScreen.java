package com.lugg.mod.client.screen;

import com.lugg.mod.ai.FreeAiClient;
import com.lugg.mod.ai.OpenRouterClient;
import com.lugg.mod.ai.actions.ActionParser;
import com.lugg.mod.ai.actions.ParsedPlan;
import com.lugg.mod.client.AiRequestState;
import com.lugg.mod.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

import java.util.List;

/**
 * Экран ожидания ответа ИИ: показывает полный лог в виде консоли, кнопка "свернуть в фон".
 */
public class AiLoadingScreen extends Screen {
    private final Screen parent;
    private static AiLoadingScreen INSTANCE = null;
    private float scroll = 0;

    public AiLoadingScreen(Screen parent) {
        super(Text.literal("Генерация ответа ИИ..."));
        this.parent = parent;
        INSTANCE = this;
    }

    public static void refreshIfOpen() {
        if (INSTANCE != null && INSTANCE.client != null && INSTANCE.client.currentScreen == INSTANCE) {
            // Просто триггерим перерисовку
        }
    }

    @Override
    protected void init() {
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("↓ Свернуть в фон (продолжить играть)"),
                btn -> {
                    this.client.setScreen(null);
                }
        ).dimensions(this.width/2 - 150, this.height - 35, 300, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Отменить запрос"),
                btn -> {
                    AiRequestState.INSTANCE.reset();
                    this.client.setScreen(parent);
                }
        ).dimensions(10, this.height - 35, 120, 20).build());
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double amount) {
        scroll = Math.max(0, scroll - (float) amount * 30);
        return true;
    }

    @Override
    public void tick() {
        AiRequestState state = AiRequestState.INSTANCE;
        // Если запрос завершился — сразу открываем экран подтверждения
        if (state.finishedSuccessfully && state.readyPlan != null) {
            INSTANCE = null;
            this.client.setScreen(new ConfirmPlanScreen(parent, state.readyPlan));
        } else if (state.failed) {
            INSTANCE = null;
            this.client.setScreen(new AIRequestScreen(parent, state.taskType, "Запрос не удался"));
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§l§6⏳ ИИ генерирует ответ..."), this.width/2, 15, 0xFFFFFF);

        long elapsed = (System.currentTimeMillis() - AiRequestState.INSTANCE.requestStartTime) / 1000;
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Прошло: " + elapsed + " сек. Ты можешь нажать Свернуть и играть, когда ответ будет готов появится уведомление."), this.width/2, 30, 0xAAAAAA);

        // Область консоли
        int consoleX = 20;
        int consoleY = 50;
        int consoleW = this.width - 40;
        int consoleH = this.height - 95;
        context.fill(consoleX, consoleY, consoleX + consoleW, consoleY + consoleH, 0xFF000000);
        context.drawBorder(consoleX, consoleY, consoleW, consoleH, 0xFF00FF00);

        // Рендерим лог построчно с прокруткой
        int lineH = 11;
        List<String> logs = AiRequestState.INSTANCE.logs;
        int y = consoleY + 5 - (int)scroll;
        for (int i = 0; i < logs.size(); i++) {
            String line = logs.get(i);
            if (y > consoleY + consoleH - 5) break;
            if (y >= consoleY + 5) {
                context.drawTextWithShadow(this.textRenderer, Text.literal(line), consoleX + 5, y, 0x00FF00);
            }
            y += lineH;
        }

        // Скроллим автоматически вниз если пользователь не прокрутил вверх
        if (scroll == 0 || scroll > (logs.size() * lineH) - consoleH + 20) {
            scroll = Math.max(0, logs.size() * lineH - consoleH + 20);
        }

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        // Закрытие через Esc = свернуть в фон, не отменять
        this.client.setScreen(null);
    }
}
