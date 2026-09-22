package com.lugg.mod.config;

import com.lugg.mod.LuggMod;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

public class ModConfig {
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("lugg_ai.json");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public String openrouterApiKey = "";
    public int aiTimeoutSeconds = 120;

    private static ModConfig INSTANCE;

    public static ModConfig getInstance() {
        if (INSTANCE == null) {
            load();
        }
        return INSTANCE;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                INSTANCE = GSON.fromJson(reader, ModConfig.class);
                if (INSTANCE == null) INSTANCE = new ModConfig();
                LuggMod.LOGGER.info("[Config] Конфиг Lugg AI загружен");
                return;
            } catch (Exception e) {
                LuggMod.LOGGER.error("[Config] Не удалось загрузить конфиг, создаем новый", e);
            }
        }
        INSTANCE = new ModConfig();
        save();
    }

    public static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(INSTANCE, writer);
            }
        } catch (Exception e) {
            LuggMod.LOGGER.error("[Config] Не удалось сохранить конфиг", e);
        }
    }

    public boolean hasApiKey() {
        return openrouterApiKey != null && !openrouterApiKey.trim().isEmpty();
    }
}
