package me.eggzman.builders_dashboard.ui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;

public class DashboardScreen extends DashboardBaseScreen {
    private static final Text[] DESCRIPTION_LINES = new Text[] {};

    public DashboardScreen() {
        super(Text.literal("Builder's Dashboard"));
    }

    @Override
    protected void init() {
        super.init();
        clearTabs();
        addTab("Home", DashboardScreen::new, true);
        addTab("Photos", PhotoSettingsScreen::new, false);
        addTab("Block Palettes", BlockPalettesScreen::new, false);
        addTab("Groups", PinGroupsScreen::new, false);
        addTab("Keybinds", KeybindsScreen::new, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Center panel sizing
        int panelW = Math.min(360, this.width - 40);
        int panelH = Math.min(220, this.height - 40);
        int x1 = (this.width - panelW) / 2;
        int y1 = (this.height - panelH) / 2;
        int x2 = x1 + panelW;
        int y2 = y1 + panelH;

        setTabAnchor(x1, y1, panelH);

        // Panel background + border
        context.fill(x1, y1, x2, y2, 0xFF1B1B1B);
        // Border (manually drawn because drawBorder might be missing/changed)
        context.fill(x1, y1, x2, y1 + 1, 0xFF4A4A4A); // Top
        context.fill(x1, y2 - 1, x2, y2, 0xFF4A4A4A); // Bottom
        context.fill(x1, y1, x1 + 1, y2, 0xFF4A4A4A); // Left
        context.fill(x2 - 1, y1, x2, y2, 0xFF4A4A4A); // Right

        // Title
        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Builder's Dashboard"),
                this.width / 2,
                y1 + 12,
                0xFFFFFFFF
        );

        super.render(context, mouseX, mouseY, delta);
        renderTabs(context, mouseX, mouseY);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Overriding this to just dim the screen without applying blur
        // This prevents the "Can only blur once per frame" crash in 1.21.2+
        context.fill(0, 0, this.width, this.height, 0xAA000000);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
