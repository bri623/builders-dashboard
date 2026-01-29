package me.eggzman.builders_dashboard.ui;

import me.eggzman.builders_dashboard.images.ImageLibrary;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class PhotoViewerScreen extends Screen {
    private final Screen parent;
    private final ImageLibrary.ImageEntry entry;
    private float zoom = 1.0f;
    private float panX = 0.0f;
    private float panY = 0.0f;
    private boolean dragging;
    private double lastMouseX;
    private double lastMouseY;

    public PhotoViewerScreen(Screen parent, ImageLibrary.ImageEntry entry) {
        super(Text.literal("Photo Viewer"));
        this.parent = parent;
        this.entry = entry;
    }

    @Override
    protected void init() {
        super.init();
        this.zoom = 1.0f;
        this.panX = 0.0f;
        this.panY = 0.0f;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        context.fill(0, 0, this.width, this.height, 0xCC000000);

        int imgW = entry.width();
        int imgH = entry.height();
        if (imgW <= 0 || imgH <= 0) {
            super.render(context, mouseX, mouseY, delta);
            return;
        }

        float baseScale = Math.min((this.width * 0.9f) / imgW, (this.height * 0.9f) / imgH);
        float scale = baseScale * zoom;

        int drawW = Math.max(1, Math.round(imgW * scale));
        int drawH = Math.max(1, Math.round(imgH * scale));

        int centerX = this.width / 2;
        int centerY = this.height / 2;
        int drawX = Math.round(centerX - drawW / 2f + panX);
        int drawY = Math.round(centerY - drawH / 2f + panY);

        context.drawTexture(
                net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED,
                entry.textureId(),
                drawX,
                drawY,
                0f,
                0f,
                drawW,
                drawH,
                imgW,
                imgH,
                imgW,
                imgH
        );

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        float nextZoom = zoom + (float) verticalAmount * 0.1f;
        zoom = MathHelper.clamp(nextZoom, 0.25f, 4.0f);
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (click.button() == 0) {
            dragging = true;
            lastMouseX = click.x();
            lastMouseY = click.y();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging) {
            double dx = click.x() - lastMouseX;
            double dy = click.y() - lastMouseY;
            panX += (float) dx;
            panY += (float) dy;
            lastMouseX = click.x();
            lastMouseY = click.y();
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        dragging = false;
        return super.mouseReleased(click);
    }

    @Override
    public void close() {
        MinecraftClient client = this.client;
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
