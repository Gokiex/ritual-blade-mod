package dev.ritual.ritualblade;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import net.fabricmc.loader.api.FabricLoader;

/**
 * Handles config file IO and in-memory access.
 */
public final class RitualConfigHolder {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("ritual-blade-mod.json");
    private static RitualConfig config = new RitualConfig();

    private RitualConfigHolder() {
    }

    public static RitualConfig get() {
        return config;
    }

    public static void load() {
        if (Files.exists(CONFIG_PATH)) {
            try (Reader reader = Files.newBufferedReader(CONFIG_PATH)) {
                RitualConfig loaded = GSON.fromJson(reader, RitualConfig.class);
                if (loaded != null) {
                    config = loaded;
                }
            } catch (Exception e) {
                RitualBladeMod.LOGGER.error("Failed to read config, using defaults", e);
            }
        }

        validate(config);
        save();
    }

    private static void validate(RitualConfig cfg) {
        cfg.searchRadius = Math.max(8, cfg.searchRadius);
        cfg.chestSearchRadius = Math.max(4, cfg.chestSearchRadius);
        cfg.liftTicks = Math.max(20, cfg.liftTicks);
        cfg.particleCaps = Math.max(20, cfg.particleCaps);
    }

    private static void save() {
        try {
            Files.createDirectories(CONFIG_PATH.getParent());
            try (Writer writer = Files.newBufferedWriter(CONFIG_PATH)) {
                GSON.toJson(config, writer);
            }
        } catch (IOException e) {
            RitualBladeMod.LOGGER.error("Failed to write config", e);
        }
    }
}
