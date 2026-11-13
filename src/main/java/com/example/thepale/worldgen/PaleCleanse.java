package com.example.thepale.worldgen;

import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/**
 * Whether the Pale has been cleansed in this world (the finale), as world generation and the island rules need it:
 * read on worker threads at any moment, so kept here rather than in the saved data it comes from
 * (finale.FinaleState).
 *
 * <p>Once cleansed, the Pale biome is gone, and the world past the explored sea turns back into the seed's own
 * terrain: up to {@link #seaEnd()} (in the units of {@link PaleShape#effectiveRadius}) chunks generate as open sea,
 * exactly as before, so they meet the chunks that already exist without a seam; from there to {@link #landFull()}
 * the pull toward the ocean eases off; beyond it the world is what the seed would have been without the mod.
 */
public final class PaleCleanse {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile boolean cleansed;
    private static volatile double seaEnd = Double.MAX_VALUE;
    private static volatile double landFull = Double.MAX_VALUE;

    private PaleCleanse() {}

    public static boolean cleansed() {
        return cleansed;
    }

    public static double seaEnd() {
        return seaEnd;
    }

    public static double landFull() {
        return landFull;
    }

    public static void set(boolean isCleansed, double newSeaEnd, double newLandFull) {
        // The radii first: a worker reading in between sees either "not cleansed" or the full new state.
        if (isCleansed) {
            seaEnd = newSeaEnd;
            landFull = Math.max(newLandFull, newSeaEnd + 1);
        }
        cleansed = isCleansed;
    }

    /** {@return how much of the seed's own terrain a place this far out gets, 0 (the mod's sea) to 1 (new lands)} */
    public static double newLand(double effectiveRadius) {
        if (!cleansed || effectiveRadius <= seaEnd) {
            return 0;
        }
        double t = Mth.clamp((effectiveRadius - seaEnd) / (landFull - seaEnd), 0, 1);
        return t * t * (3 - 2 * t);
    }

    public static double newLand(double x, double z) {
        return cleansed ? newLand(PaleShape.server().effectiveRadius(x, z)) : 0;
    }

    /** {@return whether this place is out in the new lands, where the islands' rules no longer hold} */
    public static boolean inNewLands(double x, double z) {
        return newLand(x, z) > 0;
    }

    /**
     * Reads the state straight from the world's saved data file, before the levels exist: structure placement and the
     * spawn search already generate terrain while the Overworld is being created, earlier than its saved data loads.
     */
    static void onServerAboutToStart(MinecraftServer server) {
        cleansed = false;
        seaEnd = Double.MAX_VALUE;
        landFull = Double.MAX_VALUE;
        Path file = server.getWorldPath(LevelResource.ROOT).resolve("data").resolve("thepale_finale.dat");
        if (!Files.exists(file)) {
            return;
        }
        try {
            CompoundTag data = NbtIo.readCompressed(file, NbtAccounter.unlimitedHeap()).getCompound("data");
            if (data.getBoolean("cleansed")) {
                set(true, data.getDouble("seaEnd"), data.getDouble("landFull"));
                LOGGER.info("[thepale] The Pale is cleansed in this world: open sea to {}, the seed's own lands from {}", Math.round(seaEnd), Math.round(landFull));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[thepale] Could not read {}; treating the Pale as not cleansed until the world has loaded", file, e);
        }
    }
}
