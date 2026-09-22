package com.lugg.mod.client.input;

import com.lugg.mod.client.screen.AIMenuScreen;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public class KeyBindings {
    public static KeyBinding openAiMenu;

    public static void register() {
        openAiMenu = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lugg.open_ai_menu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_J,
                "category.lugg.general"
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openAiMenu.wasPressed()) {
                com.lugg.mod.client.AiRequestState state = com.lugg.mod.client.AiRequestState.INSTANCE;
                if (state.requestInProgress) {
                    // Если запрос в процессе — открываем экран загрузки с логами
                    client.setScreen(new com.lugg.mod.client.screen.AiLoadingScreen(new AIMenuScreen()));
                } else if (state.finishedSuccessfully && state.readyPlan != null) {
                    // Если готов ответ — сразу показываем экран подтверждения
                    client.setScreen(new com.lugg.mod.client.screen.ConfirmPlanScreen(new AIMenuScreen(), state.readyPlan));
                } else {
                    // Иначе открываем главное меню
                    client.setScreen(new AIMenuScreen());
                }
            }
        });
    }
}
