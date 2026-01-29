package me.eggzman.builders_dashboard.ui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public abstract class DashboardBaseScreen extends Screen {
    protected static final int TAB_X = 8;
    protected static final int TAB_TOP = 24;
    protected static final int TAB_H = 28;
    protected static final int TAB_GAP = 6;
    protected static final int TAB_COLLAPSED_W = 28;
    protected static final int TAB_EXPANDED_W = 120;

    private final List<Tab> tabs = new ArrayList<>();
    private Integer anchorX;
    private Integer anchorY;
    private Integer anchorMaxY;

    protected DashboardBaseScreen(Text title) {
        super(title);
    }

    protected void clearTabs() {
        tabs.clear();
    }

    protected void addTab(String label, Supplier<Screen> target, boolean selected) {
        tabs.add(new Tab(label, target, selected));
    }

    protected void setTabAnchor(int panelX, int panelY, int panelH) {
        this.anchorX = panelX;
        this.anchorY = panelY + 16;
        this.anchorMaxY = panelY + panelH - 16;
    }

    protected void renderTabs(DrawContext context, int mouseX, int mouseY) {
        int startY = anchorY != null ? anchorY : TAB_TOP;
        int endY = anchorMaxY != null ? anchorMaxY : this.height - 12;
        int anchor = anchorX != null ? anchorX : TAB_X + TAB_COLLAPSED_W;
        int y = startY;
        for (Tab tab : tabs) {
            boolean hover = mouseX >= tab.drawX && mouseX <= tab.drawX + tab.drawW
                    && mouseY >= y && mouseY <= y + TAB_H;
            float targetW = (hover || tab.selected) ? TAB_EXPANDED_W : TAB_COLLAPSED_W;
            tab.width += (targetW - tab.width) * 0.35f;

            int drawW = Math.round(tab.width);
            int bg = tab.selected ? 0xFF2B2B2B : (hover ? 0xFF242424 : 0xFF1E1E1E);
            int drawX = anchor - drawW;
            context.fill(drawX, y, anchor, y + TAB_H, bg);
            context.fill(drawX, y + TAB_H - 1, anchor, y + TAB_H, 0xFF3A3A3A);

            if (drawW > TAB_COLLAPSED_W + 6) {
                context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal(tab.label),
                        drawX + 8,
                        y + 9,
                        0xFFE0E0E0
                );
            }

            tab.y = y;
            tab.drawW = drawW;
            tab.drawX = drawX;
            y += TAB_H + TAB_GAP;
            if (y > endY) {
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double x = click.x();
        double y = click.y();
        for (Tab tab : tabs) {
            if (x >= tab.drawX && x <= tab.drawX + tab.drawW && y >= tab.y && y <= tab.y + TAB_H) {
                if (!tab.selected && this.client != null) {
                    this.client.setScreen(tab.target.get());
                }
                return true;
            }
        }

        return super.mouseClicked(click, doubled);
    }

    private static final class Tab {
        private final String label;
        private final Supplier<Screen> target;
        private final boolean selected;
        private float width = TAB_COLLAPSED_W;
        private int y;
        private int drawW = TAB_COLLAPSED_W;
        private int drawX = TAB_X;

        private Tab(String label, Supplier<Screen> target, boolean selected) {
            this.label = label;
            this.target = target;
            this.selected = selected;
        }
    }
}
