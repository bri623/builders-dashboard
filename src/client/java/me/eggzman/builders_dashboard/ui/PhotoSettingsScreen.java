package me.eggzman.builders_dashboard.ui;

import me.eggzman.builders_dashboard.images.ImageLibrary;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class PhotoSettingsScreen extends DashboardBaseScreen {
    private TextFieldWidget pathField;
    private String statusText = "";
    private final List<ButtonWidget> listButtons = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<ImageLibrary.ImageEntry> listEntries = new ArrayList<>();
    private ImageLibrary.ImageEntry selectedEntry;
    private OpacitySlider opacitySlider;
    private ButtonWidget viewButton;
    private int listStartY;
    private int listEndY;
    private int lastImageCount = -1;
    private String recentKey = "";
    private int scrollOffset = 0;
    private int maxScroll = 0;
    private int draggingIndex = -1;
    private int dragTargetIndex = -1;
    private final int rowH = 22;
    private ViewMode viewMode = ViewMode.ALL;

    public PhotoSettingsScreen() {
        super(Text.literal("Photo Settings"));
    }

    @Override
    protected void init() {
        super.init();
        clearTabs();
        addTab("Home", DashboardScreen::new, false);
        addTab("Photos", PhotoSettingsScreen::new, true);
        addTab("Block Palettes", BlockPalettesScreen::new, false);
        addTab("Groups", PinGroupsScreen::new, false);
        addTab("Keybinds", KeybindsScreen::new, false);

        int panelW = Math.min(360, this.width - 40);
        int panelH = Math.min(240, this.height - 40);
        int x1 = (this.width - panelW) / 2;
        int y1 = (this.height - panelH) / 2;

        int buttonW = 120;
        int buttonH = 20;
        int viewW = 110;
        int gap = 8;
        int totalW = buttonW + viewW + gap;
        int buttonX = x1 + (panelW - totalW) / 2;
        int buttonY = y1 + 50;

        addDrawableChild(ButtonWidget.builder(Text.literal("Add Image"), btn -> {
            if (this.client == null) {
                return;
            }
            statusText = "Opening file explorer...";
            MinecraftClient client = this.client;
            Thread pickerThread = new Thread(() -> {
                ImageLibrary.PickerSelection selection = ImageLibrary.pickFile();
                client.execute(() -> handlePickerSelection(selection));
            }, "BuildersDashboard-FilePicker");
            pickerThread.setDaemon(true);
            pickerThread.start();
        }).dimensions(buttonX, buttonY, buttonW, buttonH).build());

        viewButton = ButtonWidget.builder(Text.literal(viewLabel()), btn -> cycleViewMode())
                .dimensions(buttonX + buttonW + gap, buttonY, viewW, buttonH).build();
        addDrawableChild(viewButton);

        int fieldW = panelW - 40;
        int fieldH = 20;
        int fieldX = x1 + 20;
        int fieldY = buttonY + 40;

        pathField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldW, fieldH, Text.literal("Image path"));
        pathField.setMaxLength(1024);
        pathField.setPlaceholder(Text.literal("C:\\path\\to\\image.png"));
        addDrawableChild(pathField);

        addDrawableChild(ButtonWidget.builder(Text.literal("Add Path"), btn -> {
            String raw = pathField.getText().trim();
            if (raw.isEmpty()) {
                statusText = "Enter a file path";
                return;
            }

            Path path;
            try {
                path = Path.of(raw);
            } catch (InvalidPathException ex) {
                statusText = "Invalid path";
                return;
            }

            int before = ImageLibrary.getImages().size();
            boolean added = ImageLibrary.addImageFromPath(path);
            int after = ImageLibrary.getImages().size();

            if (added || after > before) {
                statusText = "Image added";
                pathField.setText("");
            } else {
                statusText = "No image added";
            }

            rebuildList();
        }).dimensions(buttonX, fieldY + 26, buttonW, buttonH).build());

        int sliderW = panelW - 40;
        int sliderH = 20;
        int sliderX = x1 + 20;
        int sliderY = y1 + panelH - 30;

        opacitySlider = new OpacitySlider(sliderX, sliderY, sliderW, sliderH);
        addDrawableChild(opacitySlider);

        listStartY = fieldY + 60;
        listEndY = sliderY - 6;
        rebuildList();
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int currentCount = ImageLibrary.getImages().size();
        if (currentCount != lastImageCount) {
            rebuildList();
        }
        if (viewMode == ViewMode.RECENT) {
            String key = buildRecentKey();
            if (!key.equals(recentKey)) {
                recentKey = key;
                rebuildList();
            }
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
                Text.literal("Photo Settings"),
                this.width / 2,
                y1 + 12,
                0xFFFFFFFF
        );

        if (!statusText.isEmpty()) {
            int statusY = pathField != null ? pathField.getY() + 52 : y1 + 150;
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
                if (row.entry == selectedEntry) {
                    context.fill(row.labelX - 4, row.y, row.labelX + pathField.getWidth(), row.y + rowH, 0x331E90FF);
                }
                context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal(row.label),
                        row.labelX,
                        row.y + 6,
                        0xFFDDDDDD
                );
            }
        }

        if (draggingIndex != -1 && dragTargetIndex != -1) {
            int lineY = listStartY + dragTargetIndex * rowH - scrollOffset;
            if (lineY >= listStartY && lineY <= listEndY) {
                context.fill(pathField.getX(), lineY - 1, pathField.getX() + pathField.getWidth(), lineY + 1, 0xFFAAAAAA);
            }
        }

        context.disableScissor();

        setListButtonsVisible(false);
        super.render(context, mouseX, mouseY, delta);
        setListButtonsVisible(true);

        context.enableScissor(listX1, listStartY, listX2, listEndY);
        for (ButtonWidget btn : listButtons) {
            btn.render(context, mouseX, mouseY, delta);
        }
        context.disableScissor();

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
        if (mouseX >= pathField.getX()
                && mouseX <= pathField.getX() + pathField.getWidth()
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
        if (super.mouseClicked(click, doubled)) {
            return true;
        }

        double mouseX = click.x();
        double mouseY = click.y();
        if (click.button() == 0 && mouseY >= listStartY && mouseY <= listEndY) {
            boolean overButton = false;
            for (ButtonWidget btn : listButtons) {
                if (btn.isMouseOver(mouseX, mouseY)) {
                    overButton = true;
                    break;
                }
            }
            if (!overButton) {
                int index = indexFromMouseY(mouseY);
                if (index >= 0 && index < listEntries.size()) {
                    selectEntry(listEntries.get(index));
                    if (viewMode == ViewMode.ALL) {
                        draggingIndex = index;
                        dragTargetIndex = index;
                    }
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (viewMode == ViewMode.ALL && draggingIndex != -1) {
            int index = indexFromMouseY(click.y());
            if (index >= 0 && index < listEntries.size()) {
                dragTargetIndex = index;
            }
            return true;
        }

        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (viewMode == ViewMode.ALL && draggingIndex != -1) {
            if (dragTargetIndex != -1 && dragTargetIndex != draggingIndex) {
                ImageLibrary.moveImage(listEntries.get(draggingIndex), dragTargetIndex);
            }
            draggingIndex = -1;
            dragTargetIndex = -1;
            rebuildList();
            return true;
        }

        return super.mouseReleased(click);
    }

    private void rebuildList() {
        for (ButtonWidget btn : listButtons) {
            remove(btn);
        }
        listButtons.clear();
        rows.clear();
        listEntries.clear();

        if (this.client == null) {
            return;
        }

        List<ImageLibrary.ImageEntry> images;
        if (viewMode == ViewMode.FAVORITES) {
            images = ImageLibrary.getFavoriteImages();
        } else if (viewMode == ViewMode.RECENT) {
            images = ImageLibrary.getRecentImages();
        } else {
            images = ImageLibrary.getImages();
        }
        lastImageCount = images.size();
        listEntries.addAll(images);
        if (selectedEntry != null && !listEntries.contains(selectedEntry)) {
            selectedEntry = null;
        }
        if (selectedEntry == null && !listEntries.isEmpty()) {
            selectedEntry = listEntries.get(0);
        }
        updateOpacitySlider();
        int listHeight = Math.max(0, listEndY - listStartY);
        maxScroll = Math.max(0, images.size() * rowH - listHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        int y = listStartY - scrollOffset;

        for (int i = 0; i < images.size(); i++) {
            ImageLibrary.ImageEntry entry = images.get(i);
            if (y + rowH < listStartY) {
                y += rowH;
                continue;
            }
            if (y + rowH > listEndY) {
                break;
            }

            String name = entry.filePath().getFileName().toString();
            name = stripExtension(name);

            int labelX = pathField.getX();
            int starW = 50;
            int toggleW = 60;
            int lockW = 60;
            int removeW = 60;
            int gap = 6;
            int rightX = pathField.getX() + pathField.getWidth()
                    - (starW + toggleW + lockW + removeW + gap * 3);

            ButtonWidget starBtn = ButtonWidget.builder(
                    Text.literal(entry.isFavorite() ? "Unstar" : "Star"),
                    btn -> {
                        ImageLibrary.toggleFavorite(entry);
                        rebuildList();
                    }
            ).dimensions(rightX, y + 1, starW, 20).build();

            ButtonWidget toggle = ButtonWidget.builder(
                    Text.literal(entry.isEnabled() ? "Hide" : "Show"),
                    btn -> {
                        ImageLibrary.toggleEnabled(entry);
                        rebuildList();
                    }
            ).dimensions(rightX + starW + gap, y + 1, toggleW, 20).build();

            ButtonWidget lockBtn = ButtonWidget.builder(
                    Text.literal(entry.isLocked() ? "Unlock" : "Lock"),
                    btn -> {
                        ImageLibrary.toggleLocked(entry);
                        rebuildList();
                    }
            ).dimensions(rightX + starW + toggleW + gap * 2, y + 1, lockW, 20).build();

            ButtonWidget removeBtn = ButtonWidget.builder(
                    Text.literal("Remove"),
                    btn -> {
                        ImageLibrary.removeImage(entry);
                        rebuildList();
                    }
            ).dimensions(rightX + starW + toggleW + lockW + gap * 3, y + 1, removeW, 20).build();

            addDrawableChild(starBtn);
            addDrawableChild(toggle);
            addDrawableChild(lockBtn);
            addDrawableChild(removeBtn);
            listButtons.add(starBtn);
            listButtons.add(toggle);
            listButtons.add(lockBtn);
            listButtons.add(removeBtn);
            rows.add(new Row(entry, name, labelX, y));

            y += rowH;
        }
    }

    private int indexFromMouseY(double mouseY) {
        int relative = (int) Math.floor(mouseY - listStartY + scrollOffset);
        if (relative < 0) {
            return -1;
        }
        return relative / rowH;
    }

    private static String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private static final class Row {
        private final ImageLibrary.ImageEntry entry;
        private final String label;
        private final int labelX;
        private final int y;

        private Row(ImageLibrary.ImageEntry entry, String label, int labelX, int y) {
            this.entry = entry;
            this.label = label;
            this.labelX = labelX;
            this.y = y;
        }
    }

    private void selectEntry(ImageLibrary.ImageEntry entry) {
        this.selectedEntry = entry;
        updateOpacitySlider();
    }

    private String buildRecentKey() {
        StringBuilder out = new StringBuilder();
        for (ImageLibrary.ImageEntry entry : ImageLibrary.getRecentImages()) {
            out.append(entry.filePath().toString()).append('|');
        }
        return out.toString();
    }

    private void updateOpacitySlider() {
        if (opacitySlider == null) {
            return;
        }

        if (selectedEntry == null) {
            opacitySlider.active = false;
            opacitySlider.setOpacityValue(1.0f);
        } else {
            opacitySlider.active = true;
            opacitySlider.setOpacityValue(selectedEntry.opacity());
        }
    }

    private void handlePickerSelection(ImageLibrary.PickerSelection selection) {
        if (selection == null) {
            statusText = "No image added";
            return;
        }

        if (selection.result() == ImageLibrary.PickerResult.SELECTED && selection.path() != null) {
            boolean added = ImageLibrary.addImageFromPath(selection.path());
            statusText = added ? "Image added" : "No image added";
            rebuildList();
            return;
        }

        if (selection.result() == ImageLibrary.PickerResult.FAILED) {
            String error = ImageLibrary.getLastPickerError();
            if (error == null || error.isBlank()) {
                statusText = "File explorer failed to open";
            } else {
                statusText = "File explorer failed: " + error;
            }
            return;
        }

        statusText = "Picker cancelled";
    }

    private void cycleViewMode() {
        if (viewMode == ViewMode.ALL) {
            viewMode = ViewMode.FAVORITES;
        } else if (viewMode == ViewMode.FAVORITES) {
            viewMode = ViewMode.RECENT;
        } else {
            viewMode = ViewMode.ALL;
        }
        if (viewButton != null) {
            viewButton.setMessage(Text.literal(viewLabel()));
        }
        recentKey = buildRecentKey();
        scrollOffset = 0;
        draggingIndex = -1;
        dragTargetIndex = -1;
        rebuildList();
    }

    private String viewLabel() {
        if (viewMode == ViewMode.FAVORITES) {
            return "View: Favorites";
        }
        if (viewMode == ViewMode.RECENT) {
            return "View: Recent";
        }
        return "View: All";
    }

    private void setListButtonsVisible(boolean visible) {
        for (ButtonWidget btn : listButtons) {
            btn.visible = visible;
        }
    }

    private enum ViewMode {
        ALL,
        FAVORITES,
        RECENT
    }

    private final class OpacitySlider extends SliderWidget {
        private OpacitySlider(int x, int y, int width, int height) {
            super(x, y, width, height, Text.literal("Opacity"), 1.0);
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            int percent = (int) Math.round(this.value * 100);
            this.setMessage(Text.literal("Opacity: " + percent + "%"));
        }

        @Override
        protected void applyValue() {
            if (selectedEntry != null) {
                ImageLibrary.setOpacity(selectedEntry, (float) this.value, true);
            }
        }

        private void setOpacityValue(float value) {
            this.setValue(value);
        }
    }

}
