package com.lugg.mod.client.screen;

import com.lugg.mod.config.ModConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

public class SettingsScreen extends Screen {
    private final Screen parent;
    private TextFieldWidget apiKeyField;
    private String status = "§7Введи API ключ от OpenRouter и нажми Сохранить";

    protected SettingsScreen(Screen parent) {
        super(Text.literal("Настройки Lugg AI — Ключ OpenRouter"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        ModConfig config = ModConfig.getInstance();

        this.apiKeyField = new TextFieldWidget(
                this.textRenderer,
                this.width / 2 - 220,
                this.height / 2 - 30,
                440,
                20,
                Text.literal("OpenRouter API Key")
        );
        this.apiKeyField.setMaxLength(500);
        this.apiKeyField.setText(config.openrouterApiKey);
        this.addSelectableChild(apiKeyField);
        this.setFocused(apiKeyField);

        // Кнопка сохранить
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("💾 Сохранить ключ"),
                btn -> saveKey()
        ).dimensions(this.width / 2 - 220, this.height / 2, 215, 20).build());

        // Кнопка назад
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("← Назад в меню"),
                btn -> this.client.setScreen(parent)
        ).dimensions(this.width / 2 + 5, this.height / 2, 215, 20).build());
    }

    private void saveKey() {
        ModConfig config = ModConfig.getInstance();
        config.openrouterApiKey = apiKeyField.getText().trim();
        ModConfig.save();
        if (config.hasApiKey()) {
            status = "§a✅ Ключ сохранен! Можно возвращаться и отправлять запросы ИИ.";
        } else {
            status = "§cКлюч пустой!";
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§l§6Настройки Lugg AI — OpenRouter"), this.width / 2, 40, 0xFFFFFF);

        String[] instructions = {
                "§nНастройка OpenRouter (ОПЦИОНАЛЬНО! Мод работает и без ключа на бесплатном ИИ):",
                "Важно: сейчас для использования бесплатных моделей OpenRouter требуется привязать карту",
                "в личном кабинете (списаний не будет, просто политика антиспама).",
                "",
                "§nКак получить ключ (если хочешь 35+ разных моделей):",
                "1. Открой сайт §bhttps://openrouter.ai/keys§r и зарегистрируйся",
                "2. Привяжи банковскую карту в профиле (для подтверждения что ты не бот)",
                "3. Нажми кнопку \"Create Key\" и скопируй ключ (sk-or-v1-...)",
                "4. Вставь его в поле ниже и нажми Сохранить",
                "",
                "§aЕСЛИ НЕ ХОЧЕШЬ РЕГИСТРИРОВАТЬСЯ — ПРОСТО ЗАКРОЙ ЭТО МЕНЮ! БЕСПЛАТНЫЙ ИИ РАБОТАЕТ СРАЗУ.",
        };

        int y = 70;
        for (String line : instructions) {
            context.drawTextWithShadow(this.textRenderer, Text.literal(line), this.width / 2 - 220, y, 0xE0E0E0);
            y += 12;
        }

        context.drawTextWithShadow(this.textRenderer, Text.literal("API ключ:"), this.width / 2 - 220, this.height / 2 - 42, 0xCCCCCC);
        context.drawTextWrapped(this.textRenderer, Text.literal(status), this.width / 2 - 220, this.height / 2 + 30, 440, 0xFFFFFF);

        this.apiKeyField.render(context, mouseX, mouseY, delta);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        this.client.setScreen(parent);
    }
}
