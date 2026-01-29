package me.eggzman.builders_dashboard.ui;

import me.eggzman.builders_dashboard.images.ImageLibrary;
import me.eggzman.builders_dashboard.input.Keybinds;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DashboardHud {

    private static boolean enabled = true;
    private static boolean suppressTooltips;

    private static final int START_X = 8;
    private static final int START_Y = 20;
    private static final int CARD_W = 110;
    private static final int CARD_H = 140;
    private static final int GAP = 10;

    private static ImageLibrary.ImageEntry draggingEntry;
    private static ImageLibrary.ImageEntry clickedEntry;
    private static int dragOffsetX;
    private static int dragOffsetY;
    private static boolean positionsDirty;
    private static double dragStartX;
    private static double dragStartY;
    private static boolean dragMoved;

    // Files must be in:
    // src/main/resources/assets/builders_dashboard/textures/polaroids/
    private static final Identifier SHOT1 =
            Identifier.of("builders_dashboard", "textures/polaroids/shot1.png");
    private static final Identifier SHOT2 =
            Identifier.of("builders_dashboard", "textures/polaroids/shot2.png");
    private static final Identifier PALETTE =
            Identifier.of("builders_dashboard", "textures/polaroids/palette.png");
    private static final Identifier THUMBTACK =
            Identifier.of("builders_dashboard", "textures/ui/thumbtack.png");
    private static final Identifier THUMBTACK_HOVER =
            Identifier.of("builders_dashboard", "textures/ui/thumbtack_hover.png");

    // Cache PNG sizes so we don't re-read them every frame
    private static final Map<Identifier, Size> SIZE_CACHE = new HashMap<>();

    private DashboardHud() {}

    public static void init() {
        // Draw AFTER the current screen renders, so we are on top of the inventory
        ScreenEvents.AFTER_INIT.register((client, screen, scaledWidth, scaledHeight) -> {
            ScreenEvents.afterRender(screen).register((screen1, ctx, mouseX, mouseY, tickDelta) -> {
                if (!enabled) {
                    return;
                }

                // Only while inventory/chests/etc are open
                if (!(screen1 instanceof HandledScreen<?>)) return;

                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || mc.player == null) return;

                ctx.createNewRootLayer();
                renderOverlay(ctx, mc, mouseX, mouseY);
            });

            ScreenMouseEvents.allowMouseClick(screen).register((screen1, click) -> {
                if (!enabled || !(screen1 instanceof HandledScreen<?>)) {
                    return true;
                }

                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || mc.player == null) return true;

                boolean handled = handleClick(click, mc, screen1);
                return !handled;
            });

            ScreenMouseEvents.allowMouseDrag(screen).register((screen1, click, horizontalAmount, verticalAmount) -> {
                if (!enabled || !(screen1 instanceof HandledScreen<?>)) {
                    return true;
                }

                if (draggingEntry == null) {
                    return true;
                }

                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || mc.player == null) return true;

                handleDrag(click, mc);
                return false;
            });

            ScreenMouseEvents.allowMouseRelease(screen).register((screen1, click) -> {
                if (!enabled || !(screen1 instanceof HandledScreen<?>)) {
                    return true;
                }

                if (draggingEntry == null && clickedEntry == null) {
                    return true;
                }

                handleRelease(click, screen1);
                return false;
            });

            ScreenMouseEvents.allowMouseScroll(screen).register((screen1, mouseX, mouseY, horizontalAmount, verticalAmount) -> {
                if (!enabled || !(screen1 instanceof HandledScreen<?>)) {
                    return true;
                }

                MinecraftClient mc = MinecraftClient.getInstance();
                if (mc == null || mc.player == null) return true;

                boolean handled = handleScroll(mouseX, mouseY, verticalAmount, mc);
                return !handled;
            });
        });

        HudRenderCallback.EVENT.register((ctx, tickCounter) -> {
            if (!enabled) {
                return;
            }

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) {
                return;
            }

            if (mc.currentScreen != null) {
                return;
            }

            renderPinned(ctx, mc);
        });
    }

    public static void toggle() {
        enabled = !enabled;
    }

    public static void setSuppressTooltips(boolean suppress) {
        suppressTooltips = suppress;
    }

    static void renderOverlay(DrawContext ctx, MinecraftClient client, int mouseX, int mouseY) {
        List<ImageLibrary.ImageEntry> images = ImageLibrary.getEnabledImages();
        renderImages(ctx, client, mouseX, mouseY, images, true, true, true);
    }

    private static void renderPinned(DrawContext ctx, MinecraftClient client) {
        List<ImageLibrary.ImageEntry> images = ImageLibrary.getPinnedImages();
        renderImages(ctx, client, -1, -1, images, false, false, false);
    }

    private static void renderImages(
            DrawContext ctx,
            MinecraftClient client,
            int mouseX,
            int mouseY,
            List<ImageLibrary.ImageEntry> images,
            boolean showPlaceholders,
            boolean showPinButtons,
            boolean allowTooltips
    ) {
        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();

        if (images.isEmpty()) {
            return;
        }

        boolean changed = ensurePositions(images, screenW);
        if (changed) {
            ImageLibrary.savePositions();
        }

        for (ImageLibrary.ImageEntry entry : images) {
            int drawX = entry.x();
            int drawY = entry.y();
            int cardW = cardW(entry);
            int cardH = cardH(entry);

            String caption = entry.filePath().getFileName().toString();
            caption = stripExtension(caption);
            caption = client.textRenderer.trimToWidth(caption, Math.max(1, cardW - 16));

            if (drawY + cardH > screenH) {
                continue;
            }

            int frameColor = ImageLibrary.getFrameColorForImage(entry);
            drawPolaroid(ctx, client, drawX, drawY, cardW, cardH, entry.textureId(), caption, entry.width(), entry.height(), entry.opacity(), frameColor, entry.note());

            if (showPinButtons || entry.isPinned()) {
                PinBounds pin = pinBounds(drawX, drawY, cardW);
                boolean hoveredPin = showPinButtons && mouseX >= 0 && pin.contains(mouseX, mouseY);
                boolean activePin = showPinButtons && entry.isPinned();
                drawPin(ctx, pin, activePin, hoveredPin, entry.opacity());
            }
        }

        if (!allowTooltips || !Keybinds.areTooltipsEnabled()) {
            return;
        }

        ImageLibrary.ImageEntry hovered = findHovered(images, mouseX, mouseY);
        if (!suppressTooltips && hovered != null) {
            List<OrderedText> tooltip = buildTooltip(client, hovered, screenW);
            if (!tooltip.isEmpty()) {
                ctx.createNewRootLayer();
                ctx.drawOrderedTooltip(client.textRenderer, tooltip, mouseX, mouseY);
                ctx.drawDeferredElements();
            }
        }
    }

    private static List<OrderedText> buildTooltip(MinecraftClient client, ImageLibrary.ImageEntry entry, int screenW) {
        List<OrderedText> tooltip = new ArrayList<>();
        if (entry.paletteId() > 0 && !entry.paletteBlocks().isEmpty()) {
            tooltip.add(Text.literal("Palette #" + entry.paletteId()).asOrderedText());
            for (String block : entry.paletteBlocks()) {
                String label = formatBlockName(block);
                if (!label.isBlank()) {
                    tooltip.add(Text.literal("- " + label).asOrderedText());
                }
            }
        }

        String note = entry.note();
        if (note != null && !note.isBlank()) {
            int wrapWidth = Math.max(160, Math.min(240, screenW / 3));
            if (!tooltip.isEmpty()) {
                tooltip.add(Text.literal("Note:").asOrderedText());
            }
            tooltip.addAll(client.textRenderer.wrapLines(Text.literal(note), wrapWidth));
        }
        return tooltip;
    }

    private static boolean ensurePositions(List<ImageLibrary.ImageEntry> images, int screenW) {
        boolean changed = false;
        int usableW = Math.max(1, screenW - START_X - 8);
        int cols = Math.max(1, usableW / (CARD_W + GAP));
        for (int i = 0; i < images.size(); i++) {
            ImageLibrary.ImageEntry entry = images.get(i);
            if (!entry.hasPosition()) {
                int col = i % cols;
                int row = i / cols;
                int drawX = START_X + col * (CARD_W + GAP);
                int drawY = START_Y + row * (CARD_H + GAP);
                ImageLibrary.setPosition(entry, drawX, drawY, false);
                changed = true;
            }
        }
        return changed;
    }

    private static boolean handleClick(Click click, MinecraftClient client, Screen screen) {
        if (click.button() == 1) {
            List<ImageLibrary.ImageEntry> images = ImageLibrary.getEnabledImages();
            if (images.isEmpty()) {
                return false;
            }

            ensurePositions(images, client.getWindow().getScaledWidth());
            for (int i = images.size() - 1; i >= 0; i--) {
                ImageLibrary.ImageEntry entry = images.get(i);
                if (isInside(entry, click.x(), click.y())) {
                    int cardW = cardW(entry);
                    int cardH = cardH(entry);
                    ImageLibrary.markImageUsed(entry);
                    client.setScreen(new NoteEditScreen(screen, entry, entry.x(), entry.y(), cardW, cardH));
                    return true;
                }
            }

            return false;
        }

        if (click.button() == 0) {
            List<ImageLibrary.ImageEntry> images = ImageLibrary.getEnabledImages();
            if (!images.isEmpty()) {
                ensurePositions(images, client.getWindow().getScaledWidth());
                for (int i = images.size() - 1; i >= 0; i--) {
                    ImageLibrary.ImageEntry entry = images.get(i);
                    int cardW = cardW(entry);
                    PinBounds pin = pinBounds(entry.x(), entry.y(), cardW);
                    if (pin.contains(click.x(), click.y())) {
                        ImageLibrary.togglePinned(entry);
                        return true;
                    }
                }
            }
        }

        if (click.button() != 0) {
            return false;
        }

        List<ImageLibrary.ImageEntry> images = ImageLibrary.getEnabledImages();
        if (images.isEmpty()) {
            return false;
        }

        ensurePositions(images, client.getWindow().getScaledWidth());

        for (int i = images.size() - 1; i >= 0; i--) {
            ImageLibrary.ImageEntry entry = images.get(i);
            if (isInside(entry, click.x(), click.y())) {
                if (entry.isLocked()) {
                    clickedEntry = entry;
                    dragStartX = click.x();
                    dragStartY = click.y();
                    dragMoved = false;
                } else {
                    draggingEntry = entry;
                    dragOffsetX = (int) click.x() - entry.x();
                    dragOffsetY = (int) click.y() - entry.y();
                    dragStartX = click.x();
                    dragStartY = click.y();
                    dragMoved = false;
                }
                return true;
            }
        }

        return false;
    }

    private static boolean handleDrag(Click click, MinecraftClient client) {
        if (draggingEntry == null) {
            return false;
        }

        int screenW = client.getWindow().getScaledWidth();
        int screenH = client.getWindow().getScaledHeight();
        int cardW = cardW(draggingEntry);
        int cardH = cardH(draggingEntry);
        int newX = (int) click.x() - dragOffsetX;
        int newY = (int) click.y() - dragOffsetY;
        if (!dragMoved) {
            double dx = click.x() - dragStartX;
            double dy = click.y() - dragStartY;
            dragMoved = (dx * dx + dy * dy) > 9.0;
        }

        newX = Math.max(0, Math.min(screenW - cardW, newX));
        newY = Math.max(0, Math.min(screenH - cardH, newY));

        ImageLibrary.setPosition(draggingEntry, newX, newY, false);
        positionsDirty = true;
        return true;
    }

    private static boolean handleRelease(Click click, Screen screen) {
        if (draggingEntry == null && clickedEntry == null) {
            return false;
        }

        ImageLibrary.ImageEntry entryToOpen = draggingEntry != null ? draggingEntry : clickedEntry;
        if (!dragMoved && entryToOpen != null) {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client != null) {
                ImageLibrary.markImageUsed(entryToOpen);
                client.setScreen(new PhotoViewerScreen(screen, entryToOpen));
            }
        }

        draggingEntry = null;
        clickedEntry = null;
        dragOffsetX = 0;
        dragOffsetY = 0;
        dragStartX = 0;
        dragStartY = 0;
        dragMoved = false;
        if (positionsDirty) {
            ImageLibrary.savePositions();
            positionsDirty = false;
        }
        return true;
    }

    private static boolean isInside(ImageLibrary.ImageEntry entry, double mouseX, double mouseY) {
        int x = entry.x();
        int y = entry.y();
        int cardW = cardW(entry);
        int cardH = cardH(entry);
        return mouseX >= x && mouseX <= x + cardW && mouseY >= y && mouseY <= y + cardH;
    }

    private static ImageLibrary.ImageEntry findHovered(List<ImageLibrary.ImageEntry> images, double mouseX, double mouseY) {
        for (int i = images.size() - 1; i >= 0; i--) {
            ImageLibrary.ImageEntry entry = images.get(i);
            if (isInside(entry, mouseX, mouseY)) {
                return entry;
            }
        }
        return null;
    }

    private static boolean handleScroll(double mouseX, double mouseY, double verticalAmount, MinecraftClient client) {
        List<ImageLibrary.ImageEntry> images = ImageLibrary.getEnabledImages();
        if (images.isEmpty()) {
            return false;
        }

        ensurePositions(images, client.getWindow().getScaledWidth());

        for (int i = images.size() - 1; i >= 0; i--) {
            ImageLibrary.ImageEntry entry = images.get(i);
            if (isInside(entry, mouseX, mouseY)) {
                if (entry.isLocked()) {
                    return true;
                }
                float delta = (float) (verticalAmount * 0.1f);
                float newScale = entry.scale() + delta;
                int screenW = client.getWindow().getScaledWidth();
                int screenH = client.getWindow().getScaledHeight();
                ImageLibrary.setScale(entry, newScale, false);
                int cardW = cardW(entry);
                int cardH = cardH(entry);
                int newX = Math.max(0, Math.min(screenW - cardW, entry.x()));
                int newY = Math.max(0, Math.min(screenH - cardH, entry.y()));
                ImageLibrary.setPosition(entry, newX, newY, false);
                ImageLibrary.savePositions();
                return true;
            }
        }

        return false;
    }

    private static int cardW(ImageLibrary.ImageEntry entry) {
        return Math.max(1, Math.round(CARD_W * entry.scale()));
    }

    private static int cardH(ImageLibrary.ImageEntry entry) {
        return Math.max(1, Math.round(CARD_H * entry.scale()));
    }

    private static void drawPolaroid(
            DrawContext ctx,
            MinecraftClient client,
            int x, int y, int w, int h,
            Identifier texture,
            String caption
    ) {
        int frame = 0xFFF5F5F5;
        int border = 0xFFB0B0B0;
        int shadow = 0x66000000;

        // shadow
        ctx.fill(x + 2, y + 2, x + w + 2, y + h + 2, shadow);

        // frame
        ctx.fill(x, y, x + w, y + h, frame);
        drawBorder(ctx, x, y, w, h, border);

        int padding = 6;
        int captionH = 22;

        int imgX = x + padding;
        int imgY = y + padding;
        int imgW = w - padding * 2;
        int imgH = h - padding * 2 - captionH;

        // draw image (fit/contain so it doesn't zoom-crop)
        drawTextureContain(ctx, client, texture, imgX, imgY, imgW, imgH);

        // caption (no shadow = easier to read)
        ctx.drawText(
                client.textRenderer,
                Text.literal(caption),
                x + 8,
                y + h - 16,
                0xFF222222,
                false
        );
    }

    private static void drawPolaroid(
            DrawContext ctx,
            MinecraftClient client,
            int x, int y, int w, int h,
            Identifier texture,
            String caption,
            int texW,
            int texH,
            float opacity,
            int frameColor,
            String note
    ) {
        int baseFrame = ensureOpaque(frameColor);
        int baseBorder = darkenColor(baseFrame, 0.72f);
        int frame = applyOpacity(baseFrame, opacity);
        int border = applyOpacity(baseBorder, opacity);
        int shadow = applyOpacity(0x66000000, opacity);

        // shadow
        ctx.fill(x + 2, y + 2, x + w + 2, y + h + 2, shadow);

        // frame
        ctx.fill(x, y, x + w, y + h, frame);
        drawBorder(ctx, x, y, w, h, border);

        int padding = Math.max(1, Math.round(6 * (w / (float) CARD_W)));
        int captionH = Math.max(1, Math.round(22 * (w / (float) CARD_W)));

        int imgX = x + padding;
        int imgY = y + padding;
        int imgW = w - padding * 2;
        int imgH = h - padding * 2 - captionH;

        // draw image (fit/contain so it doesn't zoom-crop)
        drawTextureContain(ctx, texture, imgX, imgY, imgW, imgH, texW, texH, opacity);

        // caption (no shadow = easier to read)
        ctx.drawText(
                client.textRenderer,
                Text.literal(caption),
                x + 8,
                y + h - 16,
                applyOpacity(0xFF222222, opacity),
                false
        );

        drawChecklistBadge(ctx, client, x, y, w, opacity, note);
    }

    /**
     * Draws an image into a box WITHOUT cropping (contain/fit).
     * Uses the real PNG size so random images won't look zoomed.
     */
    private static void drawTextureContain(
            DrawContext ctx,
            MinecraftClient client,
            Identifier texture,
            int x, int y,
            int boxW, int boxH
    ) {
        Size s = getSize(client, texture);
        drawTextureContain(ctx, texture, x, y, boxW, boxH, s.w, s.h, 1.0f);
    }

    private static void drawTextureContain(
            DrawContext ctx,
            Identifier texture,
            int x, int y,
            int boxW, int boxH,
            int texW, int texH,
            float opacity
    ) {
        int safeW = texW <= 0 ? 256 : texW;
        int safeH = texH <= 0 ? 256 : texH;

        float scale = Math.min((float) boxW / (float) safeW, (float) boxH / (float) safeH);
        int drawW = Math.max(1, Math.round(safeW * scale));
        int drawH = Math.max(1, Math.round(safeH * scale));

        int drawX = x + (boxW - drawW) / 2;
        int drawY = y + (boxH - drawH) / 2;

        // background (letterbox)
        ctx.fill(x, y, x + boxW, y + boxH, applyOpacity(0xFF2B2B2B, opacity));

        // Pass real textureW/textureH here so the full image maps correctly
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
                safeH,
                colorWithOpacity(opacity)
        );

        // subtle inner border around the image area
        drawBorder(ctx, x, y, boxW, boxH, applyOpacity(0xFF2A2A2A, opacity));
    }

    private static int applyOpacity(int argb, float opacity) {
        int alpha = (argb >>> 24) & 0xFF;
        int newAlpha = Math.round(alpha * opacity);
        return (argb & 0x00FFFFFF) | (newAlpha << 24);
    }

    private static int colorWithOpacity(float opacity) {
        int alpha = Math.round(255 * opacity);
        return (alpha << 24) | 0xFFFFFF;
    }

    private static int ensureOpaque(int argb) {
        return (argb & 0x00FFFFFF) | 0xFF000000;
    }

    private static int darkenColor(int argb, float factor) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        r = Math.max(0, Math.min(255, Math.round(r * factor)));
        g = Math.max(0, Math.min(255, Math.round(g * factor)));
        b = Math.max(0, Math.min(255, Math.round(b * factor)));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    private static String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private static String formatBlockName(String block) {
        if (block == null) {
            return "";
        }
        String trimmed = block.trim().toLowerCase();
        if (trimmed.isEmpty()) {
            return "";
        }
        String[] parts = trimmed.split("_");
        StringBuilder out = new StringBuilder(trimmed.length());
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase());
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.toString();
    }

    private static Size getSize(MinecraftClient client, Identifier id) {
        Size cached = SIZE_CACHE.get(id);
        if (cached != null) return cached;

        try {
            Optional<Resource> resOpt = client.getResourceManager().getResource(id);
            if (resOpt.isEmpty()) {
                Size s = new Size(256, 256);
                SIZE_CACHE.put(id, s);
                return s;
            }

            Resource res = resOpt.get();
            try (InputStream in = res.getInputStream();
                 NativeImage img = NativeImage.read(in)) {
                Size s = new Size(img.getWidth(), img.getHeight());
                SIZE_CACHE.put(id, s);
                return s;
            }
        } catch (Throwable t) {
            Size s = new Size(256, 256);
            SIZE_CACHE.put(id, s);
            return s;
        }
    }

    private static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);             // top
        ctx.fill(x, y + h - 1, x + w, y + h, color);     // bottom
        ctx.fill(x, y, x + 1, y + h, color);             // left
        ctx.fill(x + w - 1, y, x + w, y + h, color);     // right
    }

    private static PinBounds pinBounds(int cardX, int cardY, int cardW) {
        float scale = cardW / (float) CARD_W;
        int size = Math.max(8, Math.round(12 * scale));
        int pad = Math.max(6, Math.round(8 * scale));
        int x = cardX + cardW - pad - size;
        int y = cardY + cardHScaled(cardW) - pad - size;
        return new PinBounds(x, y, size);
    }

    private static int cardHScaled(int cardW) {
        return Math.max(1, Math.round(CARD_H * (cardW / (float) CARD_W)));
    }

    private static void drawPin(DrawContext ctx, PinBounds pin, boolean active, boolean hovered, float opacity) {
        Identifier texture = (active || hovered) ? THUMBTACK : THUMBTACK_HOVER;
        int size = pin.size();
        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                texture,
                pin.x(),
                pin.y(),
                0f,
                0f,
                size,
                size,
                16,
                16,
                16,
                16,
                colorWithOpacity(opacity)
        );
    }

    private static void drawChecklistBadge(
            DrawContext ctx,
            MinecraftClient client,
            int x,
            int y,
            int w,
            float opacity,
            String note
    ) {
        ChecklistInfo info = getChecklistInfo(note);
        if (info.total == 0) {
            return;
        }
        String label = info.done + "/" + info.total;
        int textW = client.textRenderer.getWidth(label);
        int pad = 4;
        int badgeW = textW + pad * 2;
        int badgeH = client.textRenderer.fontHeight + 2;
        int badgeX = x + w - badgeW - 6;
        int badgeY = y + 6;
        int bg = applyOpacity(0xFF1E1E1E, opacity);
        int border = applyOpacity(0xFF3A3A3A, opacity);
        ctx.fill(badgeX, badgeY, badgeX + badgeW, badgeY + badgeH, bg);
        drawBorder(ctx, badgeX, badgeY, badgeW, badgeH, border);
        ctx.drawText(
                client.textRenderer,
                Text.literal(label),
                badgeX + pad,
                badgeY + 1,
                applyOpacity(0xFFEFEFEF, opacity),
                false
        );
    }

    private static ChecklistInfo getChecklistInfo(String note) {
        if (note == null || note.isBlank()) {
            return new ChecklistInfo(0, 0);
        }
        String[] lines = note.split("\\R");
        int total = 0;
        int done = 0;
        for (String raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.trim();
            if (line.startsWith("-")) {
                line = line.substring(1).trim();
            }
            if (line.startsWith("[ ]")) {
                total++;
            } else if (line.startsWith("[x]") || line.startsWith("[X]")) {
                total++;
                done++;
            }
        }
        return new ChecklistInfo(total, done);
    }

    private record PinBounds(int x, int y, int size) {
        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + size && mouseY >= y && mouseY <= y + size;
        }
    }

    private record Size(int w, int h) {}

    private record ChecklistInfo(int total, int done) {}
}
