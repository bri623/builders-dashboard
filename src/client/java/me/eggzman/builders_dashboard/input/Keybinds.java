package me.eggzman.builders_dashboard.input;

import me.eggzman.builders_dashboard.ui.DashboardHud;
import me.eggzman.builders_dashboard.ui.DashboardScreen;
import me.eggzman.builders_dashboard.images.ImageLibrary;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.ParentElement;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.ingame.AbstractCommandBlockScreen;
import net.minecraft.client.gui.screen.ingame.AbstractSignEditScreen;
import net.minecraft.client.gui.screen.ingame.BookEditScreen;
import net.minecraft.client.gui.screen.ingame.BookSigningScreen;
import net.minecraft.client.gui.screen.ingame.CommandBlockScreen;
import net.minecraft.client.gui.screen.ingame.HangingSignEditScreen;
import net.minecraft.client.gui.screen.ingame.JigsawBlockScreen;
import net.minecraft.client.gui.screen.ingame.LecternScreen;
import net.minecraft.client.gui.screen.ingame.MinecartCommandBlockScreen;
import net.minecraft.client.gui.screen.ingame.SignEditScreen;
import net.minecraft.client.gui.screen.ingame.StructureBlockScreen;
import net.minecraft.client.gui.screen.recipebook.RecipeBookWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.IdentityHashMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.lang.reflect.Field;

public final class Keybinds {

    private static final KeyBinding.Category CATEGORY =
            KeyBinding.Category.create(Identifier.of("builders_dashboard", "main"));

    private static KeyBinding toggleHud;
    private static KeyBinding openConfig;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("builders_dashboard");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("keybinds.json");
    private static final Map<String, KeyCombo> GROUP_BINDINGS = new HashMap<>();
    private static final Map<String, Boolean> DOWN_STATE = new HashMap<>();
    private static KeyCombo toggleHudCombo = KeyCombo.single(GLFW.GLFW_KEY_O);
    private static KeyCombo openConfigCombo = KeyCombo.single(GLFW.GLFW_KEY_P);
    private static boolean tooltipsEnabled = true;

    private Keybinds() {}

    public static void init() {
        toggleHud = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.builders_dashboard.toggle",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_O,
                CATEGORY
        ));

        openConfig = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.builders_dashboard.config",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_P,
                CATEGORY
        ));

        loadConfig();
        updateKeyBindings();

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            if (client == null || client.world == null || client.player == null) {
                clearDownStates();
                return;
            }
            if (isTypingInTextField(client)) {
                clearDownStates();
                return;
            }
            handleComboEdge(client, "action:toggleHud", toggleHudCombo, () -> DashboardHud.toggle());
            handleComboEdge(client, "action:openConfig", openConfigCombo, () -> client.setScreen(new DashboardScreen()));
            handleGroupBindings(client);
        });
    }

    public static KeyCombo getToggleHudCombo() {
        return toggleHudCombo;
    }

    public static KeyCombo getOpenConfigCombo() {
        return openConfigCombo;
    }

    public static boolean areTooltipsEnabled() {
        return tooltipsEnabled;
    }

    public static void toggleTooltips() {
        setTooltipsEnabled(!tooltipsEnabled);
    }

    public static void setTooltipsEnabled(boolean enabled) {
        tooltipsEnabled = enabled;
        saveConfig();
    }

    public static void setToggleHudCombo(KeyCombo combo) {
        toggleHudCombo = combo == null ? KeyCombo.unbound() : combo;
        saveConfig();
        updateKeyBindings();
    }

    public static void setOpenConfigCombo(KeyCombo combo) {
        openConfigCombo = combo == null ? KeyCombo.unbound() : combo;
        saveConfig();
        updateKeyBindings();
    }

    public static KeyCombo getGroupCombo(String groupName) {
        if (groupName == null) {
            return KeyCombo.unbound();
        }
        KeyCombo combo = GROUP_BINDINGS.get(groupName.toLowerCase(Locale.ROOT));
        return combo == null ? KeyCombo.unbound() : combo;
    }

    public static void setGroupCombo(String groupName, KeyCombo combo) {
        if (groupName == null) {
            return;
        }
        String key = groupName.toLowerCase(Locale.ROOT);
        if (combo == null || combo.isUnbound()) {
            GROUP_BINDINGS.remove(key);
        } else {
            GROUP_BINDINGS.put(key, combo);
        }
        saveConfig();
    }

    public static void renameGroupBinding(String oldName, String newName) {
        if (oldName == null || newName == null) {
            return;
        }
        String from = oldName.toLowerCase(Locale.ROOT);
        String to = newName.toLowerCase(Locale.ROOT);
        if (from.equals(to)) {
            return;
        }
        KeyCombo combo = GROUP_BINDINGS.remove(from);
        if (combo != null) {
            GROUP_BINDINGS.put(to, combo);
            saveConfig();
        }
    }

    public static void deleteGroupBinding(String name) {
        if (name == null) {
            return;
        }
        String key = name.toLowerCase(Locale.ROOT);
        if (GROUP_BINDINGS.remove(key) != null) {
            saveConfig();
        }
    }

    public static String formatCombo(KeyCombo combo) {
        if (combo == null || combo.isUnbound()) {
            return "Unbound";
        }
        StringBuilder out = new StringBuilder();
        if (combo.ctrl) {
            out.append("Ctrl+");
        }
        if (combo.shift) {
            out.append("Shift+");
        }
        if (combo.alt) {
            out.append("Alt+");
        }
        InputUtil.Key key = InputUtil.fromKeyCode(new KeyInput(combo.keyCode, 0, 0));
        out.append(key.getLocalizedText().getString());
        return out.toString();
    }

    private static void handleComboEdge(MinecraftClient client, String key, KeyCombo combo, Runnable action) {
        if (combo == null || combo.isUnbound()) {
            return;
        }
        boolean down = isComboDown(client, combo);
        boolean wasDown = DOWN_STATE.getOrDefault(key, false);
        if (down && !wasDown) {
            action.run();
        }
        DOWN_STATE.put(key, down);
    }

    private static void handleGroupBindings(MinecraftClient client) {
        if (client == null || client.getWindow() == null) {
            return;
        }
        String activeGroup = ImageLibrary.getActiveGroupName();
        String firstEdgeGroup = null;
        boolean activeEdge = false;
        for (String group : ImageLibrary.getGroupNames()) {
            KeyCombo combo = getGroupCombo(group);
            if (combo.isUnbound()) {
                continue;
            }
            boolean down = isComboDown(client, combo);
            String key = "group:" + group.toLowerCase(Locale.ROOT);
            boolean wasDown = DOWN_STATE.getOrDefault(key, false);
            if (down && !wasDown) {
                if (activeGroup != null && activeGroup.equalsIgnoreCase(group)) {
                    activeEdge = true;
                } else if (firstEdgeGroup == null) {
                    firstEdgeGroup = group;
                }
            }
            DOWN_STATE.put(key, down);
        }
        if (activeEdge && activeGroup != null) {
            ImageLibrary.applyGroup(activeGroup);
            return;
        }
        if (firstEdgeGroup != null) {
            ImageLibrary.applyGroup(firstEdgeGroup);
        }
    }

    private static boolean isComboDown(MinecraftClient client, KeyCombo combo) {
        if (combo == null || combo.isUnbound() || client == null || client.getWindow() == null) {
            return false;
        }
        boolean keyDown = InputUtil.isKeyPressed(client.getWindow(), combo.keyCode);
        boolean ctrl = isModifierDown(client, GLFW.GLFW_KEY_LEFT_CONTROL, GLFW.GLFW_KEY_RIGHT_CONTROL);
        boolean shift = isModifierDown(client, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_RIGHT_SHIFT);
        boolean alt = isModifierDown(client, GLFW.GLFW_KEY_LEFT_ALT, GLFW.GLFW_KEY_RIGHT_ALT);
        return keyDown && ctrl == combo.ctrl && shift == combo.shift && alt == combo.alt;
    }

    private static boolean isTypingInTextField(MinecraftClient client) {
        if (client == null) {
            return false;
        }
        Screen screen = client.currentScreen;
        if (screen == null) {
            return false;
        }
        if (isAlwaysTypingScreen(screen)) {
            return true;
        }
        Element focused = screen.getFocused();
        if (isFocusedTextInput(focused)) {
            return true;
        }
        if (hasFocusedTextField(screen)) {
            return true;
        }
        return false;
    }

    private static boolean isFocusedTextInput(Object obj) {
        if (obj == null) {
            return false;
        }
        if (obj instanceof TextFieldWidget textField) {
            return textField.isFocused();
        }
        return false;
    }

    private static boolean isAlwaysTypingScreen(Screen screen) {
        return screen instanceof AbstractSignEditScreen
                || screen instanceof SignEditScreen
                || screen instanceof HangingSignEditScreen
                || screen instanceof BookEditScreen
                || screen instanceof BookSigningScreen
                || screen instanceof AbstractCommandBlockScreen
                || screen instanceof CommandBlockScreen
                || screen instanceof MinecartCommandBlockScreen
                || screen instanceof StructureBlockScreen
                || screen instanceof JigsawBlockScreen
                || screen instanceof LecternScreen
                || screen instanceof ChatScreen;
    }

    private static boolean hasFocusedTextField(Screen screen) {
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        if (hasFocusedTextFieldInFields(screen)) {
            return true;
        }
        return containsFocusedTextField(screen.children(), seen);
    }

    private static boolean containsFocusedTextField(Iterable<? extends Element> elements, IdentityHashMap<Object, Boolean> seen) {
        for (Element element : elements) {
            if (element == null || seen.put(element, Boolean.TRUE) != null) {
                continue;
            }
            if (isFocusedTextInput(element)) {
                return true;
            }
            if (element instanceof RecipeBookWidget) {
                if (hasFocusedTextFieldInFields(element)) {
                    return true;
                }
            }
            if (element instanceof ParentElement parent) {
                if (containsFocusedTextField(parent.children(), seen)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean hasFocusedTextFieldInFields(Object owner) {
        for (Class<?> type = owner.getClass(); type != null && type != Object.class; type = type.getSuperclass()) {
            Field[] fields = type.getDeclaredFields();
            for (Field field : fields) {
                if (!TextFieldWidget.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                try {
                    field.setAccessible(true);
                    Object value = field.get(owner);
                    if (value instanceof TextFieldWidget textField && textField.isFocused()) {
                        return true;
                    }
                } catch (Throwable ignored) {
                    // best-effort
                }
            }
        }
        return false;
    }

    private static void clearDownStates() {
        if (!DOWN_STATE.isEmpty()) {
            DOWN_STATE.replaceAll((key, value) -> false);
        }
    }

    private static boolean isModifierDown(MinecraftClient client, int leftKey, int rightKey) {
        return InputUtil.isKeyPressed(client.getWindow(), leftKey)
                || InputUtil.isKeyPressed(client.getWindow(), rightKey);
    }

    private static void updateKeyBindings() {
        if (toggleHud != null) {
            toggleHud.setBoundKey(InputUtil.fromKeyCode(new KeyInput(toggleHudCombo.keyCode, 0, 0)));
        }
        if (openConfig != null) {
            openConfig.setBoundKey(InputUtil.fromKeyCode(new KeyInput(openConfigCombo.keyCode, 0, 0)));
        }
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null && client.getWindow() != null) {
            KeyBinding.updatePressedStates();
            KeyBinding.unpressAll();
        }
    }

    private static void loadConfig() {
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }
        try {
            String json = Files.readString(CONFIG_FILE);
            KeybindsConfig cfg = GSON.fromJson(json, KeybindsConfig.class);
            if (cfg == null) {
                return;
            }
            if (cfg.toggleHud != null) {
                toggleHudCombo = cfg.toggleHud;
            }
            if (cfg.openConfig != null) {
                openConfigCombo = cfg.openConfig;
            }
            if (cfg.tooltipsEnabled != null) {
                tooltipsEnabled = cfg.tooltipsEnabled;
            }
            if (cfg.groups != null) {
                GROUP_BINDINGS.clear();
                GROUP_BINDINGS.putAll(cfg.groups);
            }
        } catch (IOException | JsonSyntaxException ignored) {
            // best-effort
        }
    }

    private static void saveConfig() {
        KeybindsConfig cfg = new KeybindsConfig();
        cfg.toggleHud = toggleHudCombo;
        cfg.openConfig = openConfigCombo;
        cfg.groups = new HashMap<>(GROUP_BINDINGS);
        cfg.tooltipsEnabled = tooltipsEnabled;
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(cfg));
        } catch (IOException ignored) {
            // best-effort
        }
    }

    public static final class KeyCombo {
        public int keyCode;
        public boolean ctrl;
        public boolean shift;
        public boolean alt;

        public KeyCombo() {}

        public KeyCombo(int keyCode, boolean ctrl, boolean shift, boolean alt) {
            this.keyCode = keyCode;
            this.ctrl = ctrl;
            this.shift = shift;
            this.alt = alt;
        }

        public static KeyCombo single(int keyCode) {
            return new KeyCombo(keyCode, false, false, false);
        }

        public static KeyCombo unbound() {
            return new KeyCombo(GLFW.GLFW_KEY_UNKNOWN, false, false, false);
        }

        public boolean isUnbound() {
            return keyCode == GLFW.GLFW_KEY_UNKNOWN || keyCode == 0;
        }
    }

    private static final class KeybindsConfig {
        KeyCombo toggleHud;
        KeyCombo openConfig;
        Map<String, KeyCombo> groups;
        Boolean tooltipsEnabled;
    }
}
