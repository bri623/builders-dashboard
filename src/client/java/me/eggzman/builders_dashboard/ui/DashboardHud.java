package me.eggzman.builders_dashboard.ui;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStream;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public final class DashboardHud {

    private static boolean enabled = true;

    // Files must exist under:
    // src/main/resources/assets/builders_dashboard/textures/polaroids/
    private static final Identifier SHOT1 =
            Identifier.of("builders_dashboard", "textures/polaroids/shot1.png");
    private static final Identifier SHOT2 =
            Identifier.of("builders_dashboard", "textures/polaroids/shot2.png");
    private static final Identifier PALETTE =
            Identifier.of("builders_dashboard", "textures/polaroids/palette.png");

    // Cache PNG sizes so we don’t re-read them every frame
    private static final Map<Identifier, Size> SIZE_CACHE = new HashMap<>();

    private DashboardHud() {}

    public static void init() {
        HudRenderCallback.EVENT.register((context, tickDelta) -> {
            if (!enabled) return;

            MinecraftClient client = MinecraftClient.getInstance();
            if (client == null || client.player == null) return;

            // only show while a handled gui (inventory/chest/etc) is open
            if (!(client.currentScreen instanceof HandledScreen<?>)) return;

            render(context);
        });
    }

    public static void toggle() {
        enabled = !enabled;
    }

    private static void render(DrawContext ctx) {
        int x = 8;
        int y = 20;
        int cardW = 110;
        int cardH = 140;
        int gap = 10;

        drawPolaroid(ctx, x, y, cardW, cardH, SHOT1, "Screenshot 1");
        y += cardH + gap;

        drawPolaroid(ctx, x, y, cardW, cardH, SHOT2, "Screenshot 2");
        y += cardH + gap;

        drawPolaroid(ctx, x, y, cardW, cardH, PALETTE, "Palette");
    }

    private static void drawPolaroid(
            DrawContext ctx,
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

        // draw the photo area (contain/fit so no zoom-crop)
        drawTextureContain(ctx, MinecraftClient.getInstance(), texture, imgX, imgY, imgW, imgH);

        // caption (no shadow -> easier to read on “paper”)
        ctx.drawText(
                MinecraftClient.getInstance().textRenderer,
                Text.literal(caption),
                x + 8,
                y + h - 16,
                0xFF222222,
                false
        );
    }

    /**
     * Draws an image into a box WITHOUT cropping (contain/fit).
     * Uses the real PNG size so random images won’t look zoomed.
     */
    private static void drawTextureContain(
            DrawContext ctx,
            MinecraftClient client,
            Identifier texture,
            int x, int y,
            int boxW, int boxH
    ) {
        Size s = getSize(client, texture);
        int texW = s.w;
        int texH = s.h;

        // fallback if we can’t read size for some reason
        if (texW <= 0 || texH <= 0) {
            texW = 256;
            texH = 256;
        }

        // compute scaled size (contain)
        float scale = Math.min((float) boxW / (float) texW, (float) boxH / (float) texH);
        int drawW = Math.max(1, Math.round(texW * scale));
        int drawH = Math.max(1, Math.round(texH * scale));

        int drawX = x + (boxW - drawW) / 2;
        int drawY = y + (boxH - drawH) / 2;

        // background behind image (letterboxing looks clean)
        ctx.fill(x, y, x + boxW, y + boxH, 0xFF2B2B2B);

        // IMPORTANT: Inventory screens can leave the shader tinted darker.
        // Force our draw to full brightness.
        forceShaderColorWhite(ctx);

        // draw full texture scaled into the box
        ctx.drawTexture(
                RenderPipelines.GUI_TEXTURED,
                texture,
                drawX, drawY,
                0f, 0f,
                drawW, drawH,
                texW, texH
        );

        // reset again so we don’t accidentally tint anything else
        forceShaderColorWhite(ctx);

        // subtle inner border around the image area
        drawBorder(ctx, x, y, boxW, boxH, 0xFF2A2A2A);
    }

    /**
     * Try to force shader/tint color to white.
     * Uses reflection so it compiles even if Mojang moves/renames methods between versions.
     */
    private static void forceShaderColorWhite(DrawContext ctx) {
        // 1) Some versions have DrawContext#setShaderColor(r,g,b,a)
        try {
            Method m = ctx.getClass().getMethod("setShaderColor", float.class, float.class, float.class, float.class);
            m.invoke(ctx, 1f, 1f, 1f, 1f);
            return;
        } catch (Throwable ignored) {}

        // 2) Older/common place: com.mojang.blaze3d.systems.RenderSystem#setShaderColor
        try {
            Class<?> rs = Class.forName("com.mojang.blaze3d.systems.RenderSystem");
            Method m = rs.getMethod("setShaderColor", float.class, float.class, float.class, float.class);
            m.invoke(null, 1f, 1f, 1f, 1f);
        } catch (Throwable ignored) {}
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

    private record Size(int w, int h) {}
}
