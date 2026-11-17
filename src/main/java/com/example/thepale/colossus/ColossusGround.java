package com.example.thepale.colossus;

import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The ground the Frost Colossus walks on: what its feet stand on, on the server and on the client alike.
 *
 * <p>Trees don't count (it wades through a forest like through grass), nor does water (it stands on the bottom, or
 * where the sea is deep, wades as if the bottom were {@link #MAX_DEPTH} blocks under the surface).
 * Only columns of chunks that are already loaded are looked at, so asking never loads or generates anything; the
 * answer for a column that isn't there is {@link Double#NaN}.
 */
public final class ColossusGround {
    /** The corners of a foot, from its middle, in blocks. */
    private static final double FOOT_HALF_WIDTH = 1.25;
    private static final double FOOT_HALF_LENGTH = 1.75;
    /** Out at sea it wades no deeper than this, in blocks: the water comes up to its knees, the rest of it stands clear. */
    public static final int MAX_DEPTH = 5;

    private ColossusGround() {}

    /** {@return the top of the ground in this column, or NaN where no chunk is loaded} */
    public static double height(Level level, double x, double z) {
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        // Only a chunk that is here already: this never waits for one to load.
        LevelChunk chunk = level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4);
        if (chunk == null) {
            return Double.NaN;
        }
        // The one surface both sides track; it counts leaves and water, which are skipped below.
        int top = chunk.getHeight(Heightmap.Types.MOTION_BLOCKING, blockX & 15, blockZ & 15);
        int bottom = level.getMinBuildHeight();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int surface = Integer.MIN_VALUE;
        for (int y = top; y >= bottom; y--) {
            BlockState state = chunk.getBlockState(pos.set(blockX, y, blockZ));
            if (surface == Integer.MIN_VALUE && state.getFluidState().is(FluidTags.WATER)) {
                surface = y + 1;
            }
            if (isGround(state)) {
                // In deep water it wades, as if on a bottom no deeper than this under the surface.
                return surface != Integer.MIN_VALUE ? Math.max(y + 1, surface - MAX_DEPTH) : y + 1;
            }
        }
        return surface != Integer.MIN_VALUE ? surface - MAX_DEPTH : bottom;
    }

    /**
     * {@return the ground a foot stands on when its middle is here}: the highest of its middle and its four corners,
     * so it rests on top of a bump instead of sinking into it. NaN where no chunk is loaded.
     */
    public static double underFoot(Level level, double x, double z, float yaw) {
        double sin = Math.sin(Math.toRadians(yaw));
        double cos = Math.cos(Math.toRadians(yaw));
        double best = height(level, x, z);
        if (Double.isNaN(best)) {
            return best;
        }
        for (int corner = 0; corner < 4; corner++) {
            double across = (corner & 1) == 0 ? -FOOT_HALF_WIDTH : FOOT_HALF_WIDTH;
            double along = (corner & 2) == 0 ? -FOOT_HALF_LENGTH : FOOT_HALF_LENGTH;
            // Forward is (-sin, cos) for this yaw, and across is to its side.
            double height = height(level, x - sin * along + cos * across, z + cos * along + sin * across);
            if (!Double.isNaN(height) && height > best) {
                best = height;
            }
        }
        return best;
    }

    /** {@return how many blocks of water stand over the ground of this column} (0 where no chunk is loaded) */
    public static int waterDepth(Level level, double x, double z) {
        double ground = height(level, x, z);
        int blockX = Mth.floor(x);
        int blockZ = Mth.floor(z);
        LevelChunk chunk = level.getChunkSource().getChunkNow(blockX >> 4, blockZ >> 4);
        if (Double.isNaN(ground) || chunk == null) {
            return 0;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int depth = 0;
        for (int y = (int) ground; depth < 16 && chunk.getFluidState(pos.set(blockX, y, blockZ)).is(FluidTags.WATER); y++) {
            depth++;
        }
        return depth;
    }

    private static boolean isGround(BlockState state) {
        return state.blocksMotion() && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS);
    }
}
