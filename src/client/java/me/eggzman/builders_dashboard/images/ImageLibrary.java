package me.eggzman.builders_dashboard.images;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;

import java.awt.AWTError;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.awt.HeadlessException;
import java.awt.Toolkit;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.JFileChooser;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.imageio.ImageIO;

public final class ImageLibrary {
    private static final String MOD_ID = "builders_dashboard";
    private static final int DEFAULT_FRAME_COLOR = 0xFFF5F5F5;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final List<ImageEntry> IMAGES = new ArrayList<>();
    private static final List<GroupConfig> GROUPS = new ArrayList<>();
    private static final Map<String, GroupEntry> BASE_STATE = new HashMap<>();
    private static final List<String> RECENT_IMAGES = new ArrayList<>();
    private static boolean baseStateActive;
    private static Path lastBrowseDir;
    private static String lastPickerError = "";
    private static String activeGroupName = "";

    private static final Path CONFIG_DIR =
            FabricLoader.getInstance().getConfigDir().resolve(MOD_ID);
    private static final Path IMAGES_DIR = CONFIG_DIR.resolve("images");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("images.json");

    private ImageLibrary() {}

    public static void init() {
        loadConfig();
    }

    public static List<ImageEntry> getImages() {
        return Collections.unmodifiableList(IMAGES);
    }

    public static List<ImageEntry> getEnabledImages() {
        List<ImageEntry> enabled = new ArrayList<>();
        for (ImageEntry entry : IMAGES) {
            if (entry.isEnabled()) {
                enabled.add(entry);
            }
        }
        return enabled;
    }

    public static List<ImageEntry> getPinnedImages() {
        List<ImageEntry> pinned = new ArrayList<>();
        for (ImageEntry entry : IMAGES) {
            if (entry.isEnabled() && entry.isPinned()) {
                pinned.add(entry);
            }
        }
        return pinned;
    }

    public static List<ImageEntry> getFavoriteImages() {
        List<ImageEntry> favorites = new ArrayList<>();
        for (ImageEntry entry : IMAGES) {
            if (entry.isFavorite()) {
                favorites.add(entry);
            }
        }
        return favorites;
    }

    public static List<ImageEntry> getRecentImages() {
        List<ImageEntry> recent = new ArrayList<>();
        if (RECENT_IMAGES.isEmpty()) {
            return recent;
        }
        Map<String, ImageEntry> map = new HashMap<>();
        for (ImageEntry entry : IMAGES) {
            map.put(imageRelPath(entry), entry);
        }
        for (String rel : RECENT_IMAGES) {
            ImageEntry entry = map.get(rel);
            if (entry != null) {
                recent.add(entry);
            }
        }
        return recent;
    }

    public static String getActiveGroupName() {
        return activeGroupName;
    }

    public static int getActiveGroupFrameColor() {
        GroupConfig group = findGroup(activeGroupName);
        if (group != null && group.frameColor != 0) {
            return ensureOpaque(group.frameColor);
        }
        return DEFAULT_FRAME_COLOR;
    }

    public static int getFrameColorForImage(ImageEntry entry) {
        if (entry == null) {
            return DEFAULT_FRAME_COLOR;
        }
        GroupConfig active = findGroup(activeGroupName);
        if (active != null && groupContainsEntry(active, entry)) {
            return ensureOpaque(active.frameColor);
        }
        GroupConfig first = findGroupForEntry(entry);
        if (first != null) {
            return ensureOpaque(first.frameColor);
        }
        return DEFAULT_FRAME_COLOR;
    }

    public static List<String> getGroupNames() {
        List<String> names = new ArrayList<>();
        for (GroupConfig group : GROUPS) {
            if (group != null && group.name != null) {
                names.add(group.name);
            }
        }
        return names;
    }

    public static boolean renameGroup(String oldName, String newName) {
        String from = normalizeGroupName(oldName);
        String to = normalizeGroupName(newName);
        if (from.isEmpty() || to.isEmpty()) {
            return false;
        }
        if (from.equalsIgnoreCase(to)) {
            return true;
        }
        if (findGroup(to) != null) {
            return false;
        }
        GroupConfig group = findGroup(from);
        if (group == null) {
            return false;
        }
        group.name = to;
        if (activeGroupName != null && activeGroupName.equalsIgnoreCase(from)) {
            activeGroupName = to;
        }
        me.eggzman.builders_dashboard.input.Keybinds.renameGroupBinding(from, to);
        saveConfig();
        return true;
    }

    public static boolean duplicateGroup(String sourceName, String newName) {
        String from = normalizeGroupName(sourceName);
        String to = normalizeGroupName(newName);
        if (from.isEmpty() || to.isEmpty()) {
            return false;
        }
        if (findGroup(to) != null) {
            return false;
        }
        GroupConfig source = findGroup(from);
        if (source == null) {
            return false;
        }
        GroupConfig copy = new GroupConfig();
        copy.name = to;
        copy.frameColor = source.frameColor;
        copy.entries = new ArrayList<>();
        if (source.entries != null) {
            for (GroupEntry entry : source.entries) {
                GroupEntry cloned = new GroupEntry();
                cloned.file = entry.file;
                cloned.enabled = entry.enabled;
                cloned.x = entry.x;
                cloned.y = entry.y;
                cloned.hasPosition = entry.hasPosition;
                cloned.scale = entry.scale;
                cloned.opacity = entry.opacity;
                cloned.locked = entry.locked;
                cloned.pinned = entry.pinned;
                copy.entries.add(cloned);
            }
        }
        GROUPS.add(copy);
        saveConfig();
        return true;
    }

    public static boolean groupContainsImage(String groupName, ImageEntry entry) {
        GroupConfig group = findGroup(normalizeGroupName(groupName));
        if (group == null || group.entries == null) {
            return false;
        }
        return groupContainsEntry(group, entry);
    }

    public static void setGroupImage(String groupName, ImageEntry entry, boolean include) {
        GroupConfig group = findGroup(normalizeGroupName(groupName));
        if (group == null) {
            return;
        }
        if (group.entries == null) {
            group.entries = new ArrayList<>();
        }
        String rel = imageRelPath(entry);
        GroupEntry found = null;
        for (GroupEntry groupEntry : group.entries) {
            if (groupEntry != null && rel.equals(groupEntry.file)) {
                found = groupEntry;
                break;
            }
        }
        if (!include) {
            if (found != null) {
                group.entries.remove(found);
                saveConfig();
            }
            return;
        }
        if (found == null) {
            GroupEntry groupEntry = new GroupEntry();
            groupEntry.file = rel;
            groupEntry.enabled = entry.isEnabled();
            groupEntry.hasPosition = entry.hasPosition();
            groupEntry.x = entry.x();
            groupEntry.y = entry.y();
            groupEntry.scale = entry.scale();
            groupEntry.opacity = entry.opacity();
            groupEntry.locked = entry.isLocked();
            groupEntry.pinned = entry.isPinned();
            group.entries.add(groupEntry);
        }
        saveConfig();
    }

    public static int getGroupFrameColor(String groupName) {
        GroupConfig group = findGroup(normalizeGroupName(groupName));
        if (group == null || group.frameColor == 0) {
            return DEFAULT_FRAME_COLOR;
        }
        return ensureOpaque(group.frameColor);
    }

    public static void setGroupFrameColor(String groupName, int color) {
        GroupConfig group = findGroup(normalizeGroupName(groupName));
        if (group == null) {
            return;
        }
        group.frameColor = ensureOpaque(color);
        saveConfig();
    }

    public static boolean saveGroup(String name) {
        String normalized = normalizeGroupName(name);
        if (normalized.isEmpty()) {
            return false;
        }

        GroupConfig group = findGroup(normalized);
        if (group == null) {
            group = new GroupConfig();
            group.name = normalized;
            group.entries = new ArrayList<>();
            group.frameColor = DEFAULT_FRAME_COLOR;
            GROUPS.add(group);
            for (ImageEntry entry : IMAGES) {
                if (!entry.isEnabled()) {
                    continue;
                }
                GroupEntry groupEntry = new GroupEntry();
                updateGroupEntryFromImage(groupEntry, entry);
                group.entries.add(groupEntry);
            }
        } else {
            if (group.entries == null) {
                group.entries = new ArrayList<>();
            }
            Map<String, ImageEntry> entryMap = new HashMap<>();
            for (ImageEntry entry : IMAGES) {
                entryMap.put(imageRelPath(entry), entry);
            }
            for (GroupEntry groupEntry : group.entries) {
                if (groupEntry == null || groupEntry.file == null) {
                    continue;
                }
                ImageEntry imageEntry = entryMap.get(groupEntry.file);
                if (imageEntry != null) {
                    updateGroupEntryFromImage(groupEntry, imageEntry);
                }
            }
        }

        saveConfig();
        return true;
    }

    public static boolean deleteGroup(String name) {
        String normalized = normalizeGroupName(name);
        if (normalized.isEmpty()) {
            return false;
        }

        GroupConfig group = findGroup(normalized);
        if (group == null) {
            return false;
        }

        GROUPS.remove(group);
        if (activeGroupName != null && activeGroupName.equalsIgnoreCase(group.name)) {
            activeGroupName = "";
        }
        me.eggzman.builders_dashboard.input.Keybinds.deleteGroupBinding(normalized);
        saveConfig();
        return true;
    }

    public static boolean applyGroup(String name) {
        String normalized = normalizeGroupName(name);
        if (normalized.isEmpty()) {
            return false;
        }

        if (activeGroupName != null && activeGroupName.equalsIgnoreCase(normalized)) {
            if (baseStateActive) {
                restoreBaseState();
            } else {
                GroupConfig active = findGroup(normalized);
                if (active != null && active.entries != null) {
                    for (ImageEntry entry : IMAGES) {
                        if (groupContainsEntry(active, entry)) {
                            entry.setEnabled(false);
                            entry.setPinned(false);
                            entry.setLocked(false);
                        }
                    }
                }
            }
            activeGroupName = "";
            baseStateActive = false;
            saveConfig();
            return true;
        }

        if (activeGroupName != null && !activeGroupName.isBlank()
                && !activeGroupName.equalsIgnoreCase(normalized)) {
            saveActiveGroupState();
        }

        if (!baseStateActive && (activeGroupName == null || activeGroupName.isBlank())) {
            captureBaseState();
        }

        GroupConfig group = findGroup(normalized);
        if (group == null || group.entries == null) {
            return false;
        }

        Map<String, GroupEntry> map = new HashMap<>();
        for (GroupEntry entry : group.entries) {
            if (entry != null && entry.file != null) {
                map.put(entry.file, entry);
            }
        }

        for (ImageEntry entry : IMAGES) {
            String rel = imageRelPath(entry);
            GroupEntry groupEntry = map.get(rel);
            if (groupEntry == null) {
                if (isInAnyGroup(entry)) {
                    entry.setEnabled(false);
                    entry.setPinned(false);
                    entry.setLocked(false);
                }
                continue;
            }

            applyGroupEntryToImage(groupEntry, entry);
        }

        activeGroupName = group.name;
        saveConfig();
        return true;
    }

    public static void openFilePicker(MinecraftClient client) {
        Thread pickerThread = new Thread(() -> {
            PickerSelection selection = pickFile();
            if (selection.result() != PickerResult.SELECTED || selection.path() == null) {
                return;
            }

            client.execute(() -> addImageFromPath(selection.path()));
        }, "BuildersDashboard-FilePicker");

        pickerThread.setDaemon(true);
        pickerThread.start();
    }

    public static PickerSelection pickFile() {
        return openFilePickerInternal();
    }

    public static String getLastPickerError() {
        return lastPickerError;
    }

    public static boolean addImageFromPath(Path source) {
        return addImageFromPathWithEntry(source) != null;
    }

    public static ImageEntry addImageFromPathWithEntry(Path source) {
        String name = source.getFileName().toString();
        String ext = getExtension(name);
        if (ext == null || (!ext.equals("png") && !ext.equals("jpg") && !ext.equals("jpeg"))) {
            return null;
        }

        try {
            Files.createDirectories(IMAGES_DIR);
        } catch (IOException ignored) {
            return null;
        }

        String baseName = stripExtension(name);
        String safeName = sanitizeFileName(baseName) + "." + ext;
        Path target = uniquePath(IMAGES_DIR.resolve(safeName));

        try {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ignored) {
            return null;
        }

        lastBrowseDir = source.getParent();

        ImageEntry entry = loadImage(target, true);
        if (entry != null) {
            IMAGES.add(entry);
            markImageUsed(entry);
            return entry;
        }

        return null;
    }

    public static Path getDefaultBrowseDir() {
        if (lastBrowseDir != null && Files.isDirectory(lastBrowseDir)) {
            return lastBrowseDir;
        }
        String home = System.getProperty("user.home");
        if (home != null && !home.isBlank()) {
            return Path.of(home);
        }
        return CONFIG_DIR;
    }

    private static PickerSelection openFilePickerInternal() {
        lastPickerError = "";
        if (isWindows()) {
            PickerSelection windowsSelection = tryWindowsDialog();
            if (windowsSelection.result() != PickerResult.FAILED) {
                return windowsSelection;
            }
        }

        forceAwtEnabled();
        try {
            Toolkit.getDefaultToolkit();
        } catch (Throwable t) {
            lastPickerError = "AWT toolkit failed: " + t.getClass().getSimpleName();
            return new PickerSelection(PickerResult.FAILED, null);
        }

        PickerSelection result = tryFileDialog();
        if (result.result() != PickerResult.FAILED) {
            lastPickerError = "";
            return result;
        }

        result = trySwingChooser();
        if (result.result() != PickerResult.FAILED) {
            lastPickerError = "";
            return result;
        }

        if (lastPickerError == null || lastPickerError.isBlank()) {
            lastPickerError = "Unknown AWT failure";
        }
        return new PickerSelection(PickerResult.FAILED, null);
    }

    private static void forceAwtEnabled() {
        String headless = System.getProperty("java.awt.headless");
        if ("true".equalsIgnoreCase(headless)) {
            System.setProperty("java.awt.headless", "false");
        }
    }

    private static boolean isWindows() {
        String name = System.getProperty("os.name");
        if (name == null) {
            return false;
        }
        return name.toLowerCase(Locale.ROOT).contains("win");
    }

    private static PickerSelection tryWindowsDialog() {
        StringBuilder script = new StringBuilder(256);
        script.append("[Console]::OutputEncoding = [System.Text.Encoding]::UTF8;");
        script.append("Add-Type -AssemblyName System.Windows.Forms;");
        script.append("$dialog = New-Object System.Windows.Forms.OpenFileDialog;");
        script.append("$dialog.Filter = 'Images (*.png;*.jpg;*.jpeg)|*.png;*.jpg;*.jpeg|All Files (*.*)|*.*';");
        script.append("$dialog.Multiselect = $false;");
        Path browseDir = getDefaultBrowseDir();
        if (browseDir != null) {
            String dir = browseDir.toString().replace("'", "''");
            script.append("$dialog.InitialDirectory = '").append(dir).append("';");
        }
        script.append("if ($dialog.ShowDialog() -eq [System.Windows.Forms.DialogResult]::OK) { ");
        script.append("Write-Output $dialog.FileName");
        script.append(" }");

        ProcessBuilder builder = new ProcessBuilder(
                "powershell.exe",
                "-NoProfile",
                "-Command",
                script.toString()
        );
        builder.redirectErrorStream(true);

        String output = "";
        int exitCode;
        try {
            Process process = builder.start();
            output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            exitCode = process.waitFor();
        } catch (Throwable t) {
            lastPickerError = "PowerShell dialog error: " + t.getClass().getSimpleName();
            return new PickerSelection(PickerResult.FAILED, null);
        }

        if (exitCode != 0) {
            lastPickerError = "PowerShell dialog failed (exit " + exitCode + ")";
            return new PickerSelection(PickerResult.FAILED, null);
        }

        Path selected = extractSelectedPath(output);
        if (selected == null) {
            return new PickerSelection(PickerResult.CANCELLED, null);
        }

        return new PickerSelection(PickerResult.SELECTED, selected);
    }

    private static Path extractSelectedPath(String text) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String[] lines = text.split("\\R");
        String fallback = null;
        for (String line : lines) {
            if (line == null) {
                continue;
            }
            String trimmed = sanitizePathLine(line);
            if (trimmed.isEmpty()) {
                continue;
            }
            if (fallback == null) {
                fallback = trimmed;
            }
            try {
                Path path = Path.of(trimmed);
                if (Files.exists(path)) {
                    return path;
                }
            } catch (Exception ignored) {
                // keep searching
            }
        }
        if (fallback == null) {
            return null;
        }
        try {
            return Path.of(fallback);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String sanitizePathLine(String line) {
        String trimmed = line.trim();
        if (!trimmed.isEmpty() && trimmed.charAt(0) == '\uFEFF') {
            trimmed = trimmed.substring(1).trim();
        }
        if (trimmed.length() >= 2 && trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
            trimmed = trimmed.substring(1, trimmed.length() - 1).trim();
        }
        return trimmed;
    }

    private static PickerSelection tryFileDialog() {
        if (GraphicsEnvironment.isHeadless()) {
            lastPickerError = "AWT headless mode";
            return new PickerSelection(PickerResult.FAILED, null);
        }

        AtomicReference<PickerSelection> result = new AtomicReference<>(new PickerSelection(PickerResult.CANCELLED, null));
        try {
            EventQueue.invokeAndWait(() -> {
                Frame frame = new Frame();
                frame.setAlwaysOnTop(true);
                frame.setLocationRelativeTo(null);
                frame.setUndecorated(true);
                frame.setVisible(true);

                FileDialog dialog = new FileDialog(frame, "Select an image", FileDialog.LOAD);
                Path browseDir = getDefaultBrowseDir();
                if (browseDir != null) {
                    dialog.setDirectory(browseDir.toString());
                }
                dialog.setFilenameFilter((dir, name) -> {
                    String lower = name.toLowerCase(Locale.ROOT);
                    return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
                });
                dialog.setAlwaysOnTop(true);
                dialog.setVisible(true);

                String file = dialog.getFile();
                String dir = dialog.getDirectory();
                dialog.dispose();
                frame.dispose();

                if (file != null && dir != null && !file.isBlank() && !dir.isBlank()) {
                    result.set(new PickerSelection(PickerResult.SELECTED, Path.of(dir, file)));
                } else {
                    result.set(new PickerSelection(PickerResult.CANCELLED, null));
                }
            });
        } catch (HeadlessException | AWTError ex) {
            lastPickerError = "FileDialog error: " + ex.getClass().getSimpleName();
            return new PickerSelection(PickerResult.FAILED, null);
        } catch (Throwable t) {
            lastPickerError = "FileDialog error: " + t.getClass().getSimpleName();
            return new PickerSelection(PickerResult.FAILED, null);
        }

        return result.get();
    }

    private static PickerSelection trySwingChooser() {
        if (GraphicsEnvironment.isHeadless()) {
            lastPickerError = "Swing headless mode";
            return new PickerSelection(PickerResult.FAILED, null);
        }

        AtomicReference<PickerSelection> result = new AtomicReference<>(new PickerSelection(PickerResult.CANCELLED, null));
        try {
            SwingUtilities.invokeAndWait(() -> {
                try {
                    UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
                } catch (Exception ignored) {
                    // best-effort
                }

                JFileChooser chooser = new JFileChooser();
                Path browseDir = getDefaultBrowseDir();
                if (browseDir != null && Files.isDirectory(browseDir)) {
                    chooser.setCurrentDirectory(browseDir.toFile());
                }
                chooser.setFileFilter(new FileNameExtensionFilter("Images", "png", "jpg", "jpeg"));
                chooser.setMultiSelectionEnabled(false);

                int selection = chooser.showOpenDialog(null);
                if (selection == JFileChooser.APPROVE_OPTION && chooser.getSelectedFile() != null) {
                    result.set(new PickerSelection(PickerResult.SELECTED, chooser.getSelectedFile().toPath()));
                } else {
                    result.set(new PickerSelection(PickerResult.CANCELLED, null));
                }
            });
        } catch (HeadlessException | AWTError ex) {
            lastPickerError = "Swing error: " + ex.getClass().getSimpleName();
            return new PickerSelection(PickerResult.FAILED, null);
        } catch (Throwable t) {
            lastPickerError = "Swing error: " + t.getClass().getSimpleName();
            return new PickerSelection(PickerResult.FAILED, null);
        }

        return result.get();
    }

    public static void setEnabled(ImageEntry entry, boolean enabled) {
        entry.setEnabled(enabled);
        updateActiveGroupEntry(entry);
        saveConfig();
    }

    public static void toggleEnabled(ImageEntry entry) {
        entry.setEnabled(!entry.isEnabled());
        updateActiveGroupEntry(entry);
        saveConfig();
    }

    public static void setLocked(ImageEntry entry, boolean locked) {
        entry.setLocked(locked);
        updateActiveGroupEntry(entry);
        saveConfig();
    }

    public static void toggleLocked(ImageEntry entry) {
        entry.setLocked(!entry.isLocked());
        updateActiveGroupEntry(entry);
        saveConfig();
    }

    public static void setPinned(ImageEntry entry, boolean pinned) {
        entry.setPinned(pinned);
        updateActiveGroupEntry(entry);
        saveConfig();
    }

    public static void togglePinned(ImageEntry entry) {
        entry.setPinned(!entry.isPinned());
        updateActiveGroupEntry(entry);
        saveConfig();
    }

    public static void setFavorite(ImageEntry entry, boolean favorite) {
        entry.setFavorite(favorite);
        saveConfig();
    }

    public static void toggleFavorite(ImageEntry entry) {
        entry.setFavorite(!entry.isFavorite());
        saveConfig();
    }

    public static void setPaletteInfo(ImageEntry entry, int paletteId, List<String> blocks) {
        if (entry == null || paletteId <= 0) {
            return;
        }
        entry.setPaletteInfo(paletteId, blocks);
        saveConfig();
    }

    public static void markImageUsed(ImageEntry entry) {
        if (entry == null) {
            return;
        }
        String rel = imageRelPath(entry);
        RECENT_IMAGES.remove(rel);
        RECENT_IMAGES.add(0, rel);
        while (RECENT_IMAGES.size() > 10) {
            RECENT_IMAGES.remove(RECENT_IMAGES.size() - 1);
        }
        saveConfig();
    }

    public static void setNote(ImageEntry entry, String note) {
        entry.setNote(note);
        saveConfig();
    }

    public static void setScale(ImageEntry entry, float scale, boolean save) {
        entry.setScale(scale);
        updateActiveGroupEntry(entry);
        if (save) {
            saveConfig();
        }
    }

    public static void setOpacity(ImageEntry entry, float opacity, boolean save) {
        entry.setOpacity(opacity);
        updateActiveGroupEntry(entry);
        if (save) {
            saveConfig();
        }
    }

    public static void setPosition(ImageEntry entry, int x, int y, boolean save) {
        entry.setPosition(x, y);
        updateActiveGroupEntry(entry);
        if (save) {
            saveConfig();
        }
    }

    public static void savePositions() {
        saveConfig();
    }

    public static void moveImage(ImageEntry entry, int newIndex) {
        int oldIndex = IMAGES.indexOf(entry);
        if (oldIndex < 0) {
            return;
        }

        int maxIndex = IMAGES.size() - 1;
        int target = Math.max(0, Math.min(maxIndex, newIndex));
        if (oldIndex == target) {
            return;
        }

        IMAGES.remove(oldIndex);
        IMAGES.add(target, entry);
        saveConfig();
    }

    public static void removeImage(ImageEntry entry) {
        IMAGES.remove(entry);
        RECENT_IMAGES.remove(imageRelPath(entry));
        MinecraftClient client = MinecraftClient.getInstance();
        if (client != null) {
            client.getTextureManager().destroyTexture(entry.textureId());
        }
        saveConfig();
    }

    private static void loadConfig() {
        IMAGES.clear();
        GROUPS.clear();
        RECENT_IMAGES.clear();

        if (!Files.exists(CONFIG_FILE)) {
            return;
        }

        try {
            String json = Files.readString(CONFIG_FILE);
            ImageConfig cfg = GSON.fromJson(json, ImageConfig.class);
            if (cfg == null) return;
            if (cfg.entries != null && !cfg.entries.isEmpty()) {
                for (ImageConfigEntry entryCfg : cfg.entries) {
                    Path path = IMAGES_DIR.resolve(entryCfg.file);
                    ImageEntry entry = loadImage(path, entryCfg.enabled);
                    if (entry != null) {
                        if (entryCfg.hasPosition) {
                            entry.setPosition(entryCfg.x, entryCfg.y);
                        }
                        if (entryCfg.scale > 0.0f) {
                            entry.setScale(entryCfg.scale);
                        }
                        if (entryCfg.opacity > 0.0f) {
                            entry.setOpacity(entryCfg.opacity);
                        }
                        entry.setLocked(entryCfg.locked);
                        entry.setPinned(entryCfg.pinned);
                        if (entryCfg.note != null) {
                            entry.setNote(entryCfg.note);
                        }
                        entry.setFavorite(entryCfg.favorite);
                        entry.setPaletteInfo(entryCfg.paletteId, entryCfg.paletteBlocks);
                        IMAGES.add(entry);
                    }
                }
            } else if (cfg.files != null) {
                for (String rel : cfg.files) {
                    Path path = IMAGES_DIR.resolve(rel);
                    ImageEntry entry = loadImage(path, true);
                    if (entry != null) {
                        IMAGES.add(entry);
                    }
                }
            }
            if (cfg.groups != null && !cfg.groups.isEmpty()) {
                for (GroupConfig group : cfg.groups) {
                    if (group == null || group.name == null || group.name.isBlank()) {
                        continue;
                    }
                    if (group.entries == null) {
                        group.entries = new ArrayList<>();
                    }
                    if (group.frameColor == 0) {
                        group.frameColor = DEFAULT_FRAME_COLOR;
                    }
                    GROUPS.add(group);
                }
            }
            if (cfg.activeGroup != null && !cfg.activeGroup.isBlank()) {
                activeGroupName = cfg.activeGroup;
            } else {
                activeGroupName = "";
            }
            RECENT_IMAGES.clear();
            if (cfg.recentImages != null) {
                RECENT_IMAGES.addAll(cfg.recentImages);
            }
        } catch (IOException | JsonSyntaxException ignored) {
            // Ignore invalid configs and start fresh
        }
    }

    private static void saveConfig() {
        ImageConfig cfg = new ImageConfig();
        cfg.entries = new ArrayList<>();
        for (ImageEntry entry : IMAGES) {
            ImageConfigEntry cfgEntry = new ImageConfigEntry();
            cfgEntry.file = IMAGES_DIR.relativize(entry.filePath()).toString();
            cfgEntry.enabled = entry.isEnabled();
            cfgEntry.hasPosition = entry.hasPosition();
            cfgEntry.x = entry.x();
            cfgEntry.y = entry.y();
            cfgEntry.scale = entry.scale();
            cfgEntry.opacity = entry.opacity();
            cfgEntry.locked = entry.isLocked();
            cfgEntry.pinned = entry.isPinned();
            cfgEntry.note = entry.note();
            cfgEntry.favorite = entry.isFavorite();
            cfgEntry.paletteId = entry.paletteId();
            cfgEntry.paletteBlocks = entry.paletteBlocks();
            cfg.entries.add(cfgEntry);
        }
        if (!GROUPS.isEmpty()) {
            cfg.groups = new ArrayList<>();
            for (GroupConfig group : GROUPS) {
                if (group != null && group.name != null) {
                    cfg.groups.add(group);
                }
            }
        }
        if (activeGroupName != null && !activeGroupName.isBlank()) {
            cfg.activeGroup = activeGroupName;
        }
        if (!RECENT_IMAGES.isEmpty()) {
            cfg.recentImages = new ArrayList<>(RECENT_IMAGES);
        }

        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(cfg));
        } catch (IOException ignored) {
            // best-effort
        }
    }

    private static ImageEntry loadImage(Path path, boolean enabled) {
        if (!Files.exists(path)) {
            return null;
        }

        NativeImage image = readImage(path);
        if (image == null) {
            return null;
        }

        String fileName = path.getFileName().toString();
        Identifier id = Identifier.of(MOD_ID, "uploads/" + sanitizeIdentifier(fileName));

        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) {
            image.close();
            return null;
        }

        NativeImageBackedTexture texture = new NativeImageBackedTexture(() -> "builders_dashboard/" + fileName, image);
        TextureManager textureManager = client.getTextureManager();
        textureManager.registerTexture(id, texture);

        return new ImageEntry(id, image.getWidth(), image.getHeight(), path, enabled);
    }

    private static NativeImage readImage(Path path) {
        try (InputStream in = Files.newInputStream(path)) {
            return NativeImage.read(in);
        } catch (IOException ignored) {
            // Fall back to ImageIO for JPG variants NativeImage can't read
        }

        BufferedImage buffered;
        try {
            buffered = ImageIO.read(path.toFile());
        } catch (IOException ignored) {
            return null;
        }
        if (buffered == null) {
            return null;
        }

        int w = buffered.getWidth();
        int h = buffered.getHeight();
        NativeImage nativeImage = new NativeImage(w, h, false);
        try {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    nativeImage.setColorArgb(x, y, buffered.getRGB(x, y));
                }
            }
        } catch (Throwable t) {
            nativeImage.close();
            return null;
        }

        return nativeImage;
    }

    private static Path uniquePath(Path path) {
        if (!Files.exists(path)) {
            return path;
        }

        String name = stripExtension(path.getFileName().toString());
        String ext = getExtension(path.getFileName().toString());
        int i = 1;
        while (true) {
            String candidate = name + "_" + i + "." + ext;
            Path candidatePath = path.getParent().resolve(candidate);
            if (!Files.exists(candidatePath)) {
                return candidatePath;
            }
            i++;
        }
    }

    private static String sanitizeFileName(String name) {
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
        return result.isEmpty() ? "image" : result;
    }

    private static String sanitizeIdentifier(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(lower.length());
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if ((c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') || c == '-' || c == '_' || c == '.' || c == '/') {
                out.append(c);
            } else {
                out.append('_');
            }
        }
        String result = out.toString();
        return result.isEmpty() ? "image" : result;
    }

    private static String stripExtension(String name) {
        int idx = name.lastIndexOf('.');
        return idx > 0 ? name.substring(0, idx) : name;
    }

    private static String getExtension(String name) {
        int idx = name.lastIndexOf('.');
        if (idx < 0 || idx == name.length() - 1) return null;
        return name.substring(idx + 1).toLowerCase(Locale.ROOT);
    }

    private static String imageRelPath(ImageEntry entry) {
        return IMAGES_DIR.relativize(entry.filePath()).toString();
    }

    private static int ensureOpaque(int color) {
        return (color & 0x00FFFFFF) | 0xFF000000;
    }

    private static String normalizeGroupName(String name) {
        if (name == null) {
            return "";
        }
        String trimmed = name.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.length() > 32) {
            trimmed = trimmed.substring(0, 32);
        }
        return trimmed;
    }

    private static GroupConfig findGroup(String name) {
        for (GroupConfig group : GROUPS) {
            if (group != null && group.name != null && group.name.equalsIgnoreCase(name)) {
                return group;
            }
        }
        return null;
    }

    private static void saveActiveGroupState() {
        GroupConfig group = findGroup(activeGroupName);
        if (group == null || group.entries == null) {
            return;
        }
        Map<String, ImageEntry> entryMap = new HashMap<>();
        for (ImageEntry entry : IMAGES) {
            entryMap.put(imageRelPath(entry), entry);
        }
        for (GroupEntry groupEntry : group.entries) {
            if (groupEntry == null || groupEntry.file == null) {
                continue;
            }
            ImageEntry imageEntry = entryMap.get(groupEntry.file);
            if (imageEntry != null) {
                updateGroupEntryFromImage(groupEntry, imageEntry);
            }
        }
        saveConfig();
    }

    private static void captureBaseState() {
        BASE_STATE.clear();
        for (ImageEntry entry : IMAGES) {
            GroupEntry groupEntry = new GroupEntry();
            updateGroupEntryFromImage(groupEntry, entry);
            BASE_STATE.put(groupEntry.file, groupEntry);
        }
        baseStateActive = true;
    }

    private static void restoreBaseState() {
        if (!baseStateActive) {
            return;
        }
        for (ImageEntry entry : IMAGES) {
            GroupEntry groupEntry = BASE_STATE.get(imageRelPath(entry));
            if (groupEntry != null) {
                applyGroupEntryToImage(groupEntry, entry);
            }
        }
    }

    private static void updateActiveGroupEntry(ImageEntry entry) {
        if (entry == null || activeGroupName == null || activeGroupName.isBlank()) {
            return;
        }
        GroupConfig group = findGroup(activeGroupName);
        if (group == null) {
            return;
        }
        GroupEntry groupEntry = findGroupEntry(group, entry);
        if (groupEntry == null) {
            return;
        }
        updateGroupEntryFromImage(groupEntry, entry);
    }

    private static void updateGroupEntryFromImage(GroupEntry groupEntry, ImageEntry entry) {
        groupEntry.file = imageRelPath(entry);
        groupEntry.enabled = entry.isEnabled();
        groupEntry.hasPosition = entry.hasPosition();
        groupEntry.x = entry.x();
        groupEntry.y = entry.y();
        groupEntry.scale = entry.scale();
        groupEntry.opacity = entry.opacity();
        groupEntry.locked = entry.isLocked();
        groupEntry.pinned = entry.isPinned();
    }

    private static void applyGroupEntryToImage(GroupEntry groupEntry, ImageEntry entry) {
        entry.setEnabled(groupEntry.enabled);
        entry.setPinned(groupEntry.pinned);
        entry.setLocked(groupEntry.locked);
        if (groupEntry.scale > 0.0f) {
            entry.setScale(groupEntry.scale);
        }
        if (groupEntry.opacity > 0.0f) {
            entry.setOpacity(groupEntry.opacity);
        }
        if (groupEntry.hasPosition) {
            entry.setPosition(groupEntry.x, groupEntry.y);
        } else {
            entry.clearPosition();
        }
    }

    private static GroupEntry findGroupEntry(GroupConfig group, ImageEntry entry) {
        if (group == null || group.entries == null || entry == null) {
            return null;
        }
        String rel = imageRelPath(entry);
        for (GroupEntry groupEntry : group.entries) {
            if (groupEntry != null && rel.equals(groupEntry.file)) {
                return groupEntry;
            }
        }
        return null;
    }

    private static boolean isInAnyGroup(ImageEntry entry) {
        return findGroupForEntry(entry) != null;
    }

    private static GroupConfig findGroupForEntry(ImageEntry entry) {
        if (entry == null) {
            return null;
        }
        for (GroupConfig group : GROUPS) {
            if (groupContainsEntry(group, entry)) {
                return group;
            }
        }
        return null;
    }

    private static boolean groupContainsEntry(GroupConfig group, ImageEntry entry) {
        if (group == null || group.entries == null || entry == null) {
            return false;
        }
        String rel = imageRelPath(entry);
        for (GroupEntry groupEntry : group.entries) {
            if (groupEntry != null && rel.equals(groupEntry.file)) {
                return true;
            }
        }
        return false;
    }

    public static final class ImageEntry {
        private final Identifier textureId;
        private final int width;
        private final int height;
        private final Path filePath;
        private boolean enabled;
        private int x;
        private int y;
        private boolean hasPosition;
        private float scale = 1.0f;
        private float opacity = 1.0f;
        private boolean locked;
        private boolean pinned;
        private boolean favorite;
        private String note = "";
        private int paletteId;
        private List<String> paletteBlocks = new ArrayList<>();

        private ImageEntry(Identifier textureId, int width, int height, Path filePath, boolean enabled) {
            this.textureId = textureId;
            this.width = width;
            this.height = height;
            this.filePath = filePath;
            this.enabled = enabled;
        }

        public Identifier textureId() {
            return textureId;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public Path filePath() {
            return filePath;
        }

        public boolean isEnabled() {
            return enabled;
        }

        private void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public int x() {
            return x;
        }

        public int y() {
            return y;
        }

        public boolean hasPosition() {
            return hasPosition;
        }

        public float scale() {
            return scale;
        }

        public float opacity() {
            return opacity;
        }

        public boolean isLocked() {
            return locked;
        }

        public boolean isPinned() {
            return pinned;
        }

        public boolean isFavorite() {
            return favorite;
        }

        public String note() {
            return note;
        }

        public int paletteId() {
            return paletteId;
        }

        public List<String> paletteBlocks() {
            return new ArrayList<>(paletteBlocks);
        }

        private void setPosition(int x, int y) {
            this.x = x;
            this.y = y;
            this.hasPosition = true;
        }

        private void clearPosition() {
            this.x = 0;
            this.y = 0;
            this.hasPosition = false;
        }

        private void setScale(float scale) {
            this.scale = clamp(scale, 0.5f, 2.0f);
        }

        private void setOpacity(float opacity) {
            this.opacity = clamp(opacity, 0.1f, 1.0f);
        }

        private void setLocked(boolean locked) {
            this.locked = locked;
        }

        private void setPinned(boolean pinned) {
            this.pinned = pinned;
        }

        private void setFavorite(boolean favorite) {
            this.favorite = favorite;
        }

        private void setPaletteInfo(int paletteId, List<String> blocks) {
            if (paletteId <= 0) {
                return;
            }
            this.paletteId = paletteId;
            this.paletteBlocks.clear();
            if (blocks != null) {
                for (String block : blocks) {
                    if (block != null && !block.isBlank()) {
                        this.paletteBlocks.add(block.trim());
                    }
                }
            }
        }

        private void setNote(String note) {
            String safe = note == null ? "" : note.trim();
            if (safe.length() > 100) {
                safe = safe.substring(0, 100);
            }
            this.note = safe;
        }
    }

    private static final class ImageConfig {
        List<String> files;
        List<ImageConfigEntry> entries;
        List<GroupConfig> groups;
        String activeGroup;
        List<String> recentImages;
    }

    private static final class ImageConfigEntry {
        String file;
        boolean enabled = true;
        int x = 0;
        int y = 0;
        boolean hasPosition = false;
        float scale = 1.0f;
        float opacity = 1.0f;
        boolean locked = false;
        boolean pinned = false;
        boolean favorite = false;
        int paletteId = 0;
        List<String> paletteBlocks;
        String note = "";
    }

    private static final class GroupConfig {
        String name;
        int frameColor = DEFAULT_FRAME_COLOR;
        List<GroupEntry> entries;
    }

    private static final class GroupEntry {
        String file;
        boolean enabled = true;
        int x = 0;
        int y = 0;
        boolean hasPosition = false;
        float scale = 1.0f;
        float opacity = 1.0f;
        boolean locked = false;
        boolean pinned = false;
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public enum PickerResult {
        SELECTED,
        CANCELLED,
        FAILED
    }

    public record PickerSelection(PickerResult result, Path path) {}

    public static NativeImage readImageForPreview(Path path) {
        return readImage(path);
    }
}
