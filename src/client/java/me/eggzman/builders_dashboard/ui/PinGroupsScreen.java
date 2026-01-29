package me.eggzman.builders_dashboard.ui;

import me.eggzman.builders_dashboard.images.ImageLibrary;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class PinGroupsScreen extends DashboardBaseScreen {
    private TextFieldWidget nameField;
    private String statusText = "";
    private final List<ButtonWidget> listButtons = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<String> groupNames = new ArrayList<>();
    private int listStartY;
    private int listEndY;
    private int lastGroupCount = -1;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private final int rowH = 22;

    public PinGroupsScreen() {
        super(Text.literal("Pin Groups"));
    }

    @Override
    protected void init() {
        super.init();
        clearTabs();
        addTab("Home", DashboardScreen::new, false);
        addTab("Photos", PhotoSettingsScreen::new, false);
        addTab("Block Palettes", BlockPalettesScreen::new, false);
        addTab("Groups", PinGroupsScreen::new, true);
        addTab("Keybinds", KeybindsScreen::new, false);

        int panelW = Math.min(360, this.width - 40);
        int panelH = Math.min(240, this.height - 40);
        int x1 = (this.width - panelW) / 2;
        int y1 = (this.height - panelH) / 2;

        int fieldH = 20;
        int fieldX = x1 + 20;
        int fieldY = y1 + 50;
        int saveW = 80;
        int gap = 6;
        int fieldW = Math.max(80, panelW - 40 - saveW - gap);
        int saveX = fieldX + fieldW + gap;

        nameField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldW, fieldH, Text.literal("Group name"));
        nameField.setMaxLength(32);
        nameField.setPlaceholder(Text.literal("e.g. Castle"));
        addDrawableChild(nameField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Save"), btn -> {
            String raw = nameField.getText().trim();
            if (raw.isEmpty()) {
                statusText = "Enter a group name";
                return;
            }
            boolean saved = ImageLibrary.saveGroup(raw);
            statusText = saved ? "Group saved" : "Group not saved";
            if (saved) {
                nameField.setText("");
            }
            rebuildList();
        }).dimensions(saveX, fieldY, saveW, fieldH).build());

        listStartY = fieldY + 30;
        listEndY = y1 + panelH - 16;
        rebuildList();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int currentCount = ImageLibrary.getGroupNames().size();
        if (currentCount != lastGroupCount) {
            rebuildList();
        }

        int panelW = Math.min(360, this.width - 40);
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
                Text.literal("Pin Groups"),
                this.width / 2,
                y1 + 12,
                0xFFFFFFFF
        );

        if (!statusText.isEmpty()) {
            int statusY = nameField != null ? nameField.getY() + 26 : y1 + 80;
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(statusText),
                    this.width / 2,
                    statusY,
                    0xFFB0B0B0
            );
        }

        int listX1 = x1 + 16;
        int listX2 = x2 - 16;
        context.enableScissor(listX1, listStartY, listX2, listEndY);

        if (!rows.isEmpty()) {
            for (Row row : rows) {
                context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal(row.label),
                        row.labelX,
                        row.y + 6,
                        row.color
                );
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
        if (nameField != null
                && mouseX >= nameField.getX()
                && mouseX <= nameField.getX() + nameField.getWidth()
                && mouseY >= listStartY
                && mouseY <= listEndY) {
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

    private void rebuildList() {
        for (ButtonWidget btn : listButtons) {
            remove(btn);
        }
        listButtons.clear();
        rows.clear();
        groupNames.clear();

        List<String> groups = ImageLibrary.getGroupNames();
        lastGroupCount = groups.size();
        groupNames.addAll(groups);

        int listHeight = Math.max(0, listEndY - listStartY);
        maxScroll = Math.max(0, groupNames.size() * rowH - listHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        int y = listStartY - scrollOffset;

        int labelX = nameField != null ? nameField.getX() : 20;
        int loadW = 60;
        int deleteW = 60;
        int gap = 6;
        int rightX = labelX + (nameField != null ? nameField.getWidth() : 200) - (loadW + deleteW + gap);

        for (String name : groupNames) {
            if (y + rowH < listStartY) {
                y += rowH;
                continue;
            }
            if (y + rowH > listEndY) {
                break;
            }

            ButtonWidget loadBtn = ButtonWidget.builder(
                    Text.literal("Load"),
                    btn -> {
                        boolean applied = ImageLibrary.applyGroup(name);
                        statusText = applied ? "Group loaded" : "Group not found";
                    }
            ).dimensions(rightX, y + 1, loadW, 20).build();

            ButtonWidget editBtn = ButtonWidget.builder(
                    Text.literal("Edit"),
                    btn -> {
                        if (this.client != null) {
                            this.client.setScreen(new GroupEditScreen(this, name));
                        }
                    }
            ).dimensions(rightX + loadW + gap, y + 1, deleteW, 20).build();

            addDrawableChild(loadBtn);
            addDrawableChild(editBtn);
            listButtons.add(loadBtn);
            listButtons.add(editBtn);
            int color = ImageLibrary.getGroupFrameColor(name);
            rows.add(new Row(name, labelX, y, color));

            y += rowH;
        }
    }

    private static final class Row {
        private final String label;
        private final int labelX;
        private final int y;
        private final int color;

        private Row(String label, int labelX, int y, int color) {
            this.label = label;
            this.labelX = labelX;
            this.y = y;
            this.color = color;
        }
    }
}
