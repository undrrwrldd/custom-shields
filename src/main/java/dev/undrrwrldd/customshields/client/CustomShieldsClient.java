package dev.undrrwrldd.customshields.client;

import dev.undrrwrldd.customshields.client.gui.CustomShieldScreen;
import dev.undrrwrldd.customshields.client.preset.PresetManager;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

public class CustomShieldsClient implements ClientModInitializer {

    public static final String MOD_ID = "customshields";
    public static final String AUTHOR = "@undrrwrldd";

    public static KeyBinding openGuiKey;

    @Override
    public void onInitializeClient() {
        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.customshields.open",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_K,
                KeyBinding.Category.create(Identifier.of(MOD_ID, "main"))
        ));

        ClientLifecycleEvents.CLIENT_STARTED.register(client -> {
            RuntimePack.get().ensureScaffold();
            PresetManager.get().load();
            ShieldTextureManager.get().init(client);
            PresetManager.get().applyActive();
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) {
                    client.setScreen(new CustomShieldScreen());
                }
            }
            ShieldTextureManager.get().tick();
        });
    }

    public static MinecraftClient mc() {
        return MinecraftClient.getInstance();
    }
}
