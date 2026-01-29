package me.eggzman.builders_dashboard.ui;

import me.eggzman.builders_dashboard.images.ImageLibrary;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.minecraft.text.Style;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class NoteEditScreen extends Screen {
    private static final int MAX_NOTE_CHARS = 100;
    private static final int NOTE_MARGIN = 6;

    private final Screen parent;
    private final ImageLibrary.ImageEntry entry;
    private final int anchorX;
    private final int anchorY;
    private final int cardW;
    private final int cardH;
    private TextFieldWidget noteField;
    private int noteX;
    private int noteY;
    private int noteW;
    private int noteH;
    private boolean noteOnRight;

    public NoteEditScreen(Screen parent, ImageLibrary.ImageEntry entry, int anchorX, int anchorY, int cardW, int cardH) {
        super(Text.literal("Edit Note"));
        this.parent = parent;
        this.entry = entry;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.cardW = cardW;
        this.cardH = cardH;
    }

    @Override
    protected void init() {
        super.init();

        layoutNote();
        DashboardHud.setSuppressTooltips(true);

        int padding = 8;
        int headerH = 12;
        int fieldX = noteX + padding;
        int fieldY = noteY + padding + headerH;
        int fieldW = Math.max(10, noteW - padding * 2);
        int fieldH = Math.max(14, noteH - padding * 2 - headerH);

        noteField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldW, fieldH, Text.literal("Note"));
        noteField.setMaxLength(MAX_NOTE_CHARS);
        noteField.setText(entry.note());
        noteField.setDrawsBackground(false);
        noteField.setTextShadow(false);
        noteField.setEditableColor(0x00FFFFFF);
        noteField.setUneditableColor(0x00FFFFFF);
        addDrawableChild(noteField);
        setInitialFocus(noteField);
        noteField.setFocused(true);
    }

    private void layoutNote() {
        int baseW = Math.min(200, Math.max(120, Math.round(cardW * 0.9f)));
        int baseH = Math.min(120, Math.max(70, Math.round(cardH * 0.45f)));
        noteW = Math.max(40, Math.min(baseW, this.width - NOTE_MARGIN * 2));
        noteH = Math.max(40, Math.min(baseH, this.height - NOTE_MARGIN * 2));

        int gap = 6;
        int rightX = anchorX + cardW + gap;
        int leftX = anchorX - gap - noteW;

        boolean fitsRight = rightX + noteW <= this.width - NOTE_MARGIN;
        boolean fitsLeft = leftX >= NOTE_MARGIN;
        noteOnRight = fitsRight || !fitsLeft;

        if (noteOnRight) {
            noteX = Math.min(rightX, this.width - noteW - NOTE_MARGIN);
        } else {
            noteX = Math.max(NOTE_MARGIN, leftX);
        }

        int desiredY = anchorY + Math.round(cardH * 0.05f);
        noteY = Math.max(NOTE_MARGIN, Math.min(this.height - noteH - NOTE_MARGIN, desiredY));
    }

    private void saveNote() {
        if (noteField == null) {
            return;
        }
        String text = noteField.getText();
        if (text == null) {
            text = "";
        }
        text = text.trim();
        if (text.length() > MAX_NOTE_CHARS) {
            text = text.substring(0, MAX_NOTE_CHARS);
        }
        ImageLibrary.setNote(entry, text);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (parent != null) {
            parent.renderWithTooltip(context, mouseX, mouseY, delta);
        }

        MinecraftClient client = this.client;
        if (client != null) {
            DashboardHud.renderOverlay(context, client, mouseX, mouseY);
        }

        drawNote(context);

        super.render(context, mouseX, mouseY, delta);
        drawNoteText(context);
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        // Keep the inventory screen visible underneath the sticky note.
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (noteField != null && noteField.isFocused()) {
            int keyCode = input.getKeycode();
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                insertNewLine();
                return true;
            }
        }
        return super.keyPressed(input);
    }

    private void drawNote(DrawContext context) {
        int shadow = 0x44000000;
        int noteColor = 0xFFF8E59A;
        int border = 0xFFE0C156;
        int fold = Math.min(12, Math.min(noteW, noteH) / 4);

        context.fill(noteX + 2, noteY + 2, noteX + noteW + 2, noteY + noteH + 2, shadow);
        context.fill(noteX, noteY, noteX + noteW, noteY + noteH, noteColor);
        drawBorder(context, noteX, noteY, noteW, noteH, border);

        if (fold >= 6) {
            context.fill(noteX + noteW - fold, noteY, noteX + noteW, noteY + fold, 0xFFEAD37A);
            context.fill(noteX + noteW - fold, noteY, noteX + noteW - fold + 1, noteY + fold, border);
            context.fill(noteX + noteW - fold, noteY + fold - 1, noteX + noteW, noteY + fold, border);
        }

        drawConnector(context, noteColor, border);

        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal("Note"),
                noteX + 8,
                noteY + 6,
                0xFF5A4A1A
        );
    }

    private void drawNoteText(DrawContext context) {
        if (noteField == null) {
            return;
        }

        String text = noteField.getText();
        if (text == null) {
            text = "";
        }

        TextArea area = getTextArea();
        int textX = area.x;
        int textY = area.y;
        int textW = area.w;
        int textH = area.h;

        context.enableScissor(textX, textY, textX + textW, textY + textH);

        List<LineRange> lines = wrapLineRanges(text, textW);
        int lineHeight = this.textRenderer.fontHeight;
        int maxLines = Math.max(1, textH / lineHeight);
        int visibleLines = Math.min(lines.size(), maxLines);

        for (int i = 0; i < visibleLines; i++) {
            LineRange range = lines.get(i);
            if (range.end > range.start) {
                String lineText = text.substring(range.start, range.end);
                context.drawText(this.textRenderer, lineText, textX, textY + i * lineHeight, 0xFF5A4A1A, false);
            }
        }

        drawCaret(context, text, lines, textX, textY, lineHeight, visibleLines);
        context.disableScissor();
    }

    private void drawCaret(
            DrawContext context,
            String text,
            List<LineRange> lines,
            int textX,
            int textY,
            int lineHeight,
            int visibleLines
    ) {
        if (noteField == null || !noteField.isFocused()) {
            return;
        }

        long now = Util.getMeasuringTimeMs();
        if ((now / 300L) % 2L != 0L) {
            return;
        }

        int cursor = noteField.getCursor();
        if (cursor < 0) {
            cursor = 0;
        }

        int lineIndex = Math.max(0, lines.size() - 1);
        for (int i = 0; i < lines.size(); i++) {
            LineRange range = lines.get(i);
            if (cursor <= range.end) {
                lineIndex = i;
                break;
            }
        }

        if (lineIndex >= visibleLines) {
            return;
        }

        LineRange range = lines.get(lineIndex);
        int caretIndex = Math.min(Math.max(cursor, range.start), range.end);
        String before = caretIndex > range.start ? text.substring(range.start, caretIndex) : "";
        int caretX = textX + this.textRenderer.getWidth(before);
        int caretY = textY + lineIndex * lineHeight;
        context.fill(caretX, caretY - 1, caretX + 1, caretY + lineHeight, 0xFF5A4A1A);
    }

    private List<LineRange> wrapLineRanges(String text, int width) {
        List<LineRange> ranges = new ArrayList<>();
        this.textRenderer.getTextHandler().wrapLines(text, width, Style.EMPTY, false, (style, start, end) -> {
            ranges.add(new LineRange(start, end));
        });
        if (ranges.isEmpty()) {
            ranges.add(new LineRange(0, 0));
        }
        return ranges;
    }

    private TextArea getTextArea() {
        int padding = 8;
        int headerH = 12;
        int textX = noteX + padding;
        int textY = noteY + padding + headerH;
        int textW = Math.max(1, noteW - padding * 2);
        int textH = Math.max(1, noteH - padding * 2 - headerH);
        return new TextArea(textX, textY, textW, textH);
    }

    private void setCursorFromClick(double mouseX, double mouseY, TextArea area) {
        if (noteField == null) {
            return;
        }

        String text = noteField.getText();
        if (text == null) {
            text = "";
        }

        List<LineRange> lines = wrapLineRanges(text, area.w);
        int lineHeight = this.textRenderer.fontHeight;
        int lineIndex = (int) ((mouseY - area.y) / lineHeight);
        lineIndex = Math.max(0, Math.min(lineIndex, lines.size() - 1));

        LineRange range = lines.get(lineIndex);
        String lineText = text.substring(range.start, range.end);
        int localX = (int) Math.max(0, Math.min(area.w, mouseX - area.x));
        int charCount = this.textRenderer.getTextHandler().getTrimmedLength(lineText, localX, Style.EMPTY);
        int cursor = range.start + charCount;
        noteField.setCursor(cursor, false);
    }

    private void insertNewLine() {
        if (noteField == null) {
            return;
        }
        String text = noteField.getText();
        if (text == null) {
            text = "";
        }
        int cursor = noteField.getCursor();
        cursor = Math.max(0, Math.min(cursor, text.length()));
        if (text.length() >= MAX_NOTE_CHARS) {
            return;
        }
        String next = text.substring(0, cursor) + "\n" + text.substring(cursor);
        if (next.length() > MAX_NOTE_CHARS) {
            next = next.substring(0, MAX_NOTE_CHARS);
        }
        noteField.setText(next);
        int newCursor = Math.min(cursor + 1, next.length());
        noteField.setCursor(newCursor, false);
    }

    private record LineRange(int start, int end) {}

    private record TextArea(int x, int y, int w, int h) {}

    private void drawConnector(DrawContext context, int noteColor, int border) {
        int connectorY = clamp(anchorY + 12, noteY + 8, noteY + noteH - 8);
        int thickness = 3;
        if (noteOnRight) {
            int startX = anchorX + cardW;
            int endX = noteX;
            if (endX > startX) {
                context.fill(startX, connectorY, endX, connectorY + thickness, border);
                context.fill(endX - 4, connectorY - 2, endX, connectorY + thickness + 2, noteColor);
            }
        } else {
            int startX = noteX + noteW;
            int endX = anchorX;
            if (endX > startX) {
                context.fill(startX, connectorY, endX, connectorY + thickness, border);
                context.fill(startX, connectorY - 2, startX + 4, connectorY + thickness + 2, noteColor);
            }
        }
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private boolean isInsideNote(double x, double y) {
        return x >= noteX && x <= noteX + noteW && y >= noteY && y <= noteY + noteH;
    }

    private boolean toggleChecklistAtClick(double mouseX, double mouseY, TextArea area) {
        if (noteField == null) {
            return false;
        }
        String text = noteField.getText();
        if (text == null || text.isEmpty()) {
            return false;
        }
        List<LineRange> lines = wrapLineRanges(text, area.w);
        int lineHeight = this.textRenderer.fontHeight;
        int lineIndex = (int) ((mouseY - area.y) / lineHeight);
        lineIndex = Math.max(0, Math.min(lineIndex, lines.size() - 1));
        LineRange range = lines.get(lineIndex);
        if (range.end <= range.start) {
            return false;
        }

        int idx = range.start;
        while (idx < range.end && text.charAt(idx) == ' ') {
            idx++;
        }
        if (idx < range.end && text.charAt(idx) == '-') {
            idx++;
            if (idx < range.end && text.charAt(idx) == ' ') {
                idx++;
            }
        }
        if (idx + 2 >= text.length()) {
            return false;
        }
        if (text.charAt(idx) != '[' || text.charAt(idx + 2) != ']') {
            return false;
        }
        char current = text.charAt(idx + 1);
        if (current != ' ' && current != 'x' && current != 'X') {
            return false;
        }
        char next = (current == 'x' || current == 'X') ? ' ' : 'x';
        StringBuilder updated = new StringBuilder(text);
        updated.setCharAt(idx + 1, next);
        int cursor = noteField.getCursor();
        noteField.setText(updated.toString());
        noteField.setCursor(Math.min(cursor, updated.length()), false);
        return true;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (!isInsideNote(click.x(), click.y())) {
            close();
            return true;
        }

        if (noteField != null) {
            noteField.setFocused(true);
            setFocused(noteField);
        }

        TextArea area = getTextArea();
        if (click.x() >= area.x && click.x() <= area.x + area.w && click.y() >= area.y && click.y() <= area.y + area.h) {
            if (toggleChecklistAtClick(click.x(), click.y(), area)) {
                return true;
            }
            setCursorFromClick(click.x(), click.y(), area);
        }

        return true;
    }

    @Override
    public void close() {
        saveNote();
        DashboardHud.setSuppressTooltips(false);
        MinecraftClient client = this.client;
        if (client != null) {
            client.setScreen(parent);
        }
    }

    @Override
    public void removed() {
        DashboardHud.setSuppressTooltips(false);
        super.removed();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static void drawBorder(DrawContext ctx, int x, int y, int w, int h, int color) {
        ctx.fill(x, y, x + w, y + 1, color);
        ctx.fill(x, y + h - 1, x + w, y + h, color);
        ctx.fill(x, y, x + 1, y + h, color);
        ctx.fill(x + w - 1, y, x + w, y + h, color);
    }
}
