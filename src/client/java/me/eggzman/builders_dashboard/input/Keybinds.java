package me.eggzman.builders_dashboard.input;

import me.eggzman.builders_dashboard.ui.DashboardHud;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.util.Identifier;
import net.minecraft.client.util.InputUtil;
import org.lwjgl.glfw.GLFW;

public final class Keybinds {

    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("builders_dashboard", "main"));

    private static KeyBinding toggleHud;

    private Keybinds() {}

    public static void init() {
        toggleHud = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.builders_dashboard.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                CATEGORY
        ));

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (toggleHud.wasPressed()) {
                DashboardHud.toggle();
            }
        });
    }
}
