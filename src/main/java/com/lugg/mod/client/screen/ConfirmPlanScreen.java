package com.lugg.mod.client.screen;

import com.lugg.mod.ai.actions.ActionParser;
import com.lugg.mod.ai.actions.ParsedPlan;
import com.lugg.mod.client.AiRequestState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.List;

public class ConfirmPlanScreen extends Screen {
    private final Screen parent;
    private final ParsedPlan plan;

    public ConfirmPlanScreen(Screen parent, ParsedPlan plan) {
        super(Text.literal("Подтверждение плана ИИ"));
        this.parent = parent;
        this.plan = plan;
    }

    @Override
    protected void init() {
        int centerX = this.width / 2;
        int btnW = 150;

        boolean hasActions = !plan.blocks.isEmpty() || !plan.instantActions.isEmpty();

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal(hasActions ? "✅ Выполнить" : "❌ План пустой, назад"),
                btn -> {
                    if (hasActions) {
                        ActionParser.executePlan(plan);
                        AiRequestState.INSTANCE.reset();
                        this.client.setScreen(null);
                    } else {
                        AiRequestState.INSTANCE.reset();
                        this.client.setScreen(parent);
                    }
                }
        ).dimensions(centerX - btnW - 5, this.height - 40, btnW, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("❌ Отказать (отклонить план)"),
                btn -> {
                    AiRequestState.INSTANCE.reset();
                    this.client.setScreen(parent);
                }
        ).dimensions(centerX + 5, this.height - 40, btnW, 20).build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§l§6ИИ составил план действий"), this.width/2, 25, 0xFFFFFF);

        int y = 50;
        if (!plan.narration.isEmpty()) {
            List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal("§e" + plan.narration), this.width-80);
            for (OrderedText line : lines) {
                context.drawTextWithShadow(this.textRenderer, line, 40, y, 0xE0E0E0);
                y += 12;
            }
            y += 8;
        } else {
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7(без описания)"), 40, y, 0x999999);
            y += 20;
        }

        context.drawTextWithShadow(this.textRenderer, Text.literal("§lБудет выполнено:"), 40, y, 0x55FF55);
        y += 15;

        boolean any = false;
        if (plan.getTotalBlocks() > 0) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(String.format("§f  🖨 3Д принтер напечатает §e%d§f блоков '%s'", plan.getTotalBlocks(), plan.buildName)), 40, y, 0xFFFFFF);
            y += 12; any = true;
        }
        if (plan.getTotalItems() > 0) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(String.format("§f  🗡 Выдастся §e%d§f предмет(ов)", plan.getTotalItems())), 40, y, 0xFFFFFF);
            y += 12; any = true;
        }
        if (plan.getTotalSummons() > 0) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(String.format("§f  🐑 Заспавнится §e%d§f существ", plan.getTotalSummons())), 40, y, 0xFFFFFF);
            y += 12; any = true;
        }
        if (plan.getTotalMessages() > 0) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(String.format("§f  🔊 Озвучится §e%d§f реплик", plan.getTotalMessages())), 40, y, 0xFFFFFF);
            y += 12; any = true;
        }
        int extra = plan.instantActions.size() - plan.getTotalItems() - plan.getTotalSummons() - plan.getTotalMessages();
        if (extra > 0) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(String.format("§f  ⚡ Эффектов/звуков/титров: §e%d", extra)), 40, y, 0xFFFFFF);
            y += 12; any = true;
        }
        if (!any) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("§c  ⚠ План получился ПУСТОЙ! ИИ не придумал что сделать."), 40, y, 0xFF5555);
            y += 12;
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7Нажми Отмена и попробуй сформулировать запрос конкретнее:"), 40, y, 0xAAAAAA);
            y += 12;
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7Например: \"построй дом 5х5 из дуба с дверью и окном\""), 40, y, 0xAAAAAA);
        }

        if (any) {
            context.drawTextWithShadow(this.textRenderer, Text.literal("§7Нажми Выполнить — меню закроется и начнётся печать. Лети смотреть со всех сторон!"), 40, y + 10, 0xAAAAAA);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
