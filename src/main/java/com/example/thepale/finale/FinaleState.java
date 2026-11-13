package com.example.thepale.finale;

import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Where the finale stands in this world, saved with the Overworld (data/thepale_finale.dat): the invasion, the fight
 * and whether the Pale has been cleansed. A world without the file is a world where nothing has happened yet.
 */
public final class FinaleState extends SavedData {
    public static final String NAME = "thepale_finale";

    public enum Phase {
        /** Nothing has been started. */
        NONE,
        /** The boss is on its way through the Pale. */
        APPROACH,
        /** It stands off the shore until night falls and a player is near. */
        WAITING,
        /** It is ashore. */
        BATTLE,
        /** Everybody died or left: it went back into the sea and returns the next night. */
        RETREATED,
        /** It is dead. */
        DEFEATED
    }

    Phase phase = Phase.NONE;
    /** Game-time ticks of the approach so far. */
    long progress;
    /** The Overworld's day time at the last tick, to count time skipped by sleeping or /time add. */
    long lastDayTime = -1;
    /** Where it started, out in the Pale. */
    double startX;
    double startZ;
    /** Where it waits, just off the shore. */
    double offshoreX;
    double offshoreZ;
    /** The landing point on the shore: the middle of the arena. */
    double landX;
    double landZ;
    /** Whether the landing point has been picked again since the silhouette showed. */
    boolean retargeted;
    /** The last stage announced to the players (see Invasion). */
    int announced = -1;
    /** The night (see Invasion#night) it retreated in. */
    long retreatNight;
    @Nullable
    UUID boss;

    boolean cleansed;
    /** Game time of the cleansing, for the fade on clients. */
    long cleansedAt;
    /** Effective radius (see PaleShape) up to which new chunks stay open sea. */
    double seaEnd;
    /** Effective radius from which new chunks are the seed's own terrain. */
    double landFull;

    public static FinaleState get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(new Factory<>(FinaleState::new, FinaleState::load), NAME);
    }

    public Phase phase() {
        return phase;
    }

    public boolean cleansed() {
        return cleansed;
    }

    static FinaleState load(CompoundTag tag, HolderLookup.Provider registries) {
        FinaleState state = new FinaleState();
        try {
            state.phase = Phase.valueOf(tag.getString("phase"));
        } catch (IllegalArgumentException e) {
            state.phase = Phase.NONE;
        }
        state.progress = tag.getLong("progress");
        state.lastDayTime = tag.contains("lastDayTime") ? tag.getLong("lastDayTime") : -1;
        state.startX = tag.getDouble("startX");
        state.startZ = tag.getDouble("startZ");
        state.offshoreX = tag.getDouble("offshoreX");
        state.offshoreZ = tag.getDouble("offshoreZ");
        state.landX = tag.getDouble("landX");
        state.landZ = tag.getDouble("landZ");
        state.retargeted = tag.getBoolean("retargeted");
        state.announced = tag.contains("announced") ? tag.getInt("announced") : -1;
        state.retreatNight = tag.getLong("retreatNight");
        state.boss = tag.hasUUID("boss") ? tag.getUUID("boss") : null;
        state.cleansed = tag.getBoolean("cleansed");
        state.cleansedAt = tag.getLong("cleansedAt");
        state.seaEnd = tag.getDouble("seaEnd");
        state.landFull = tag.getDouble("landFull");
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putString("phase", phase.name());
        tag.putLong("progress", progress);
        tag.putLong("lastDayTime", lastDayTime);
        tag.putDouble("startX", startX);
        tag.putDouble("startZ", startZ);
        tag.putDouble("offshoreX", offshoreX);
        tag.putDouble("offshoreZ", offshoreZ);
        tag.putDouble("landX", landX);
        tag.putDouble("landZ", landZ);
        tag.putBoolean("retargeted", retargeted);
        tag.putInt("announced", announced);
        tag.putLong("retreatNight", retreatNight);
        if (boss != null) {
            tag.putUUID("boss", boss);
        }
        tag.putBoolean("cleansed", cleansed);
        tag.putLong("cleansedAt", cleansedAt);
        tag.putDouble("seaEnd", seaEnd);
        tag.putDouble("landFull", landFull);
        return tag;
    }
}
