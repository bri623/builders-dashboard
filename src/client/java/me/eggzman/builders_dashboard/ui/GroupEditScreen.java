package me.eggzman.builders_dashboard.ui;

import me.eggzman.builders_dashboard.images.ImageLibrary;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;

public class GroupEditScreen extends DashboardBaseScreen {
    private final Screen parent;
    private String groupName;
    private TextFieldWidget nameField;
    private ColorSlider redSlider;
    private ColorSlider greenSlider;
    private ColorSlider blueSlider;
    private String statusText = "";
    private final List<ButtonWidget> listButtons = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<ImageLibrary.ImageEntry> images = new ArrayList<>();
    private int previewX;
    private int previewY;
    private int listStartY;
    private int listEndY;
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private final int rowH = 22;

    public GroupEditScreen(Screen parent, String groupName) {
        super(Text.literal("Edit Group"));
        this.parent = parent;
        this.groupName = groupName;
    }

    @Override
    protected void init() {
        super.init();
        clearTabs();
        addTab("Home", DashboardScreen::new, false);
        addTab("Photos", PhotoSettingsScreen::new, false);
        addTab("Block Palettes", BlockPalettesScreen::new, false);
        addTab("Groups", PinGroupsScreen::new, true);

        int panelW = Math.min(420, this.width - 40);
        int panelH = Math.min(260, this.height - 40);
        int x1 = (this.width - panelW) / 2;
        int y1 = (this.height - panelH) / 2;

        int rowY = y1 + 40;
        int fieldH = 20;
        int gap = 6;
        int actionW = 70;
        int fieldW = Math.max(80, panelW - 40 - actionW * 2 - gap * 2);
        int fieldX = x1 + 20;
        int renameX = fieldX + fieldW + gap;
        int dupX = renameX + actionW + gap;

        nameField = new TextFieldWidget(this.textRenderer, fieldX, rowY, fieldW, fieldH, Text.literal("Group name"));
        nameField.setMaxLength(32);
        nameField.setPlaceholder(Text.literal("New name"));
        addDrawableChild(nameField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Rename"), btn -> renameGroup())
                .dimensions(renameX, rowY, actionW, fieldH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Duplicate"), btn -> duplicateGroup())
                .dimensions(dupX, rowY, actionW, fieldH).build());

        int colorRowY = rowY + 26;
        int deleteW = 70;
        addDrawableChild(ButtonWidget.builder(Text.literal("Delete"), btn -> deleteGroup())
                .dimensions(fieldX, colorRowY, deleteW, fieldH).build());

        int previewSize = 18;
        previewX = fieldX + deleteW + gap;
        previewY = colorRowY + 1;

        int sliderX = previewX + previewSize + gap;
        int sliderW = panelW - 40 - deleteW - gap - previewSize - gap;
        int sliderH = 16;
        int sliderGap = 6;

        redSlider = new ColorSlider(sliderX, colorRowY, sliderW, sliderH, "R");
        greenSlider = new ColorSlider(sliderX, colorRowY + sliderH + sliderGap, sliderW, sliderH, "G");
        blueSlider = new ColorSlider(sliderX, colorRowY + (sliderH + sliderGap) * 2, sliderW, sliderH, "B");
        addDrawableChild(redSlider);
        addDrawableChild(greenSlider);
        addDrawableChild(blueSlider);
        setSlidersFromColor(ImageLibrary.getGroupFrameColor(groupName));

        listStartY = colorRowY + (sliderH + sliderGap) * 3 + 12;
        listEndY = y1 + panelH - 12;
        rebuildList();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelW = Math.min(420, this.width - 40);
        int panelH = Math.min(260, this.height - 40);
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
                Text.literal("Edit Group: " + groupName),
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
                context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal(row.label),
                        row.labelX,
                        row.y + 6,
                        0xFFDDDDDD
                );
            }
        }

        context.disableScissor();

        if (previewX != 0 || previewY != 0) {
            drawColorPreview(context, previewX, previewY);
        }

        super.render(context, mouseX, mouseY, delta);
        renderTabs(context, mouseX, mouseY);
    }

    private void drawColorPreview(DrawContext context, int x, int y) {
        int size = 18;
        int color = ImageLibrary.getGroupFrameColor(groupName);
        context.fill(x, y, x + size, y + size, color);
        context.fill(x, y, x + size, y + 1, 0xFF333333);
        context.fill(x, y + size - 1, x + size, y + size, 0xFF333333);
        context.fill(x, y, x + 1, y + size, 0xFF333333);
        context.fill(x + size - 1, y, x + size, y + size, 0xFF333333);
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

    @Override
    public void close() {
        MinecraftClient client = this.client;
        if (client != null) {
            client.setScreen(parent);
        }
    }

    private void rebuildList() {
        for (ButtonWidget btn : listButtons) {
            remove(btn);
        }
        listButtons.clear();
        rows.clear();
        images.clear();

        images.addAll(ImageLibrary.getImages());
        int listHeight = Math.max(0, listEndY - listStartY);
        maxScroll = Math.max(0, images.size() * rowH - listHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        int y = listStartY - scrollOffset;

        int labelX = nameField != null ? nameField.getX() : 20;
        int toggleW = 70;
        int gap = 6;
        int rightX = labelX + (nameField != null ? nameField.getWidth() : 200) - toggleW;

        for (ImageLibrary.ImageEntry entry : images) {
            if (y + rowH < listStartY) {
                y += rowH;
                continue;
            }
            if (y + rowH > listEndY) {
                break;
            }

            String name = entry.filePath().getFileName().toString();
            boolean inGroup = ImageLibrary.groupContainsImage(groupName, entry);

            ButtonWidget toggle = ButtonWidget.builder(
                    Text.literal(inGroup ? "Remove" : "Add"),
                    btn -> {
                        ImageLibrary.setGroupImage(groupName, entry, !inGroup);
                        rebuildList();
                    }
            ).dimensions(rightX, y + 1, toggleW, 20).build();

            addDrawableChild(toggle);
            listButtons.add(toggle);
            rows.add(new Row(name, labelX, y));

            y += rowH;
        }
    }

    private void renameGroup() {
        String raw = nameField != null ? nameField.getText().trim() : "";
        if (raw.isEmpty()) {
            statusText = "Enter a new name";
            return;
        }
        boolean renamed = ImageLibrary.renameGroup(groupName, raw);
        if (!renamed) {
            statusText = "Rename failed";
            return;
        }
        groupName = raw;
        statusText = "Group renamed";
        if (nameField != null) {
            nameField.setText("");
        }
    }

    private void duplicateGroup() {
        String raw = nameField != null ? nameField.getText().trim() : "";
        if (raw.isEmpty()) {
            statusText = "Enter a new name";
            return;
        }
        boolean duplicated = ImageLibrary.duplicateGroup(groupName, raw);
        statusText = duplicated ? "Group duplicated" : "Duplicate failed";
    }

    private void deleteGroup() {
        boolean deleted = ImageLibrary.deleteGroup(groupName);
        if (deleted) {
            statusText = "Group deleted";
            close();
        } else {
            statusText = "Delete failed";
        }
    }

    private void setSlidersFromColor(int color) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        if (redSlider != null) {
            redSlider.setChannelValue(r);
        }
        if (greenSlider != null) {
            greenSlider.setChannelValue(g);
        }
        if (blueSlider != null) {
            blueSlider.setChannelValue(b);
        }
    }

    private void applySliderColor() {
        int r = redSlider != null ? redSlider.getChannelValue() : 255;
        int g = greenSlider != null ? greenSlider.getChannelValue() : 255;
        int b = blueSlider != null ? blueSlider.getChannelValue() : 255;
        int color = 0xFF000000 | (r << 16) | (g << 8) | b;
        ImageLibrary.setGroupFrameColor(groupName, color);
        statusText = "Color updated";
    }

    private final class ColorSlider extends net.minecraft.client.gui.widget.SliderWidget {
        private final String label;

        private ColorSlider(int x, int y, int width, int height, String label) {
            super(x, y, width, height, Text.literal(label), 0.0);
            this.label = label;
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int value = getChannelValue();
            this.setMessage(Text.literal(label + ": " + value));
        }

        @Override
        protected void applyValue() {
            applySliderColor();
        }

        private int getChannelValue() {
            return (int) Math.round(this.value * 255.0);
        }

        private void setChannelValue(int value) {
            int clamped = Math.max(0, Math.min(255, value));
            this.setValue(clamped / 255.0);
            updateMessage();
        }
    }

    private static final class Row {
        private final String label;
        private final int labelX;
        private final int y;

        private Row(String label, int labelX, int y) {
            this.label = label;
            this.labelX = labelX;
            this.y = y;
        }
    }
}
