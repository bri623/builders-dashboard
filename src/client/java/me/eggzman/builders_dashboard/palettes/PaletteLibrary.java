package me.eggzman.builders_dashboard.palettes;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class PaletteLibrary {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_DIR =
            FabricLoader.getInstance().getConfigDir().resolve("builders_dashboard");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("palettes.json");
    private static final int MAX_RECENT = 10;
    private static boolean loaded;
    private static final List<PaletteSnapshot> FAVORITES = new ArrayList<>();
    private static final List<PaletteSnapshot> RECENTS = new ArrayList<>();

    private PaletteLibrary() {}

    public static List<PaletteSnapshot> getFavorites() {
        ensureLoaded();
        return new ArrayList<>(FAVORITES);
    }

    public static List<PaletteSnapshot> getRecents() {
        ensureLoaded();
        return new ArrayList<>(RECENTS);
    }

    public static boolean isFavorite(int id) {
        ensureLoaded();
        return findIndex(FAVORITES, id) >= 0;
    }

    public static void toggleFavorite(PaletteSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        ensureLoaded();
        int idx = findIndex(FAVORITES, snapshot.id);
        if (idx >= 0) {
            FAVORITES.remove(idx);
        } else {
            FAVORITES.add(0, snapshot);
        }
        saveConfig();
    }

    public static void markUsed(PaletteSnapshot snapshot) {
        if (snapshot == null) {
            return;
        }
        ensureLoaded();
        int idx = findIndex(RECENTS, snapshot.id);
        if (idx >= 0) {
            RECENTS.remove(idx);
        }
        RECENTS.add(0, snapshot);
        while (RECENTS.size() > MAX_RECENT) {
            RECENTS.remove(RECENTS.size() - 1);
        }
        int favIdx = findIndex(FAVORITES, snapshot.id);
        if (favIdx >= 0) {
            FAVORITES.set(favIdx, snapshot);
        }
        saveConfig();
    }

    private static void ensureLoaded() {
        if (loaded) {
            return;
        }
        loaded = true;
        loadConfig();
    }

    private static int findIndex(List<PaletteSnapshot> list, int id) {
        for (int i = 0; i < list.size(); i++) {
            PaletteSnapshot snap = list.get(i);
            if (snap != null && snap.id == id) {
                return i;
            }
        }
        return -1;
    }

    private static void loadConfig() {
        FAVORITES.clear();
        RECENTS.clear();
        if (!Files.exists(CONFIG_FILE)) {
            return;
        }
        try {
            String json = Files.readString(CONFIG_FILE);
            PaletteConfig cfg = GSON.fromJson(json, PaletteConfig.class);
            if (cfg == null) {
                return;
            }
            if (cfg.favorites != null) {
                FAVORITES.addAll(cfg.favorites);
            }
            if (cfg.recents != null) {
                RECENTS.addAll(cfg.recents);
            }
        } catch (IOException | JsonSyntaxException ignored) {
            // best-effort
        }
    }

    private static void saveConfig() {
        PaletteConfig cfg = new PaletteConfig();
        cfg.favorites = new ArrayList<>(FAVORITES);
        cfg.recents = new ArrayList<>(RECENTS);
        try {
            Files.createDirectories(CONFIG_DIR);
            Files.writeString(CONFIG_FILE, GSON.toJson(cfg));
        } catch (IOException ignored) {
            // best-effort
        }
    }

    public static final class PaletteSnapshot {
        public int id;
        public int likes;
        public String time_ago;
        public String blockOne;
        public String blockTwo;
        public String blockThree;
        public String blockFour;
        public String blockFive;
        public String blockSix;

        public PaletteSnapshot() {}
    }

    private static final class PaletteConfig {
        List<PaletteSnapshot> favorites;
        List<PaletteSnapshot> recents;
    }
}
