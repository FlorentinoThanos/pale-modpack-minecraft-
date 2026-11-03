package com.example.thepale.worldgen;

import com.example.thepale.mixin.StructurePlacementAccessor;
import java.util.Map;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * A random spread placement with some regions' tries moved to chosen chunks ({@link StructurePins}). Each region still
 * gets exactly one try, so everything that finds structures through the placement (generation, /locate, explorer
 * maps) sees the moved ones.
 */
public final class PinnedSpreadPlacement extends RandomSpreadStructurePlacement {
    /** Region (as {@link ChunkPos#asLong}) to the chunk its try is moved to. */
    private final Map<Long, ChunkPos> pins;

    /** A copy of {@code base} with the given spacing and separation and these regions' tries moved. */
    public PinnedSpreadPlacement(RandomSpreadStructurePlacement base, int spacing, int separation, Map<Long, ChunkPos> pins) {
        super(
                settings(base).thepale$locateOffset(),
                settings(base).thepale$frequencyReductionMethod(),
                settings(base).thepale$frequency(),
                settings(base).thepale$salt(),
                settings(base).thepale$exclusionZone(),
                spacing,
                separation,
                base.spreadType());
        this.pins = Map.copyOf(pins);
    }

    private static StructurePlacementAccessor settings(RandomSpreadStructurePlacement placement) {
        return (StructurePlacementAccessor) placement;
    }

    @Override
    public ChunkPos getPotentialStructureChunk(long seed, int chunkX, int chunkZ) {
        ChunkPos pinned = pins.get(ChunkPos.asLong(Math.floorDiv(chunkX, spacing()), Math.floorDiv(chunkZ, spacing())));
        return pinned != null ? pinned : super.getPotentialStructureChunk(seed, chunkX, chunkZ);
    }
}
