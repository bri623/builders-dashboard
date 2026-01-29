package me.eggzman.builders_dashboard.ui;

import me.eggzman.builders_dashboard.images.ImageLibrary;
import me.eggzman.builders_dashboard.input.Keybinds;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class KeybindsScreen extends DashboardBaseScreen {
    private String statusText = "";
    private final List<ButtonWidget> listButtons = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int listStartY;
    private int listEndY;
    private int lastGroupCount = -1;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private final int rowH = 22;
    private static final int STATIC_ROW_COUNT = 11;

    private CaptureTarget captureTarget;

    public KeybindsScreen() {
        super(Text.literal("Keybinds"));
    }

    @Override
    protected void init() {
        super.init();
        clearTabs();
        addTab("Home", DashboardScreen::new, false);
        addTab("Photos", PhotoSettingsScreen::new, false);
        addTab("Block Palettes", BlockPalettesScreen::new, false);
        addTab("Groups", PinGroupsScreen::new, false);
        addTab("Keybinds", KeybindsScreen::new, true);

        int panelW = Math.min(380, this.width - 40);
        int panelH = Math.min(240, this.height - 40);
        int x1 = (this.width - panelW) / 2;
        int y1 = (this.height - panelH) / 2;

        listStartY = y1 + 40;
        listEndY = y1 + panelH - 12;
        rebuildList();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int currentCount = ImageLibrary.getGroupNames().size();
        if (currentCount != lastGroupCount) {
            rebuildList();
        }

        int panelW = Math.min(380, this.width - 40);
        int panelH = Math.min(240, this.height - 40);
        int x1 = (this.width - panelW) / 2;
        int y1 = (this.height - panelH) / 2;
        int x2 = x1 + panelW;
        int y2 = y1 + panelH;

        setTabAnchor(x1, y1, panelH);

        context.fill(x1, y1, x2, y2, 0xFF1B1B1B);
        context.fill(x1, y1, x2, y1 + 1, 0xFF4A4A4A);
        context.fill(x1, y2 - 1, x2, y2, 0xFF4A4A4A);
        context.fill(x1, y1, x1 + 1, y2, 0xFF4A4A4A);
        context.fill(x2 - 1, y1, x2, y2, 0xFF4A4A4A);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Keybinds"),
                this.width / 2,
                y1 + 12,
                0xFFFFFFFF
        );

        if (!statusText.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(statusText),
                    this.width / 2,
                    y2 - 18,
                    0xFFB0B0B0
            );
        }

        int listX1 = x1 + 16;
        int listX2 = x2 - 16;
        context.enableScissor(listX1, listStartY, listX2, listEndY);

        if (!rows.isEmpty()) {
            for (Row row : rows) {
                if (row.isHeader) {
                    context.drawCenteredTextWithShadow(
                            this.textRenderer,
                            Text.literal(row.label),
                            this.width / 2,
                            row.y + 6,
                            row.labelColor
                    );
                } else {
                    context.drawTextWithShadow(
                            this.textRenderer,
                            Text.literal(row.label),
                            row.labelX,
                            row.y + 6,
                            row.labelColor
                    );
                    context.drawTextWithShadow(
                            this.textRenderer,
                            Text.literal(row.binding),
                            row.bindingX,
                            row.y + 6,
                            0xFFB0B0B0
                    );
                }
            }
        }

        context.disableScissor();

        super.render(context, mouseX, mouseY, delta);
        renderTabs(context, mouseX, mouseY);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xAA000000);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelW = Math.min(380, this.width - 40);
        int x1 = (this.width - panelW) / 2;
        int x2 = x1 + panelW;
        if (mouseX >= x1 && mouseX <= x2 && mouseY >= listStartY && mouseY <= listEndY) {
            scrollOffset -= (int) Math.round(verticalAmount * rowH);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
            rebuildList();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (captureTarget != null) {
            int keyCode = input.getKeycode();
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                statusText = "Capture cancelled";
                captureTarget = null;
                rebuildList();
                return true;
            }
            if (isModifierKey(keyCode)) {
                return true;
            }

            Keybinds.KeyCombo combo = new Keybinds.KeyCombo(
                    keyCode,
                    (input.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0,
                    (input.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0,
                    (input.modifiers() & GLFW.GLFW_MOD_ALT) != 0
            );
            applyCapturedCombo(combo);
            return true;
        }

        return super.keyPressed(input);
    }

    private void rebuildList() {
        for (ButtonWidget btn : listButtons) {
            remove(btn);
        }
        listButtons.clear();
        rows.clear();

        List<String> groups = ImageLibrary.getGroupNames();
        lastGroupCount = groups.size();

        int listHeight = Math.max(0, listEndY - listStartY);
        int totalRows = STATIC_ROW_COUNT + 2 + groups.size();
        maxScroll = Math.max(0, totalRows * rowH - listHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        int y = listStartY - scrollOffset;

        int panelW = Math.min(380, this.width - 40);
        int x1 = (this.width - panelW) / 2;
        int labelX = x1 + 16;
        int bindingX = x1 + 160;
        int buttonW = 50;
        int gap = 6;
        int setX = x1 + panelW - (buttonW * 2 + gap + 16);
        int clearX = setX + buttonW + gap;

        y = addHeaderRow(y, labelX, "Mouse");
        y = addStaticRow(y, labelX, bindingX, "View photo", "Left Click");
        y = addStaticRow(y, labelX, bindingX, "Edit note", "Right Click");
        y = addStaticRow(y, labelX, bindingX, "Resize polaroid", "Scroll");
        y = addStaticRow(y, labelX, bindingX, "Move polaroid", "Drag");

        y = addHeaderRow(y, labelX, "Actions");
        y = addActionRow(y, labelX, bindingX, setX, clearX, "Toggle HUD", Keybinds.getToggleHudCombo(), CaptureTarget.toggleHud());
        y = addActionRow(y, labelX, bindingX, setX, clearX, "Open Dashboard", Keybinds.getOpenConfigCombo(), CaptureTarget.openConfig());

        y = addHeaderRow(y, labelX, "UI");
        y = addToggleRow(y, labelX, bindingX, setX, "Tooltips", Keybinds.areTooltipsEnabled());

        y = addHeaderRow(y, labelX, "Groups");

        for (String group : groups) {
            int labelColor = ImageLibrary.getGroupFrameColor(group);
            String binding = Keybinds.formatCombo(Keybinds.getGroupCombo(group));
            boolean capturing = captureTarget != null && captureTarget.matchesGroup(group);
            if (capturing) {
                binding = "Press keys...";
            }
            if (y + rowH < listStartY) {
                y += rowH;
                continue;
            }
            if (y + rowH > listEndY) {
                break;
            }

            rows.add(new Row("Group: " + group, labelX, bindingX, y, labelColor, binding, false));

            ButtonWidget setBtn = ButtonWidget.builder(Text.literal("Set"), btn -> startCapture(CaptureTarget.group(group)))
                    .dimensions(setX, y + 1, buttonW, 20).build();
            ButtonWidget clearBtn = ButtonWidget.builder(Text.literal("Clear"), btn -> {
                Keybinds.setGroupCombo(group, Keybinds.KeyCombo.unbound());
                statusText = "Group key cleared";
                rebuildList();
            }).dimensions(clearX, y + 1, buttonW, 20).build();

            addDrawableChild(setBtn);
            addDrawableChild(clearBtn);
            listButtons.add(setBtn);
            listButtons.add(clearBtn);

            y += rowH;
        }
    }

    private int addActionRow(int y, int labelX, int bindingX, int setX, int clearX, String label, Keybinds.KeyCombo combo, CaptureTarget target) {
        String binding = Keybinds.formatCombo(combo);
        boolean capturing = captureTarget != null && captureTarget.matches(target);
        if (capturing) {
            binding = "Press keys...";
        }
        if (y + rowH >= listStartY && y + rowH <= listEndY) {
            rows.add(new Row(label, labelX, bindingX, y, 0xFFFFFFFF, binding, false));

            ButtonWidget setBtn = ButtonWidget.builder(Text.literal("Set"), btn -> startCapture(target))
                    .dimensions(setX, y + 1, 50, 20).build();
            ButtonWidget clearBtn = ButtonWidget.builder(Text.literal("Clear"), btn -> {
                if (target.type == CaptureType.TOGGLE_HUD) {
                    Keybinds.setToggleHudCombo(Keybinds.KeyCombo.unbound());
                } else if (target.type == CaptureType.OPEN_CONFIG) {
                    Keybinds.setOpenConfigCombo(Keybinds.KeyCombo.unbound());
                }
                statusText = "Key cleared";
                rebuildList();
            }).dimensions(clearX, y + 1, 50, 20).build();

            addDrawableChild(setBtn);
            addDrawableChild(clearBtn);
            listButtons.add(setBtn);
            listButtons.add(clearBtn);
        }
        return y + rowH;
    }

    private int addToggleRow(int y, int labelX, int bindingX, int setX, String label, boolean enabled) {
        String binding = enabled ? "On" : "Off";
        if (y + rowH >= listStartY && y + rowH <= listEndY) {
            rows.add(new Row(label, labelX, bindingX, y, 0xFFFFFFFF, binding, false));

            ButtonWidget toggleBtn = ButtonWidget.builder(Text.literal("Toggle"), btn -> {
                Keybinds.toggleTooltips();
                statusText = "Tooltips " + (Keybinds.areTooltipsEnabled() ? "enabled" : "disabled");
                rebuildList();
            }).dimensions(setX, y + 1, 60, 20).build();

            addDrawableChild(toggleBtn);
            listButtons.add(toggleBtn);
        }
        return y + rowH;
    }

    private int addStaticRow(int y, int labelX, int bindingX, String label, String binding) {
        if (y + rowH >= listStartY && y + rowH <= listEndY) {
            rows.add(new Row(label, labelX, bindingX, y, 0xFFFFFFFF, binding, false));
        }
        return y + rowH;
    }

    private int addHeaderRow(int y, int labelX, String label) {
        if (y + rowH >= listStartY && y + rowH <= listEndY) {
            rows.add(new Row(label, labelX, labelX, y, 0xFF8FA0B8, "", true));
        }
        return y + rowH;
    }

    private void startCapture(CaptureTarget target) {
        captureTarget = target;
        statusText = "Press a key combo (Esc to cancel)";
        rebuildList();
    }

    private void applyCapturedCombo(Keybinds.KeyCombo combo) {
        if (captureTarget == null) {
            return;
        }
        if (captureTarget.type == CaptureType.TOGGLE_HUD) {
            Keybinds.setToggleHudCombo(combo);
            statusText = "Toggle HUD updated";
        } else if (captureTarget.type == CaptureType.OPEN_CONFIG) {
            Keybinds.setOpenConfigCombo(combo);
            statusText = "Open Dashboard updated";
        } else if (captureTarget.type == CaptureType.GROUP && captureTarget.groupName != null) {
            Keybinds.setGroupCombo(captureTarget.groupName, combo);
            statusText = "Group key updated";
        }
        captureTarget = null;
        rebuildList();
    }

    private boolean isModifierKey(int keyCode) {
        return keyCode == GLFW.GLFW_KEY_LEFT_CONTROL
                || keyCode == GLFW.GLFW_KEY_RIGHT_CONTROL
                || keyCode == GLFW.GLFW_KEY_LEFT_SHIFT
                || keyCode == GLFW.GLFW_KEY_RIGHT_SHIFT
                || keyCode == GLFW.GLFW_KEY_LEFT_ALT
                || keyCode == GLFW.GLFW_KEY_RIGHT_ALT;
    }

    private enum CaptureType {
        TOGGLE_HUD,
        OPEN_CONFIG,
        GROUP
    }

    private static final class CaptureTarget {
        private final CaptureType type;
        private final String groupName;

        private CaptureTarget(CaptureType type, String groupName) {
            this.type = type;
            this.groupName = groupName;
        }

        private static CaptureTarget toggleHud() {
            return new CaptureTarget(CaptureType.TOGGLE_HUD, null);
        }

        private static CaptureTarget openConfig() {
            return new CaptureTarget(CaptureType.OPEN_CONFIG, null);
        }

        private static CaptureTarget group(String name) {
            return new CaptureTarget(CaptureType.GROUP, name);
        }

        private boolean matches(CaptureTarget other) {
            if (other == null) {
                return false;
            }
            return type == other.type && (groupName == null || groupName.equalsIgnoreCase(other.groupName));
        }

        private boolean matchesGroup(String name) {
            return type == CaptureType.GROUP && groupName != null && groupName.equalsIgnoreCase(name);
        }
    }

    private static final class Row {
        private final String label;
        private final int labelX;
        private final int bindingX;
        private final int y;
        private final int labelColor;
        private final String binding;
        private final boolean isHeader;

        private Row(String label, int labelX, int bindingX, int y, int labelColor, String binding, boolean isHeader) {
            this.label = label;
            this.labelX = labelX;
            this.bindingX = bindingX;
            this.y = y;
            this.labelColor = labelColor;
            this.binding = binding;
            this.isHeader = isHeader;
        }
    }
}
