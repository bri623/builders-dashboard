package me.eggzman.builders_dashboard.ui;

import me.eggzman.builders_dashboard.images.ImageLibrary;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

public class PhotoPickerScreen extends DashboardBaseScreen {
    private static final int ROW_H = 58;
    private static final int THUMB_SIZE = 48;
    private static final int THUMB_PAD = 6;

    private final Screen parent;
    private TextFieldWidget dirField;
    private String statusText = "";
    private final List<FileItem> items = new ArrayList<>();
    private final Map<Path, Thumb> thumbs = new HashMap<>();
    private int scrollOffset;
    private int maxScroll;
    private int listStartY;
    private int listEndY;
    private final Set<Path> selectedPaths = new HashSet<>();
    private int focusedIndex = -1;
    private int lastClickedIndex = -1;
    private Path currentDir;
    private ButtonWidget addButton;

    public PhotoPickerScreen(Screen parent) {
        super(Text.literal("Photo Picker"));
        this.parent = parent;
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

        int panelW = Math.min(520, this.width - 40);
        int panelH = Math.min(320, this.height - 40);
        int x1 = (this.width - panelW) / 2;
        int y1 = (this.height - panelH) / 2;

        int btnW = 60;
        int btnH = 20;
        int gap = 6;
        int buttonsW = btnW * 3 + gap * 2;

        int fieldX = x1 + 20;
        int fieldY = y1 + 40;
        int fieldW = Math.max(80, panelW - 40 - buttonsW);

        dirField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldW, btnH, Text.literal("Folder path"));
        dirField.setMaxLength(2048);
        addDrawableChild(dirField);

        int buttonsX = fieldX + fieldW + gap;
        addDrawableChild(ButtonWidget.builder(Text.literal("Open"), btn -> openDirFromField())
                .dimensions(buttonsX, fieldY, btnW, btnH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Up"), btn -> openParentDirectory())
                .dimensions(buttonsX + btnW + gap, fieldY, btnW, btnH).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Refresh"), btn -> refreshDirectory())
                .dimensions(buttonsX + (btnW + gap) * 2, fieldY, btnW, btnH).build());

        int bottomY = y1 + panelH - 28;
        int addW = 120;
        int backW = 90;
        int addX = this.width / 2 - addW - 4;
        int backX = this.width / 2 + 4;
        addButton = ButtonWidget.builder(Text.literal("Add Selected"), btn -> addSelected())
                .dimensions(addX, bottomY, addW, btnH).build();
        addDrawableChild(addButton);
        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> close())
                .dimensions(backX, bottomY, backW, btnH).build());

        listStartY = fieldY + 30;
        listEndY = bottomY - 6;

        if (currentDir == null) {
            currentDir = ImageLibrary.getDefaultBrowseDir();
        }
        loadDirectory(currentDir);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int panelW = Math.min(520, this.width - 40);
        int panelH = Math.min(320, this.height - 40);
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
                Text.literal("Photo Picker"),
                this.width / 2,
                y1 + 12,
                0xFFFFFFFF
        );

        int listX1 = x1 + 20;
        int listX2 = x2 - 20;

        context.enableScissor(listX1 - 2, listStartY, listX2 + 2, listEndY);

        if (items.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("No images found in this folder"),
                    this.width / 2,
                    listStartY + 10,
                    0xFFB0B0B0
            );
        } else {
            for (int i = 0; i < items.size(); i++) {
                int y = listStartY + i * ROW_H - scrollOffset;
                if (y + ROW_H < listStartY) {
                    continue;
                }
                if (y > listEndY) {
                    break;
                }

                int thumbX = listX1 + THUMB_PAD;
                int thumbY = y + THUMB_PAD;
                int thumbBox = THUMB_SIZE;
                FileItem item = items.get(i);
                boolean selected = selectedPaths.contains(item.path);
                if (selected) {
                    context.fill(listX1 - 2, y, listX2 + 2, y + ROW_H - 2, 0x332A90FF);
                } else if (item.isDirectory && i == focusedIndex) {
                    context.fill(listX1 - 2, y, listX2 + 2, y + ROW_H - 2, 0x33222222);
                }

                context.fill(thumbX, thumbY, thumbX + thumbBox, thumbY + thumbBox, 0xFF2B2B2B);
                drawBorder(context, thumbX, thumbY, thumbBox, thumbBox, 0xFF3A3A3A);

                if (item.isDirectory) {
                    context.drawTextWithShadow(
                            this.textRenderer,
                            Text.literal("DIR"),
                            thumbX + 14,
                            thumbY + 18,
                            0xFF777777
                    );
                } else {
                    Thumb thumb = ensureThumbnail(item);
                    if (thumb != null) {
                        drawTextureContain(
                                context,
                                thumb.id(),
                                thumbX,
                                thumbY,
                                thumbBox,
                                thumbBox,
                                thumb.width(),
                                thumb.height()
                        );
                    } else if (item.thumbFailed) {
                        context.drawTextWithShadow(
                                this.textRenderer,
                                Text.literal("No preview"),
                                thumbX + 4,
                                thumbY + 18,
                                0xFF777777
                        );
                    } else {
                        context.drawTextWithShadow(
                                this.textRenderer,
                                Text.literal("Loading"),
                                thumbX + 8,
                                thumbY + 18,
                                0xFF777777
                        );
                    }
                }

                int textX = thumbX + thumbBox + 10;
                String prefix = item.isDirectory ? "[DIR] " : "";
                String label = this.textRenderer.trimToWidth(prefix + item.name, Math.max(1, listX2 - textX));
                context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal(label),
                        textX,
                        y + 20,
                        0xFFE0E0E0
                );
            }
        }

        context.disableScissor();

        if (!statusText.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(statusText),
                    this.width / 2,
                    y2 - 46,
                    0xFFB0B0B0
            );
        }

        super.render(context, mouseX, mouseY, delta);
        renderTabs(context, mouseX, mouseY);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xAA000000);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (dirField != null && dirField.isFocused()) {
            int keyCode = input.getKeycode();
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                openDirFromField();
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        int panelW = Math.min(520, this.width - 40);
        int x1 = (this.width - panelW) / 2;
        int x2 = x1 + panelW;
        int listX1 = x1 + 20;
        int listX2 = x2 - 20;
        if (mouseX >= listX1 && mouseX <= listX2 && mouseY >= listStartY && mouseY <= listEndY) {
            scrollOffset -= (int) Math.round(verticalAmount * ROW_H);
            scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
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
        int panelW = Math.min(520, this.width - 40);
        int x1 = (this.width - panelW) / 2;
        int x2 = x1 + panelW;
        int listX1 = x1 + 20;
        int listX2 = x2 - 20;
        if (mouseX >= listX1 && mouseX <= listX2 && mouseY >= listStartY && mouseY <= listEndY) {
            int index = indexFromMouseY(mouseY);
            if (index >= 0 && index < items.size()) {
                FileItem item = items.get(index);
                focusedIndex = index;
                if (item.isDirectory) {
                    if (doubled && click.button() == 0) {
                        loadDirectory(item.path);
                    } else {
                        statusText = "Double-click a folder to open it";
                    }
                    return true;
                }

                if (click.button() == 0) {
                    boolean shift = (click.modifiers() & GLFW.GLFW_MOD_SHIFT) != 0;
                    boolean ctrl = (click.modifiers() & GLFW.GLFW_MOD_CONTROL) != 0;
                    if (doubled) {
                        addFile(item.path);
                    } else if (shift && lastClickedIndex >= 0) {
                        if (!ctrl) {
                            selectedPaths.clear();
                        }
                        selectRange(lastClickedIndex, index);
                    } else if (ctrl) {
                        toggleSelection(item.path);
                    } else {
                        selectedPaths.clear();
                        selectedPaths.add(item.path);
                    }
                    lastClickedIndex = index;
                    updateAddButtonLabel();
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public void close() {
        clearThumbnails();
        MinecraftClient client = this.client;
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void openDirFromField() {
        if (dirField == null) {
            return;
        }
        String raw = dirField.getText().trim();
        if (raw.isEmpty()) {
            statusText = "Enter a folder path";
            return;
        }
        try {
            Path path = Path.of(raw);
            loadDirectory(path);
        } catch (Exception e) {
            statusText = "Invalid folder path";
        }
    }

    private void openParentDirectory() {
        if (currentDir == null) {
            return;
        }
        Path parentDir = currentDir.getParent();
        if (parentDir != null) {
            loadDirectory(parentDir);
        }
    }

    private void refreshDirectory() {
        loadDirectory(currentDir);
    }

    private void loadDirectory(Path dir) {
        clearThumbnails();
        items.clear();
        selectedPaths.clear();
        focusedIndex = -1;
        lastClickedIndex = -1;
        scrollOffset = 0;
        maxScroll = 0;

        if (dir == null || !Files.isDirectory(dir)) {
            statusText = "Folder not found";
            currentDir = dir;
            if (dirField != null && dir != null) {
                dirField.setText(dir.toString());
            }
            return;
        }

        currentDir = dir.toAbsolutePath().normalize();
        if (dirField != null) {
            dirField.setText(currentDir.toString());
        }

        List<Path> dirs = new ArrayList<>();
        List<Path> files = new ArrayList<>();
        try (Stream<Path> stream = Files.list(currentDir)) {
            stream.forEach(path -> {
                if (Files.isDirectory(path)) {
                    dirs.add(path);
                } else if (Files.isRegularFile(path) && isImageFile(path)) {
                    files.add(path);
                }
            });
        } catch (IOException e) {
            statusText = "Could not read folder";
            return;
        }

        Path parentDir = currentDir.getParent();
        if (parentDir != null) {
            items.add(new FileItem(parentDir, "..", true));
        }

        dirs.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
        for (Path path : dirs) {
            items.add(new FileItem(path, path.getFileName().toString(), true));
        }

        files.sort(Comparator.comparing(path -> path.getFileName().toString().toLowerCase(Locale.ROOT)));
        for (Path path : files) {
            items.add(new FileItem(path, path.getFileName().toString(), false));
        }

        if (items.isEmpty()) {
            statusText = "No images found";
        } else {
            statusText = "Ctrl-click to multi-select, Shift-click for range";
        }

        int listHeight = Math.max(0, listEndY - listStartY);
        maxScroll = Math.max(0, items.size() * ROW_H - listHeight);
        updateAddButtonLabel();
    }

    private int indexFromMouseY(double mouseY) {
        int relative = (int) Math.floor(mouseY - listStartY + scrollOffset);
        if (relative < 0) {
            return -1;
        }
        return relative / ROW_H;
    }

    private void addSelected() {
        if (selectedPaths.isEmpty()) {
            statusText = "Select at least one image";
            return;
        }

        int added = 0;
        for (Path path : selectedPaths) {
            if (ImageLibrary.addImageFromPath(path)) {
                added++;
            }
        }
        statusText = added > 0 ? "Added " + added + " image(s)" : "No image added";
    }

    private void addFile(Path path) {
        boolean added = ImageLibrary.addImageFromPath(path);
        statusText = added ? "Image added" : "No image added";
    }

    private void toggleSelection(Path path) {
        if (selectedPaths.contains(path)) {
            selectedPaths.remove(path);
        } else {
            selectedPaths.add(path);
        }
    }

    private void selectRange(int start, int end) {
        int min = Math.min(start, end);
        int max = Math.max(start, end);
        for (int i = min; i <= max; i++) {
            FileItem item = items.get(i);
            if (!item.isDirectory) {
                selectedPaths.add(item.path);
            }
        }
    }

    private void updateAddButtonLabel() {
        if (addButton == null) {
            return;
        }
        int count = selectedPaths.size();
        if (count > 0) {
            addButton.setMessage(Text.literal("Add Selected (" + count + ")"));
        } else {
            addButton.setMessage(Text.literal("Add Selected"));
        }
    }

    private Thumb ensureThumbnail(FileItem item) {
        Thumb existing = item.thumb;
        if (existing != null) {
            return existing;
        }
        if (item.thumbFailed) {
            return null;
        }
        MinecraftClient client = this.client;
        if (client == null) {
            return null;
        }

        NativeImage image = ImageLibrary.readImageForPreview(item.path);
        if (image == null) {
            item.thumbFailed = true;
            return null;
        }
        NativeImage scaled = scaleToMax(image, THUMB_SIZE);
        String hash = Integer.toHexString(item.path.toString().hashCode());
        Identifier id = Identifier.of("builders_dashboard", "thumbs/" + hash);
        NativeImageBackedTexture texture = new NativeImageBackedTexture(
                () -> "builders_dashboard/thumbs/" + hash,
                scaled
        );
        TextureManager textureManager = client.getTextureManager();
        textureManager.registerTexture(id, texture);
        Thumb thumb = new Thumb(id, texture, scaled.getWidth(), scaled.getHeight());
        item.thumb = thumb;
        thumbs.put(item.path, thumb);
        return thumb;
    }

    private void clearThumbnails() {
        MinecraftClient client = this.client;
        if (client != null) {
            TextureManager manager = client.getTextureManager();
            for (Thumb thumb : thumbs.values()) {
                manager.destroyTexture(thumb.id());
            }
        }
        thumbs.clear();
    }

    private static boolean isImageFile(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }

    private static NativeImage scaleToMax(NativeImage source, int maxSize) {
        int srcW = source.getWidth();
        int srcH = source.getHeight();
        if (srcW <= maxSize && srcH <= maxSize) {
            return source;
        }

        float scale = Math.min((float) maxSize / (float) srcW, (float) maxSize / (float) srcH);
        int dstW = Math.max(1, Math.round(srcW * scale));
        int dstH = Math.max(1, Math.round(srcH * scale));
        NativeImage scaled = new NativeImage(dstW, dstH, false);
        float invScale = 1.0f / scale;

        for (int y = 0; y < dstH; y++) {
            float srcY = (y + 0.5f) * invScale - 0.5f;
            int y0 = clampInt((int) Math.floor(srcY), 0, srcH - 1);
            int y1 = clampInt(y0 + 1, 0, srcH - 1);
            float fy = srcY - y0;
            for (int x = 0; x < dstW; x++) {
                float srcX = (x + 0.5f) * invScale - 0.5f;
                int x0 = clampInt((int) Math.floor(srcX), 0, srcW - 1);
                int x1 = clampInt(x0 + 1, 0, srcW - 1);
                float fx = srcX - x0;

                int c00 = source.getColorArgb(x0, y0);
                int c10 = source.getColorArgb(x1, y0);
                int c01 = source.getColorArgb(x0, y1);
                int c11 = source.getColorArgb(x1, y1);

                int a = bilerpChannel(c00, c10, c01, c11, fx, fy, 24);
                int r = bilerpChannel(c00, c10, c01, c11, fx, fy, 16);
                int g = bilerpChannel(c00, c10, c01, c11, fx, fy, 8);
                int b = bilerpChannel(c00, c10, c01, c11, fx, fy, 0);
                int argb = (a << 24) | (r << 16) | (g << 8) | b;
                scaled.setColorArgb(x, y, argb);
            }
        }

        source.close();
        return scaled;
    }

    private static int bilerpChannel(int c00, int c10, int c01, int c11, float fx, float fy, int shift) {
        int v00 = (c00 >> shift) & 0xFF;
        int v10 = (c10 >> shift) & 0xFF;
        int v01 = (c01 >> shift) & 0xFF;
        int v11 = (c11 >> shift) & 0xFF;

        float i1 = v00 + (v10 - v00) * fx;
        float i2 = v01 + (v11 - v01) * fx;
        int value = Math.round(i1 + (i2 - i1) * fy);
        return clampInt(value, 0, 255);
    }

    private static int clampInt(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private static void drawTextureContain(
            DrawContext ctx,
            Identifier texture,
            int x, int y,
            int boxW, int boxH,
            int texW, int texH
    ) {
        int safeW = texW <= 0 ? 1 : texW;
        int safeH = texH <= 0 ? 1 : texH;

        float scale = Math.min((float) boxW / (float) safeW, (float) boxH / (float) safeH);
        int drawW = Math.max(1, Math.round(safeW * scale));
        int drawH = Math.max(1, Math.round(safeH * scale));

        int drawX = x + (boxW - drawW) / 2;
        int drawY = y + (boxH - drawH) / 2;

        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                texture,
                drawX,
                drawY,
                0f,
                0f,
                drawW,
                drawH,
                safeW,
                safeH,
                safeW,
                safeH
        );
    }

    private static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }

    private static final class FileItem {
        private final Path path;
        private final String name;
        private final boolean isDirectory;
        private Thumb thumb;
        private boolean thumbFailed;

        private FileItem(Path path, String name, boolean isDirectory) {
            this.path = path;
            this.name = name;
            this.isDirectory = isDirectory;
        }
    }

    private record Thumb(Identifier id, NativeImageBackedTexture texture, int width, int height) {}
}
