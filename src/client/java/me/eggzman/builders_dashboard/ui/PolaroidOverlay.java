package me.eggzman.builders_dashboard.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;

public final class PolaroidOverlay {

    private static boolean enabled = true;

    private PolaroidOverlay() {}

    public static void toggle() {
        enabled = !enabled;
    }

    public static boolean isEnabled() {
        return enabled;
    }

    /** Call this from your HUD render code. */
    public static void render(DrawContext context, float tickDelta) {
        if (!enabled) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        Screen screen = client.currentScreen;

        // Only render when a GUI is open (inventory / chest / crafting / etc.)
        if (screen == null) return;

        int leftMargin = 10;
        int topMargin = 20;

        int cardW = 140;
        int cardH = 120;
        int gap = 10;

        drawPolaroid(context, leftMargin, topMargin + (cardH + gap) * 0, cardW, cardH, "Screenshot 1");
        drawPolaroid(context, leftMargin, topMargin + (cardH + gap) * 1, cardW, cardH, "Screenshot 2");
        drawPolaroid(context, leftMargin, topMargin + (cardH + gap) * 2, cardW, cardH, "Palette");
    }

    private static void drawPolaroid(DrawContext ctx, int x, int y, int w, int h, String caption) {
        int frame = 6;
        int bottomExtra = 18;

        int outerW = w;
        int outerH = h + bottomExtra;

        // Outer white card
        fillRect(ctx, x, y, outerW, outerH, 0xFFF8F8F8);

        // Inner "photo" area (placeholder)
        int photoX = x + frame;
        int photoY = y + frame;
        int photoW = w - frame * 2;
        int photoH = h - frame * 2;

        fillRect(ctx, photoX, photoY, photoW, photoH, 0xFF1B1B1B);

        // 1px outline
        drawRectOutline(ctx, x, y, outerW, outerH, 0xFFB0B0B0);

        // Caption
        ctx.drawTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                caption,
                x + frame,
                y + h + 4,
                0xFF2B2B2B
        );
    }

    private static void fillRect(DrawContext ctx, int x, int y, int w, int h, int argb) {
        ctx.fill(x, y, x + w, y + h, argb);
    }

    private static void drawRectOutline(DrawContext ctx, int x, int y, int w, int h, int argb) {
        ctx.fill(x, y, x + w, y + 1, argb);                 // top
        ctx.fill(x, y + h - 1, x + w, y + h, argb);         // bottom
        ctx.fill(x, y, x + 1, y + h, argb);                 // left
        ctx.fill(x + w - 1, y, x + w, y + h, argb);         // right
    }
}
