package com.lugg.mod;

import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LuggMod implements ModInitializer {
    public static final String MOD_ID = "lugg";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static final String AI_ENDPOINT = "http://localhost:8000/ai/request"; // Адрес твоего локального ИИ-сервера

    @Override
    public void onInitialize() {
        com.lugg.mod.config.ModConfig.load();
        LOGGER.info("[Lugg AI Assistant] Мод ИИ-помощника загружен! Нажми J чтобы открыть меню.");
    }
}
