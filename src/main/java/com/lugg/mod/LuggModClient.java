package com.lugg.mod;

import com.lugg.mod.ai.building.BuildManager;
import com.lugg.mod.client.BuildHudOverlay;
import com.lugg.mod.client.input.KeyBindings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public class LuggModClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        // Регистрируем клавишу J
        KeyBindings.register();

        // Регистрируем тикер для 3D принтера — строит в фоне даже при закрытом меню
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client.world != null) {
                BuildManager.INSTANCE.tick();
            }
        });

        // Регистрируем HUD который показывает прогресс печати даже когда меню закрыто
        BuildHudOverlay.register();

        LuggMod.LOGGER.info("[Lugg AI Client] Клиент ИИ-помощника загружен");
    }
}
