package com.example.thepale.colossus;

import com.example.thepale.worldgen.PaleShape;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import org.jetbrains.annotations.Nullable;

/**
 * Where the Frost Colossus may walk. On its walk past a player: any land, off the coast ({@link #inland}). Otherwise
 * (summoned by a command) its own country: the land of the cold island (in a world with one island, its cold north),
 * well away from the coast, so it never comes near the water's edge or the Pale beyond it.
 */
public final class ColossusRange {
    /** It keeps this far from the coast, in blocks. */
    public static final double COAST_MARGIN = 40;
    /** One island: its range is the land this far north of the middle, as a share of the island's radius. */
    private static final double NORTH_SHARE = 0.4;
    /** A colossus put down by hand outside its range stays within this distance of where it was put. */
    public static final double LEASH = 96;

    private ColossusRange() {}

    /** {@return whether the colossus may walk here}: inland on its island, or near {@code anchor} if it has one. */
    public static boolean allowed(double x, double z, @Nullable BlockPos anchor) {
        PaleShape shape = PaleShape.server();
        if (shape.effectiveRadius(x, z) > shape.landEnd() - COAST_MARGIN) {
            return false;
        }
        if (anchor != null) {
            double dx = x - anchor.getX();
            double dz = z - anchor.getZ();
            return dx * dx + dz * dz <= LEASH * LEASH;
        }
        return home(x, z);
    }

    /** On a walk it keeps this far from the coast, in blocks. */
    public static final double WALK_COAST_MARGIN = 24;

    /** {@return whether the colossus may walk here on its walk past a player}: anywhere on land, off the coast */
    public static boolean inland(double x, double z) {
        PaleShape shape = PaleShape.server();
        return shape.effectiveRadius(x, z) <= shape.landEnd() - WALK_COAST_MARGIN;
    }

    /** {@return whether this is the colossus's own country, the coast aside} */
    public static boolean home(double x, double z) {
        PaleShape shape = PaleShape.server();
        PaleShape.Island island = shape.nearest(x, z);
        return switch (island.kind()) {
            case COLD -> true;
            case SINGLE -> z <= island.centerZ() - NORTH_SHARE * shape.landEnd();
            default -> false;
        };
    }

    /** {@return the middle of the colossus's country: where to look for it, and where it turns back toward} */
    public static double[] heart() {
        PaleShape shape = PaleShape.server();
        PaleShape.Island cold = shape.island(PaleShape.Kind.COLD);
        if (cold != null) {
            return new double[] {cold.centerX(), cold.centerZ()};
        }
        PaleShape.Island only = shape.islands().get(0);
        return new double[] {only.centerX(), only.centerZ() - 0.65 * shape.landEnd()};
    }

    /**
     * {@return a place for the colossus to appear}: dry, fairly level ground as near the heart of its country as there
     * is. Worked out from the world generator, so nothing has to be loaded. Null if no such place turns up.
     *
     * @param turn picks one of several places around the heart (0 is the nearest to it)
     */
    @Nullable
    public static BlockPos findHome(ServerLevel level, int turn) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        int seaLevel = generator.getSeaLevel();
        double[] heart = heart();
        int found = 0;
        // Outward in a square spiral of 32-block steps.
        for (int ring = 0; ring <= 14; ring++) {
            for (int i = -ring; i <= ring; i++) {
                for (int j = -ring; j <= ring; j++) {
                    if (Math.max(Math.abs(i), Math.abs(j)) != ring) {
                        continue;
                    }
                    int x = (int) heart[0] + i * 32;
                    int z = (int) heart[1] + j * 32;
                    if (!allowed(x, z, null)) {
                        continue;
                    }
                    int ground = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                    if (ground < seaLevel || ground > seaLevel + 70) {
                        continue;
                    }
                    boolean level4 = true;
                    for (int corner = 0; corner < 4 && level4; corner++) {
                        int cornerX = x + ((corner & 1) == 0 ? -8 : 8);
                        int cornerZ = z + ((corner & 2) == 0 ? -8 : 8);
                        int height = generator.getBaseHeight(cornerX, cornerZ, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                        level4 = Math.abs(height - ground) <= 3 && height >= seaLevel;
                    }
                    if (level4 && found++ >= turn) {
                        return new BlockPos(x, ground, z);
                    }
                }
            }
        }
        return null;
    }
}
