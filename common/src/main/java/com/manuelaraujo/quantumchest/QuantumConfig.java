package com.manuelaraujo.quantumchest;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.manuelaraujo.quantumchest.block.ChestTier;
import net.minecraft.server.MinecraftServer;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Server settings, in config/quantumchest.json. Read when a world loads; the
 * file is rewritten with every key so new settings show up after an update.
 * Anything missing or out of range falls back to the default.
 *
 * Clients don't read it: the chest screen gets the numbers it shows from the
 * server, so a modpack can change them without touching anyone's game.
 */
public final class QuantumConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final int TIERS = ChestTier.values().length;

    private static final long[] DEFAULT_CAPACITY = {10_000L, 100_000L, 1_000_000L, 100_000_000L, -1};
    private static final int[] DEFAULT_MAGNET = {3, 3, 5, 8, 8};
    private static final int[] DEFAULT_IO = {16, 16, 64, 256, 1280};
    // Furnaces working side by side. Each item takes as long as in a vanilla furnace
    private static final int[] DEFAULT_SMELT = {1, 1, 1, 4, 16};
    private static final int DEFAULT_TERMINAL_RANGE = 64;

    private static long[] capacity = DEFAULT_CAPACITY.clone();
    private static int[] magnet = DEFAULT_MAGNET.clone();
    private static int[] io = DEFAULT_IO.clone();
    private static int[] smelt = DEFAULT_SMELT.clone();
    private static int terminalRange = DEFAULT_TERMINAL_RANGE;
    private static volatile MinecraftServer loadedFor;

    private QuantumConfig() {
    }

    /** Items a chest of this tier holds. Long.MAX_VALUE means no limit. */
    public static long capacity(ChestTier tier) {
        long value = capacity[tier.ordinal()];
        return value < 0 ? Long.MAX_VALUE : value;
    }

    public static int magnetRange(ChestTier tier) {
        return magnet[tier.ordinal()];
    }

    public static int ioPerSecond(ChestTier tier) {
        return io[tier.ordinal()];
    }

    /** How many items the smelter cooks at the same time. */
    public static int smeltFurnaces(ChestTier tier) {
        return smelt[tier.ordinal()];
    }

    /** How far the terminal reaches from an Advanced chest. Elite and up cover the whole dimension. */
    public static int terminalRange() {
        return terminalRange;
    }

    /** Reads the file once per server start. Cheap to call often. */
    public static void ensureLoaded(MinecraftServer server) {
        if (loadedFor == server) return;
        loadSync(server);
    }

    private static synchronized void loadSync(MinecraftServer server) {
        if (loadedFor == server) return;
        load(server.getServerDirectory().resolve("config").resolve(QuantumChest.MOD_ID + ".json"));
        loadedFor = server;
    }

    private static void load(Path file) {
        JsonObject json = new JsonObject();
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                if (parsed.isJsonObject()) json = parsed.getAsJsonObject();
            } catch (IOException | RuntimeException e) {
                QuantumChest.LOG.warn("Could not read {}, using the defaults: {}", file, e.getMessage());
            }
        }
        capacity = readLongs(json, "capacity", DEFAULT_CAPACITY, 1, Long.MAX_VALUE);
        magnet = readInts(json, "magnet_range", DEFAULT_MAGNET, 1, 16);
        io = readInts(json, "io_items_per_second", DEFAULT_IO, 1, 100_000);
        smelt = readInts(json, "smelt_furnaces", DEFAULT_SMELT, 1, 64);
        terminalRange = readInt(json, "terminal_range", DEFAULT_TERMINAL_RANGE, 8, 100_000);
        write(file);
    }

    private static void write(Path file) {
        JsonObject out = new JsonObject();
        out.addProperty("_help", "Per tier: basic, reinforced, advanced, elite, quantum. Capacity -1 means unlimited. "
                + "Magnet range is in blocks (max 16), the I/O speed is items per second, smelt_furnaces is how many items "
                + "the smelter cooks at once (each one takes as long as in a furnace). "
                + "Terminal range is in blocks from an Advanced chest. Changes apply when the world loads.");
        JsonObject cap = new JsonObject();
        for (ChestTier tier : ChestTier.values()) {
            long value = capacity[tier.ordinal()];
            cap.addProperty(tier.getSerializedName(), value == Long.MAX_VALUE ? -1 : value);
        }
        out.add("capacity", cap);
        out.add("magnet_range", ints(magnet));
        out.add("io_items_per_second", ints(io));
        out.add("smelt_furnaces", ints(smelt));
        out.addProperty("terminal_range", terminalRange);
        try {
            Files.createDirectories(file.getParent());
            try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
                GSON.toJson(out, writer);
            }
        } catch (IOException e) {
            QuantumChest.LOG.warn("Could not write {}: {}", file, e.getMessage());
        }
    }

    private static JsonObject ints(int[] values) {
        JsonObject object = new JsonObject();
        for (ChestTier tier : ChestTier.values()) object.addProperty(tier.getSerializedName(), values[tier.ordinal()]);
        return object;
    }

    private static long[] readLongs(JsonObject json, String key, long[] defaults, long min, long max) {
        long[] values = defaults.clone();
        if (!json.has(key) || !json.get(key).isJsonObject()) return values;
        JsonObject object = json.getAsJsonObject(key);
        for (ChestTier tier : ChestTier.values()) {
            try {
                if (!object.has(tier.getSerializedName())) continue;
                long value = object.get(tier.getSerializedName()).getAsLong();
                values[tier.ordinal()] = value < 0 ? -1 : Math.max(min, Math.min(max, value));
            } catch (RuntimeException ignored) {
                // Not a number: keep the default
            }
        }
        return values;
    }

    private static int[] readInts(JsonObject json, String key, int[] defaults, int min, int max) {
        int[] values = defaults.clone();
        if (!json.has(key) || !json.get(key).isJsonObject()) return values;
        JsonObject object = json.getAsJsonObject(key);
        for (ChestTier tier : ChestTier.values()) {
            try {
                if (object.has(tier.getSerializedName())) {
                    values[tier.ordinal()] = Math.max(min, Math.min(max, object.get(tier.getSerializedName()).getAsInt()));
                }
            } catch (RuntimeException ignored) {
                // Not a number: keep the default
            }
        }
        return values;
    }

    private static int readInt(JsonObject json, String key, int fallback, int min, int max) {
        try {
            return json.has(key) ? Math.max(min, Math.min(max, json.get(key).getAsInt())) : fallback;
        } catch (RuntimeException e) {
            return fallback;
        }
    }

    /** Tier count check, so a new tier can't silently read past the arrays. */
    static {
        if (DEFAULT_CAPACITY.length != TIERS || DEFAULT_MAGNET.length != TIERS || DEFAULT_IO.length != TIERS
                || DEFAULT_SMELT.length != TIERS) {
            throw new IllegalStateException("QuantumConfig defaults don't match the chest tiers");
        }
    }
}
