package com.lugg.mod.client.screen;

import com.lugg.mod.ai.FreeAiClient;
import com.lugg.mod.client.AiRequestState;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class AIRequestScreen extends Screen {
    private final Screen parent;
    private final String taskType;
    private TextFieldWidget promptField;
    private String errorMessage = "";

    protected AIRequestScreen(Screen parent, String taskType, String title) {
        super(Text.literal(title));
        this.parent = parent;
        this.taskType = taskType;
    }

    @Override
    protected void init() {
        this.promptField = new TextFieldWidget(
                this.textRenderer, 20, 40, this.width - 40, 20,
                Text.literal("Введите запрос")
        );
        this.promptField.setMaxLength(1000);
        this.promptField.setText(AiRequestState.INSTANCE.prompt);
        this.addSelectableChild(promptField);
        this.setFocused(promptField);

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Отправить запрос ИИ"),
                btn -> sendRequest()
        ).dimensions(20, 65, (this.width - 50)/2, 20).build());

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("← Назад"),
                btn -> this.client.setScreen(parent)
        ).dimensions(30 + (this.width - 50)/2, 65, (this.width - 50)/2, 20).build());
    }

    private void sendRequest() {
        String prompt = promptField.getText().trim();
        if (prompt.isEmpty()) {
            errorMessage = "§cНапиши что именно нужно сделать!";
            return;
        }
        // Запускаем запрос и открываем экран загрузки с консолью
        FreeAiClient.sendRequest(taskType, prompt);
        this.client.setScreen(new AiLoadingScreen(parent));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == 257 || keyCode == 335)) {
            sendRequest();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§l§6" + this.title.getString()), this.width / 2, 15, 0xFFFFFF);
        context.drawTextWrapped(this.textRenderer, Text.literal("Напиши точный запрос для ИИ, например: \"построй передо мной маленький деревянный дом 5 на 5 блоков с дверью и окном\""), 20, 28, this.width-40, 0xAAFFAA);
        this.promptField.render(context, mouseX, mouseY, delta);
        if (!errorMessage.isEmpty()) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(errorMessage), 20, 90, 0xFF5555);
        }
        super.render(context, mouseX, mouseY, delta);
    }
}
