package me.eggzman.builders_dashboard.ui;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import me.eggzman.builders_dashboard.images.ImageLibrary;
import me.eggzman.builders_dashboard.input.Keybinds;
import me.eggzman.builders_dashboard.palettes.PaletteLibrary;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import org.lwjgl.glfw.GLFW;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class BlockPalettesScreen extends DashboardBaseScreen {
    private static final String API_BASE = "https://www.blockpalettes.com/api/palettes/all_palettes.php";
    private static final String BLOCK_IMAGE_BASE = "https://storage.googleapis.com/bp-blocks/blocks/";
    private static final String USER_AGENT = "BuildersDashboard/1.0";
    private static final Gson GSON = new Gson();

    private static final int LIMIT = 12;
    private static final int CELL_SIZE = 24;
    private static final int GRID_COLS = 3;
    private static final int GRID_ROWS = 2;
    private static final int GRID_W = CELL_SIZE * GRID_COLS;
    private static final int GRID_H = CELL_SIZE * GRID_ROWS;
    private static final int GRID_PAD = 6;
    private static final int ROW_H = GRID_H + GRID_PAD * 2;
    private static final int RESULT_ROW_H = 16;
    private static final int MAX_RESULTS = 5;
    private static final int ACTION_BTN_W = 54;
    private static final int STAR_BTN_W = 40;
    private static final int ACTION_BTN_GAP = 6;
    private static final String[] COLOR_OPTIONS = new String[] {
            "all",
            "red",
            "orange",
            "yellow",
            "green",
            "blue",
            "purple",
            "black",
            "white"
    };

    private final List<PaletteEntry> palettes = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private final List<ButtonWidget> listButtons = new ArrayList<>();
    private final Map<String, BlockTexture> blockTextures = new HashMap<>();
    private final Set<String> loadingBlocks = new HashSet<>();
    private final Set<String> failedBlocks = new HashSet<>();
    private final Set<Integer> adding = new HashSet<>();
    private final Set<Integer> savedPaletteIds = new HashSet<>();

    private ButtonWidget recentButton;
    private ButtonWidget popularButton;
    private ButtonWidget refreshButton;
    private ButtonWidget prevButton;
    private ButtonWidget nextButton;
    private ButtonWidget colorButton;
    private ButtonWidget viewButton;
    private ButtonWidget groupButton;
    private ButtonWidget searchButton;
    private ButtonWidget clearBlockButton;
    private TextFieldWidget blockField;

    private String sort = "recent";
    private String blockFilter = "";
    private String colorFilter = "all";
    private String selectedGroupName = "";
    private String statusText = "";
    private boolean searching;
    private boolean loading;
    private int page = 1;
    private int totalPages = 1;
    private ViewMode viewMode = ViewMode.ALL;
    private int listStartY;
    private int listEndY;
    private int scrollOffset;
    private int maxScroll;
    private int lastPaletteCount = -1;
    private int lastImageCount = -1;
    private int lastGroupCount = -1;
    private int textureGeneration;
    private int lastLayoutStartY = -1;
    private int lastLayoutEndY = -1;

    private final List<String> searchResults = new ArrayList<>();
    private final List<SearchRow> searchRows = new ArrayList<>();
    private final List<String> groupOptions = new ArrayList<>();
    private int groupIndex;

    public BlockPalettesScreen() {
        super(Text.literal("Block Palettes"));
    }

    @Override
    protected void init() {
        super.init();
        clearTabs();
        addTab("Home", DashboardScreen::new, false);
        addTab("Photos", PhotoSettingsScreen::new, false);
        addTab("Block Palettes", BlockPalettesScreen::new, true);
        addTab("Groups", PinGroupsScreen::new, false);
        addTab("Keybinds", KeybindsScreen::new, false);

        int panelW = Math.min(520, this.width - 40);
        int panelH = Math.min(320, this.height - 40);
        int x1 = (this.width - panelW) / 2;
        int y1 = (this.height - panelH) / 2;

        int btnH = 20;
        int btnW = 70;
        int gap = 6;
        int topY = y1 + 38;
        int leftX = x1 + 20;

        recentButton = ButtonWidget.builder(Text.literal("Recent"), btn -> setSort("recent"))
                .dimensions(leftX, topY, btnW, btnH).build();
        popularButton = ButtonWidget.builder(Text.literal("Popular"), btn -> setSort("popular"))
                .dimensions(leftX + btnW + gap, topY, btnW, btnH).build();
        refreshButton = ButtonWidget.builder(Text.literal("Refresh"), btn -> fetchPage(page))
                .dimensions(leftX + (btnW + gap) * 2, topY, 90, btnH).build();
        addDrawableChild(recentButton);
        addDrawableChild(popularButton);
        addDrawableChild(refreshButton);

        int rightX = x1 + panelW - 20;
        int groupW = 120;
        int colorW = 90;
        int viewW = 90;
        groupButton = ButtonWidget.builder(Text.literal("Group: None"), btn -> cycleGroup())
                .dimensions(rightX - groupW, topY, groupW, btnH).build();
        colorButton = ButtonWidget.builder(Text.literal("Color: All"), btn -> cycleColor())
                .dimensions(rightX - groupW - gap - colorW, topY, colorW, btnH).build();
        viewButton = ButtonWidget.builder(Text.literal(viewLabel()), btn -> cycleViewMode())
                .dimensions(rightX - groupW - gap - colorW - gap - viewW, topY, viewW, btnH).build();
        addDrawableChild(viewButton);
        addDrawableChild(colorButton);
        addDrawableChild(groupButton);

        int fieldY = topY + btnH + 8;
        int fieldX = x1 + 20;
        int searchW = 60;
        int clearW = 60;
        int fieldW = Math.max(80, panelW - 40 - searchW - clearW - gap * 2);

        blockField = new TextFieldWidget(this.textRenderer, fieldX, fieldY, fieldW, btnH, Text.literal("Block search"));
        blockField.setMaxLength(32);
        blockField.setPlaceholder(Text.literal("Search blocks (e.g. oak)"));
        addDrawableChild(blockField);

        searchButton = ButtonWidget.builder(Text.literal("Find"), btn -> searchBlocks())
                .dimensions(fieldX + fieldW + gap, fieldY, searchW, btnH).build();
        clearBlockButton = ButtonWidget.builder(Text.literal("Clear"), btn -> clearBlockFilter())
                .dimensions(fieldX + fieldW + gap + searchW + gap, fieldY, clearW, btnH).build();
        addDrawableChild(searchButton);
        addDrawableChild(clearBlockButton);

        int bottomY = y1 + panelH - 28;
        int navW = 70;
        prevButton = ButtonWidget.builder(Text.literal("< Prev"), btn -> fetchPage(page - 1))
                .dimensions(x1 + 20, bottomY, navW, btnH).build();
        nextButton = ButtonWidget.builder(Text.literal("Next >"), btn -> fetchPage(page + 1))
                .dimensions(x1 + panelW - 20 - navW, bottomY, navW, btnH).build();
        addDrawableChild(prevButton);
        addDrawableChild(nextButton);

        listStartY = fieldY + btnH + 6;
        listEndY = bottomY - 6;

        updateGroupOptions();
        updateColorButton();
        refreshSavedPaletteIds();
        rebuildList();
        updateNavButtons();

        if (palettes.isEmpty()) {
            fetchPage(1);
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        int paletteCount = palettes.size();
        if (paletteCount != lastPaletteCount) {
            rebuildList();
        }
        if (ImageLibrary.getImages().size() != lastImageCount) {
            refreshSavedPaletteIds();
            rebuildList();
        }
        int groupCount = ImageLibrary.getGroupNames().size();
        if (groupCount != lastGroupCount) {
            updateGroupOptions();
            rebuildList();
        }

        int panelW = Math.min(520, this.width - 40);
        int panelH = Math.min(320, this.height - 40);
        int x1 = (this.width - panelW) / 2;
        int y1 = (this.height - panelH) / 2;
        int x2 = x1 + panelW;
        int y2 = y1 + panelH;
        int bottomY = y1 + panelH - 28;

        setTabAnchor(x1, y1, panelH);

        updateListBounds(bottomY);
        updateControlStates();

        context.fill(x1, y1, x2, y2, 0xFF1B1B1B);
        context.fill(x1, y1, x2, y1 + 1, 0xFF4A4A4A);
        context.fill(x1, y2 - 1, x2, y2, 0xFF4A4A4A);
        context.fill(x1, y1, x1 + 1, y2, 0xFF4A4A4A);
        context.fill(x2 - 1, y1, x2, y2, 0xFF4A4A4A);

        context.drawCenteredTextWithShadow(
                this.textRenderer,
                Text.literal("Block Palettes"),
                this.width / 2,
                y1 + 12,
                0xFFFFFFFF
        );

        String pageLabel = "Page " + page + " / " + totalPages;
        int pageX = x2 - 20 - this.textRenderer.getWidth(pageLabel);
        context.drawTextWithShadow(
                this.textRenderer,
                Text.literal(pageLabel),
                pageX,
                y1 + 12,
                0xFFB0B0B0
        );

        drawSearchResults(context, mouseX, mouseY);

        int listX1 = x1 + 16;
        int listX2 = x2 - 16;
        context.enableScissor(listX1, listStartY, listX2, listEndY);

        if (rows.isEmpty() && !loading) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal("No palettes loaded"),
                    this.width / 2,
                    listStartY + 20,
                    0xFFB0B0B0
            );
        }

        if (!rows.isEmpty()) {
            int actionsWidth = STAR_BTN_W + ACTION_BTN_W * 2 + ACTION_BTN_GAP * 2;
            for (Row row : rows) {
                drawPaletteGrid(context, row.palette, row.gridX, row.gridY);
                int textMax = Math.max(40, listX2 - actionsWidth - 6 - row.textX);
                String label = this.textRenderer.trimToWidth("Palette #" + row.palette.id, textMax);
                context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal(label),
                        row.textX,
                        row.y + 10,
                        0xFFE0E0E0
                );
                String meta = row.palette.time_ago != null && !row.palette.time_ago.isBlank()
                        ? row.palette.time_ago
                        : "Likes: " + row.palette.likes;
                meta = this.textRenderer.trimToWidth(meta, textMax);
                context.drawTextWithShadow(
                        this.textRenderer,
                        Text.literal(meta),
                        row.textX,
                        row.y + 24,
                        0xFF9A9A9A
                );
            }
        }

        context.disableScissor();

        drawPaletteTooltip(context, mouseX, mouseY);

        if (!statusText.isEmpty()) {
            context.drawCenteredTextWithShadow(
                    this.textRenderer,
                    Text.literal(statusText),
                    this.width / 2,
                    y2 - 18,
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
    public boolean shouldPause() {
        return false;
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
            rebuildList();
            return true;
        }

        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (handleSearchClick(click)) {
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean keyPressed(KeyInput input) {
        if (blockField != null && blockField.isFocused()) {
            int keyCode = input.getKeycode();
            if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
                searchBlocks();
                return true;
            }
        }
        return super.keyPressed(input);
    }

    @Override
    public void removed() {
        clearBlockTextures();
        super.removed();
    }

    private void updateListBounds(int bottomY) {
        int fieldY = blockField != null ? blockField.getY() : bottomY;
        int fieldH = blockField != null ? blockField.getHeight() : 0;
        int resultCount = Math.min(MAX_RESULTS, searchResults.size());
        int nextStart = fieldY + fieldH + 6;
        if (resultCount > 0) {
            nextStart += resultCount * RESULT_ROW_H + 4;
        }
        int nextEnd = bottomY - 6;
        if (nextStart != listStartY || nextEnd != listEndY) {
            listStartY = nextStart;
            listEndY = nextEnd;
            rebuildList();
        }
        lastLayoutStartY = listStartY;
        lastLayoutEndY = listEndY;
    }

    private void updateControlStates() {
        boolean allowFilters = viewMode == ViewMode.ALL;
        if (searchButton != null) {
            searchButton.active = allowFilters && !searching;
        }
        if (clearBlockButton != null) {
            clearBlockButton.active = allowFilters && !blockFilter.isBlank();
        }
        if (colorButton != null) {
            colorButton.active = allowFilters && !loading;
        }
        if (blockField != null) {
            blockField.setEditable(allowFilters);
        }
        if (viewButton != null) {
            viewButton.setMessage(Text.literal(viewLabel()));
        }
        updateNavButtons();
    }

    private void drawSearchResults(DrawContext context, int mouseX, int mouseY) {
        searchRows.clear();
        if (searchResults.isEmpty() || blockField == null) {
            return;
        }
        int resultCount = Math.min(MAX_RESULTS, searchResults.size());
        int startX = blockField.getX();
        int startY = blockField.getY() + blockField.getHeight() + 4;
        int width = blockField.getWidth();

        for (int i = 0; i < resultCount; i++) {
            String block = searchResults.get(i);
            int y = startY + i * RESULT_ROW_H;
            boolean hover = mouseX >= startX && mouseX <= startX + width
                    && mouseY >= y && mouseY <= y + RESULT_ROW_H;
            int bg = hover ? 0xFF2C2C2C : 0xFF232323;
            context.fill(startX, y, startX + width, y + RESULT_ROW_H, bg);
            String label = this.textRenderer.trimToWidth(block, Math.max(1, width - 12));
            context.drawTextWithShadow(
                    this.textRenderer,
                    Text.literal(label),
                    startX + 6,
                    y + 4,
                    0xFFE0E0E0
            );
            searchRows.add(new SearchRow(block, startX, y, width, RESULT_ROW_H));
        }
    }

    private void drawPaletteTooltip(DrawContext context, int mouseX, int mouseY) {
        if (!Keybinds.areTooltipsEnabled()) {
            return;
        }
        if (rows.isEmpty() || this.client == null) {
            return;
        }
        int panelW = Math.min(520, this.width - 40);
        int x1 = (this.width - panelW) / 2;
        int listX1 = x1 + 20;
        int listX2 = x1 + panelW - 20;
        if (mouseX < listX1 || mouseX > listX2 || mouseY < listStartY || mouseY > listEndY) {
            return;
        }

        Row hovered = null;
        for (Row row : rows) {
            if (mouseY >= row.y && mouseY <= row.y + ROW_H) {
                hovered = row;
                break;
            }
        }
        if (hovered == null) {
            return;
        }

        List<OrderedText> tooltip = new ArrayList<>();
        tooltip.add(Text.literal("Blocks:").asOrderedText());
        for (String block : hovered.palette.blocks()) {
            String name = formatBlockName(block);
            if (!name.isBlank()) {
                tooltip.add(Text.literal("- " + name).asOrderedText());
            }
        }

        String note = findPaletteNote(hovered.palette.id);
        if (!note.isBlank()) {
            tooltip.add(Text.literal("Note:").asOrderedText());
            tooltip.addAll(this.textRenderer.wrapLines(Text.literal(note), 180));
        }

        if (!tooltip.isEmpty()) {
            context.drawOrderedTooltip(this.textRenderer, tooltip, mouseX, mouseY);
        }
    }

    private boolean handleSearchClick(Click click) {
        if (searchRows.isEmpty()) {
            return false;
        }
        double x = click.x();
        double y = click.y();
        for (SearchRow row : searchRows) {
            if (row.contains(x, y)) {
                applyBlockFilter(row.block);
                return true;
            }
        }
        return false;
    }

    private void searchBlocks() {
        if (searching || blockField == null) {
            return;
        }
        viewMode = ViewMode.ALL;
        String query = blockField.getText().trim().toLowerCase(Locale.ROOT);
        if (query.length() < 2) {
            statusText = "Type at least 2 characters";
            return;
        }

        searching = true;
        statusText = "Searching blocks...";
        updateControlStates();

        MinecraftClient client = this.client;
        Thread searchThread = new Thread(() -> {
            SearchResult result = requestBlockSearch(query);
            if (client != null) {
                client.execute(() -> applySearchResult(result));
            }
        }, "BuildersDashboard-BlockSearch");
        searchThread.setDaemon(true);
        searchThread.start();
    }

    private SearchResult requestBlockSearch(String query) {
        String url = "https://www.blockpalettes.com/api/palettes/search-block.php?query=" + encode(query);
        try {
            String json = httpGet(url);
            SearchResponse response = GSON.fromJson(json, SearchResponse.class);
            if (response == null || !response.success || response.blocks == null) {
                return SearchResult.failure("No results");
            }
            return SearchResult.success(response.blocks);
        } catch (IOException | JsonSyntaxException e) {
            return SearchResult.failure("Search failed");
        }
    }

    private void applySearchResult(SearchResult result) {
        searching = false;
        searchResults.clear();
        if (result == null || !result.success || result.blocks.isEmpty()) {
            statusText = "No blocks found";
        } else {
            int count = Math.min(MAX_RESULTS, result.blocks.size());
            for (int i = 0; i < count; i++) {
                searchResults.add(result.blocks.get(i));
            }
            statusText = "Select a block";
        }
        updateControlStates();
    }

    private void applyBlockFilter(String block) {
        if (block == null || block.isBlank()) {
            return;
        }
        viewMode = ViewMode.ALL;
        blockFilter = block;
        colorFilter = "all";
        if (blockField != null) {
            blockField.setText(block);
        }
        searchResults.clear();
        statusText = "Filter: " + block;
        updateColorButton();
        updateControlStates();
        fetchPage(1);
    }

    private void clearBlockFilter() {
        if (blockFilter.isBlank() && searchResults.isEmpty()) {
            return;
        }
        blockFilter = "";
        searchResults.clear();
        if (blockField != null) {
            blockField.setText("");
        }
        statusText = "Block filter cleared";
        updateControlStates();
        fetchPage(1);
    }

    private void cycleColor() {
        int idx = 0;
        for (int i = 0; i < COLOR_OPTIONS.length; i++) {
            if (COLOR_OPTIONS[i].equalsIgnoreCase(colorFilter)) {
                idx = i;
                break;
            }
        }
        int next = (idx + 1) % COLOR_OPTIONS.length;
        setColorFilter(COLOR_OPTIONS[next]);
    }

    private void cycleViewMode() {
        if (viewMode == ViewMode.ALL) {
            setViewMode(ViewMode.FAVORITES);
        } else if (viewMode == ViewMode.FAVORITES) {
            setViewMode(ViewMode.RECENT);
        } else {
            setViewMode(ViewMode.ALL);
        }
    }

    private void setViewMode(ViewMode mode) {
        viewMode = mode;
        searchResults.clear();
        if (viewMode == ViewMode.ALL) {
            statusText = "Browsing palettes";
            fetchPage(1);
        } else if (viewMode == ViewMode.FAVORITES) {
            statusText = "Showing favorites";
            loadFromLibrary(PaletteLibrary.getFavorites());
        } else {
            statusText = "Showing recent";
            loadFromLibrary(PaletteLibrary.getRecents());
        }
        updateControlStates();
    }

    private void loadFromLibrary(List<PaletteLibrary.PaletteSnapshot> snapshots) {
        palettes.clear();
        for (PaletteLibrary.PaletteSnapshot snapshot : snapshots) {
            PaletteEntry entry = paletteFromSnapshot(snapshot);
            if (entry != null) {
                palettes.add(entry);
            }
        }
        page = 1;
        totalPages = 1;
        scrollOffset = 0;
        rebuildList();
    }

    private void setColorFilter(String next) {
        if (next == null || next.isBlank()) {
            next = "all";
        }
        viewMode = ViewMode.ALL;
        String normalized = next.toLowerCase(Locale.ROOT);
        colorFilter = normalized;
        if (!blockFilter.isBlank()) {
            blockFilter = "";
            searchResults.clear();
            if (blockField != null) {
                blockField.setText("");
            }
        }
        statusText = "Color: " + formatColorLabel(colorFilter);
        updateColorButton();
        updateControlStates();
        fetchPage(1);
    }

    private void updateColorButton() {
        if (colorButton == null) {
            return;
        }
        colorButton.setMessage(Text.literal("Color: " + formatColorLabel(colorFilter)));
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

    private static String formatColorLabel(String color) {
        if (color == null || color.isBlank() || "all".equalsIgnoreCase(color)) {
            return "All";
        }
        String lower = color.toLowerCase(Locale.ROOT);
        return lower.substring(0, 1).toUpperCase(Locale.ROOT) + lower.substring(1);
    }

    private static PaletteLibrary.PaletteSnapshot snapshotFromPalette(PaletteEntry entry) {
        if (entry == null) {
            return null;
        }
        PaletteLibrary.PaletteSnapshot snap = new PaletteLibrary.PaletteSnapshot();
        snap.id = entry.id;
        snap.likes = entry.likes;
        snap.time_ago = entry.time_ago;
        snap.blockOne = entry.blockOne;
        snap.blockTwo = entry.blockTwo;
        snap.blockThree = entry.blockThree;
        snap.blockFour = entry.blockFour;
        snap.blockFive = entry.blockFive;
        snap.blockSix = entry.blockSix;
        return snap;
    }

    private static PaletteEntry paletteFromSnapshot(PaletteLibrary.PaletteSnapshot snap) {
        if (snap == null) {
            return null;
        }
        PaletteEntry entry = new PaletteEntry();
        entry.id = snap.id;
        entry.likes = snap.likes;
        entry.time_ago = snap.time_ago;
        entry.blockOne = snap.blockOne;
        entry.blockTwo = snap.blockTwo;
        entry.blockThree = snap.blockThree;
        entry.blockFour = snap.blockFour;
        entry.blockFive = snap.blockFive;
        entry.blockSix = snap.blockSix;
        return entry;
    }

    private void updateGroupOptions() {
        List<String> groups = ImageLibrary.getGroupNames();
        lastGroupCount = groups.size();
        groupOptions.clear();
        groupOptions.add("None");
        groupOptions.addAll(groups);
        if (!selectedGroupName.isBlank()) {
            int idx = -1;
            for (int i = 1; i < groupOptions.size(); i++) {
                if (groupOptions.get(i).equalsIgnoreCase(selectedGroupName)) {
                    idx = i;
                    break;
                }
            }
            groupIndex = idx >= 0 ? idx : 0;
        } else if (groupIndex >= groupOptions.size()) {
            groupIndex = 0;
        }
        selectedGroupName = groupIndex == 0 ? "" : groupOptions.get(groupIndex);
        updateGroupButton();
    }

    private void cycleGroup() {
        if (groupOptions.isEmpty()) {
            updateGroupOptions();
            return;
        }
        groupIndex = (groupIndex + 1) % groupOptions.size();
        selectedGroupName = groupIndex == 0 ? "" : groupOptions.get(groupIndex);
        updateGroupButton();
    }

    private void updateGroupButton() {
        if (groupButton == null) {
            return;
        }
        String label = selectedGroupName.isBlank() ? "None" : selectedGroupName;
        groupButton.setMessage(Text.literal("Group: " + label));
    }

    private void openPalette(PaletteEntry palette) {
        if (palette == null) {
            return;
        }
        try {
            URI uri = URI.create("https://www.blockpalettes.com/palette/" + palette.id);
            Util.getOperatingSystem().open(uri);
            statusText = "Opened palette #" + palette.id;
            PaletteLibrary.markUsed(snapshotFromPalette(palette));
            if (viewMode == ViewMode.RECENT) {
                loadFromLibrary(PaletteLibrary.getRecents());
            }
        } catch (Exception e) {
            statusText = "Failed to open browser";
        }
    }

    private void setSort(String next) {
        if (loading || next == null || next.equals(sort)) {
            return;
        }
        viewMode = ViewMode.ALL;
        sort = next;
        fetchPage(1);
        updateControlStates();
    }

    private void fetchPage(int targetPage) {
        if (loading) {
            return;
        }
        if (viewMode != ViewMode.ALL) {
            return;
        }
        if (targetPage < 1) {
            return;
        }
        if (targetPage > totalPages && totalPages > 0) {
            return;
        }

        loading = true;
        statusText = "Loading palettes...";
        updateControlStates();

        MinecraftClient client = this.client;
        int requestPage = targetPage;
        String requestSort = sort;
        Thread fetchThread = new Thread(() -> {
            FetchResult result = requestPalettes(requestSort, requestPage);
            if (client != null) {
                client.execute(() -> applyFetchResult(result, requestPage));
            }
        }, "BuildersDashboard-BlockPalettes");
        fetchThread.setDaemon(true);
        fetchThread.start();
    }

    private void applyFetchResult(FetchResult result, int requestPage) {
        loading = false;
        if (result == null || !result.success) {
            statusText = result != null && result.error != null ? result.error : "Failed to load palettes";
            updateControlStates();
            return;
        }

        page = requestPage;
        totalPages = Math.max(1, result.totalPages);
        palettes.clear();
        palettes.addAll(result.palettes);
        scrollOffset = 0;
        clearBlockTextures();
        statusText = "";
        rebuildList();
        updateControlStates();
    }

    private FetchResult requestPalettes(String sortValue, int targetPage) {
        StringBuilder url = new StringBuilder();
        url.append(API_BASE)
                .append("?sort=").append(sanitizeSort(sortValue))
                .append("&page=").append(targetPage)
                .append("&limit=").append(LIMIT);
        if (!blockFilter.isBlank()) {
            url.append("&blocks=").append(encode(blockFilter));
        } else if (colorFilter != null && !"all".equalsIgnoreCase(colorFilter)) {
            url.append("&color=").append(encode(colorFilter));
        }

        try {
            String json = httpGet(url.toString());
            PaletteResponse response = GSON.fromJson(json, PaletteResponse.class);
            if (response == null || !response.success || response.palettes == null) {
                String message = response != null && response.message != null ? response.message : "Invalid response";
                return FetchResult.failure(message);
            }
            return FetchResult.success(response.palettes, response.total_pages);
        } catch (IOException | JsonSyntaxException e) {
            return FetchResult.failure("Network error: " + e.getClass().getSimpleName());
        }
    }

    private void rebuildList() {
        for (ButtonWidget btn : listButtons) {
            remove(btn);
        }
        listButtons.clear();
        rows.clear();

        lastPaletteCount = palettes.size();

        int listHeight = Math.max(0, listEndY - listStartY);
        maxScroll = Math.max(0, palettes.size() * ROW_H - listHeight);
        scrollOffset = Math.max(0, Math.min(maxScroll, scrollOffset));
        int y = listStartY - scrollOffset;

        int panelW = Math.min(520, this.width - 40);
        int x1 = (this.width - panelW) / 2;
        int listX1 = x1 + 20;
        int listX2 = x1 + panelW - 20;

        int buttonW = ACTION_BTN_W;
        int addX = listX2 - buttonW;
        int openX = addX - ACTION_BTN_GAP - buttonW;
        int starX = openX - ACTION_BTN_GAP - STAR_BTN_W;

        refreshSavedPaletteIds();

        for (PaletteEntry palette : palettes) {
            if (y + ROW_H < listStartY) {
                y += ROW_H;
                continue;
            }
            if (y + ROW_H > listEndY) {
                break;
            }

            int gridX = listX1 + GRID_PAD;
            int gridY = y + GRID_PAD;
            int textX = gridX + GRID_W + 10;

            boolean isAdding = adding.contains(palette.id);
            boolean isSaved = savedPaletteIds.contains(palette.id);
            boolean isFavorite = PaletteLibrary.isFavorite(palette.id);

            String label = isAdding ? "Adding..." : (isSaved ? "Added" : "Add");
            ButtonWidget starBtn = ButtonWidget.builder(Text.literal(isFavorite ? "Unstar" : "Star"), btn -> {
                PaletteLibrary.toggleFavorite(snapshotFromPalette(palette));
                if (viewMode == ViewMode.FAVORITES && isFavorite) {
                    loadFromLibrary(PaletteLibrary.getFavorites());
                } else {
                    rebuildList();
                }
            }).dimensions(starX, y + (ROW_H - 20) / 2, STAR_BTN_W, 20).build();

            ButtonWidget addBtn = ButtonWidget.builder(Text.literal(label), btn -> addPalette(palette))
                    .dimensions(addX, y + (ROW_H - 20) / 2, buttonW, 20).build();
            addBtn.active = !loading && !isAdding && !isSaved;

            ButtonWidget openBtn = ButtonWidget.builder(Text.literal("Open"), btn -> openPalette(palette))
                    .dimensions(openX, y + (ROW_H - 20) / 2, buttonW, 20).build();
            openBtn.active = true;

            addDrawableChild(starBtn);
            addDrawableChild(addBtn);
            addDrawableChild(openBtn);
            listButtons.add(starBtn);
            listButtons.add(addBtn);
            listButtons.add(openBtn);
            rows.add(new Row(palette, y, gridX, gridY, textX));

            y += ROW_H;
        }
    }

    private void drawPaletteGrid(DrawContext ctx, PaletteEntry palette, int x, int y) {
        String[] blocks = palette.blocks();
        for (int i = 0; i < blocks.length; i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int cellX = x + col * CELL_SIZE;
            int cellY = y + row * CELL_SIZE;

            ctx.fill(cellX, cellY, cellX + CELL_SIZE, cellY + CELL_SIZE, 0xFF2B2B2B);
            drawBorder(ctx, cellX, cellY, CELL_SIZE, CELL_SIZE, 0xFF3A3A3A);

            String block = normalizeBlockName(blocks[i]);
            if (block.isEmpty()) {
                continue;
            }
            BlockTexture tex = ensureBlockTexture(block);
            if (tex != null) {
                drawTextureContain(ctx, tex.id(), cellX, cellY, CELL_SIZE, CELL_SIZE, tex.width(), tex.height());
            }
        }
    }

    private BlockTexture ensureBlockTexture(String block) {
        BlockTexture cached = blockTextures.get(block);
        if (cached != null) {
            return cached;
        }
        if (failedBlocks.contains(block) || loadingBlocks.contains(block)) {
            return null;
        }
        loadingBlocks.add(block);
        int generation = textureGeneration;
        Thread loadThread = new Thread(() -> {
            NativeImage image = null;
            try {
                image = downloadBlockImage(block);
            } catch (IOException ignored) {
                // handled below
            }
            NativeImage loaded = image;
            MinecraftClient client = this.client;
            if (client == null) {
                if (loaded != null) {
                    loaded.close();
                }
                return;
            }
            client.execute(() -> {
                loadingBlocks.remove(block);
                if (generation != textureGeneration) {
                    if (loaded != null) {
                        loaded.close();
                    }
                    return;
                }
                if (loaded == null) {
                    failedBlocks.add(block);
                    return;
                }
                Identifier id = Identifier.of("builders_dashboard", "blockpalettes/" + sanitizeId(block));
                NativeImageBackedTexture texture = new NativeImageBackedTexture(
                        () -> "builders_dashboard/blockpalettes/" + block,
                        loaded
                );
                TextureManager manager = client.getTextureManager();
                manager.registerTexture(id, texture);
                blockTextures.put(block, new BlockTexture(id, texture, loaded.getWidth(), loaded.getHeight()));
            });
        }, "BuildersDashboard-BlockTexture");
        loadThread.setDaemon(true);
        loadThread.start();
        return null;
    }

    private void addPalette(PaletteEntry palette) {
        if (palette == null || adding.contains(palette.id)) {
            return;
        }
        adding.add(palette.id);
        statusText = "Downloading palette #" + palette.id + "...";
        rebuildList();

        MinecraftClient client = this.client;
        Thread addThread = new Thread(() -> {
            AddResult result = buildPaletteFile(palette);
            if (client != null) {
                client.execute(() -> applyAddResult(palette, result));
            }
        }, "BuildersDashboard-PaletteAdd");
        addThread.setDaemon(true);
        addThread.start();
    }

    private void applyAddResult(PaletteEntry palette, AddResult result) {
        adding.remove(palette.id);
        if (result == null || result.path == null) {
            statusText = "Palette download failed";
            rebuildList();
            return;
        }

        ImageLibrary.ImageEntry addedEntry = ImageLibrary.addImageFromPathWithEntry(result.path);
        try {
            Files.deleteIfExists(result.path);
        } catch (IOException ignored) {
            // best-effort cleanup
        }

        if (addedEntry != null) {
            PaletteLibrary.markUsed(snapshotFromPalette(palette));
            ImageLibrary.setPaletteInfo(addedEntry, palette.id, List.of(palette.blocks()));
            if (!selectedGroupName.isBlank()) {
                if (ImageLibrary.getGroupNames().stream().anyMatch(name -> name.equalsIgnoreCase(selectedGroupName))) {
                    ImageLibrary.setGroupImage(selectedGroupName, addedEntry, true);
                    statusText = "Added palette #" + palette.id + " to group";
                } else {
                    statusText = "Group not found";
                }
            } else {
                statusText = "Added palette #" + palette.id;
            }
            if (viewMode == ViewMode.RECENT) {
                loadFromLibrary(PaletteLibrary.getRecents());
            }
        } else {
            statusText = "No image added";
        }
        refreshSavedPaletteIds();
        rebuildList();
    }

    private AddResult buildPaletteFile(PaletteEntry palette) {
        String[] blocks = palette.blocks();
        BufferedImage[] blockImages = new BufferedImage[blocks.length];

        for (int i = 0; i < blocks.length; i++) {
            String block = normalizeBlockName(blocks[i]);
            if (block.isEmpty()) {
                return AddResult.failure();
            }
            try {
                BufferedImage image = downloadBlockBuffered(block);
                if (image == null) {
                    return AddResult.failure();
                }
                blockImages[i] = image;
            } catch (IOException e) {
                return AddResult.failure();
            }
        }

        int cell = 128;
        int width = cell * GRID_COLS;
        int height = cell * GRID_ROWS;
        BufferedImage output = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g = output.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        for (int i = 0; i < blockImages.length; i++) {
            int col = i % GRID_COLS;
            int row = i / GRID_COLS;
            int dx = col * cell;
            int dy = row * cell;
            BufferedImage img = blockImages[i];
            g.drawImage(img, dx, dy, cell, cell, null);
        }
        g.dispose();

        try {
            Path tmp = Files.createTempFile("palette_" + palette.id + "_", ".png");
            ImageIO.write(output, "png", tmp.toFile());
            return AddResult.success(tmp);
        } catch (IOException e) {
            return AddResult.failure();
        }
    }

    private void refreshSavedPaletteIds() {
        List<ImageLibrary.ImageEntry> images = ImageLibrary.getImages();
        lastImageCount = images.size();
        savedPaletteIds.clear();
        for (ImageLibrary.ImageEntry entry : images) {
            String name = entry.filePath().getFileName().toString().toLowerCase(Locale.ROOT);
            int id = parsePaletteId(name);
            if (id > 0) {
                savedPaletteIds.add(id);
            }
        }
    }

    private static int parsePaletteId(String fileName) {
        if (fileName == null) {
            return -1;
        }
        String lower = fileName.toLowerCase(Locale.ROOT);
        int start = lower.indexOf("palette_");
        if (start < 0) {
            return -1;
        }
        start += "palette_".length();
        int end = start;
        while (end < lower.length() && Character.isDigit(lower.charAt(end))) {
            end++;
        }
        if (end == start) {
            return -1;
        }
        try {
            return Integer.parseInt(lower.substring(start, end));
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    private void updateNavButtons() {
        boolean allowPaging = viewMode == ViewMode.ALL;
        if (recentButton != null) {
            recentButton.active = allowPaging && !loading && !"recent".equals(sort);
        }
        if (popularButton != null) {
            popularButton.active = allowPaging && !loading && !"popular".equals(sort);
        }
        if (refreshButton != null) {
            refreshButton.active = allowPaging && !loading;
        }
        if (prevButton != null) {
            prevButton.active = allowPaging && !loading && page > 1;
        }
        if (nextButton != null) {
            nextButton.active = allowPaging && !loading && page < totalPages;
        }
    }

    private void clearBlockTextures() {
        textureGeneration++;
        MinecraftClient client = this.client;
        if (client != null) {
            TextureManager manager = client.getTextureManager();
            for (BlockTexture tex : blockTextures.values()) {
                manager.destroyTexture(tex.id());
            }
        }
        blockTextures.clear();
        loadingBlocks.clear();
        failedBlocks.clear();
    }

    private static String sanitizeSort(String sortValue) {
        if ("popular".equalsIgnoreCase(sortValue)) {
            return "popular";
        }
        if ("trending".equalsIgnoreCase(sortValue)) {
            return "trending";
        }
        if ("oldest".equalsIgnoreCase(sortValue)) {
            return "oldest";
        }
        return "recent";
    }

    private static String normalizeBlockName(String block) {
        if (block == null) {
            return "";
        }
        String trimmed = block.trim().toLowerCase(Locale.ROOT);
        return trimmed.isEmpty() ? "" : trimmed;
    }

    private static String formatBlockName(String block) {
        String normalized = normalizeBlockName(block);
        if (normalized.isEmpty()) {
            return "";
        }
        String[] parts = normalized.split("_");
        StringBuilder out = new StringBuilder(normalized.length());
        for (String part : parts) {
            if (part.isEmpty()) {
                continue;
            }
            if (out.length() > 0) {
                out.append(' ');
            }
            out.append(part.substring(0, 1).toUpperCase(Locale.ROOT));
            if (part.length() > 1) {
                out.append(part.substring(1));
            }
        }
        return out.toString();
    }

    private String findPaletteNote(int paletteId) {
        if (paletteId <= 0) {
            return "";
        }
        for (ImageLibrary.ImageEntry entry : ImageLibrary.getImages()) {
            String name = entry.filePath().getFileName().toString();
            int id = parsePaletteId(name);
            if (id == paletteId) {
                String note = entry.note();
                return note == null ? "" : note.trim();
            }
        }
        return "";
    }

    private static String sanitizeId(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        String result = out.toString();
        return result.isEmpty() ? "block" : result;
    }

    private static String encode(String value) {
        if (value == null) {
            return "";
        }
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private static String httpGet(String url) throws IOException {
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        conn.setRequestProperty("Accept", "application/json");
        int code = conn.getResponseCode();
        InputStream stream = code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream();
        if (stream == null) {
            conn.disconnect();
            throw new IOException("HTTP " + code);
        }
        try (InputStream in = stream) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } finally {
            conn.disconnect();
        }
    }

    private static NativeImage downloadBlockImage(String block) throws IOException {
        String url = BLOCK_IMAGE_BASE + block + ".png";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        try (InputStream in = conn.getInputStream()) {
            return NativeImage.read(in);
        } finally {
            conn.disconnect();
        }
    }

    private static BufferedImage downloadBlockBuffered(String block) throws IOException {
        String url = BLOCK_IMAGE_BASE + block + ".png";
        HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);
        conn.setRequestProperty("User-Agent", USER_AGENT);
        try (InputStream in = conn.getInputStream()) {
            return ImageIO.read(in);
        } finally {
            conn.disconnect();
        }
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

    private static final class Row {
        private final PaletteEntry palette;
        private final int y;
        private final int gridX;
        private final int gridY;
        private final int textX;

        private Row(PaletteEntry palette, int y, int gridX, int gridY, int textX) {
            this.palette = palette;
            this.y = y;
            this.gridX = gridX;
            this.gridY = gridY;
            this.textX = textX;
        }
    }

    private record BlockTexture(Identifier id, NativeImageBackedTexture texture, int width, int height) {}

    private static final class PaletteResponse {
        boolean success;
        String message;
        int total_pages;
        List<PaletteEntry> palettes;
    }

    private static final class SearchResponse {
        boolean success;
        List<String> blocks;
    }

    private static final class PaletteEntry {
        int id;
        int likes;
        String time_ago;
        String blockOne;
        String blockTwo;
        String blockThree;
        String blockFour;
        String blockFive;
        String blockSix;

        private String[] blocks() {
            return new String[] {
                    blockOne,
                    blockTwo,
                    blockThree,
                    blockFour,
                    blockFive,
                    blockSix
            };
        }
    }

    private static final class FetchResult {
        private final boolean success;
        private final List<PaletteEntry> palettes;
        private final int totalPages;
        private final String error;

        private FetchResult(boolean success, List<PaletteEntry> palettes, int totalPages, String error) {
            this.success = success;
            this.palettes = palettes;
            this.totalPages = totalPages;
            this.error = error;
        }

        private static FetchResult success(List<PaletteEntry> palettes, int totalPages) {
            return new FetchResult(true, palettes, totalPages, null);
        }

        private static FetchResult failure(String error) {
            return new FetchResult(false, new ArrayList<>(), 1, error);
        }
    }

    private static final class AddResult {
        private final Path path;

        private AddResult(Path path) {
            this.path = path;
        }

        private static AddResult success(Path path) {
            return new AddResult(path);
        }

        private static AddResult failure() {
            return new AddResult(null);
        }
    }

    private static final class SearchResult {
        private final boolean success;
        private final List<String> blocks;

        private SearchResult(boolean success, List<String> blocks) {
            this.success = success;
            this.blocks = blocks;
        }

        private static SearchResult success(List<String> blocks) {
            return new SearchResult(true, blocks);
        }

        private static SearchResult failure(String reason) {
            return new SearchResult(false, new ArrayList<>());
        }
    }

    private static final class SearchRow {
        private final String block;
        private final int x;
        private final int y;
        private final int w;
        private final int h;

        private SearchRow(String block, int x, int y, int w, int h) {
            this.block = block;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        private boolean contains(double mouseX, double mouseY) {
            return mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        }
    }

    private enum ViewMode {
        ALL,
        FAVORITES,
        RECENT
    }
}
