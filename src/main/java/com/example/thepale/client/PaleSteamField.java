package com.example.thepale.client;

import com.example.thepale.worldgen.PaleShape;
import com.mojang.logging.LogUtils;
import java.nio.FloatBuffer;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * A map of the Pale around the camera for the steam shader ({@link PaleSteam}): how far out every spot is (the
 * effective radius, {@link PaleShape#effectiveRadius}), and how far sideways it is to the nearest spot where there can
 * be steam, so the shader can jump straight across an island to where the fog starts. Worked out off the render thread
 * whenever the camera has moved far, the islands have changed or the fog radii have.
 */
final class PaleSteamField {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Cells per side, and the size of one: 8192 blocks across, centred on the camera. */
    static final int SIZE = 512;
    static final int CELL = 16;
    /** How far the camera may get from the map's centre before it is made again, around the new spot. */
    private static final double RECENTER = 1024;
    /** Distances are stored as half floats; nothing is further than this. */
    private static final float MAX_CLEAR = 60000;

    /** One finished map. Values are interleaved: how far past the fog's start, then the distance to possible fog. */
    private record Data(PaleShape shape, double fogStart, double edge, double originX, double originZ, float[] values) {
        double centerX() {
            return originX + SIZE * CELL / 2.0;
        }

        double centerZ() {
            return originZ + SIZE * CELL / 2.0;
        }
    }

    private static int texture;
    @Nullable
    private static Data current;
    @Nullable
    private static CompletableFuture<Data> pending;
    private static boolean loggedFailure;

    private PaleSteamField() {}

    /**
     * Makes sure the map is ready for a camera here (render thread). A stale map is made again in the background; one
     * that is only off-centre is used until then, but one of other islands or radii, or too far off, is not.
     *
     * @param fogStart the effective radius where the fog starts (innerRadius - the approach)
     * @param edge how far before that the steam's ragged edge can reach
     * @return whether there is a map to use this frame
     */
    static boolean prepare(double cameraX, double cameraZ, PaleShape shape, double fogStart, double edge) {
        CompletableFuture<Data> building = pending;
        if (building != null && building.isDone()) {
            pending = null;
            Data made = building.getNow(null);
            if (made != null) {
                upload(made);
            }
        }
        Data map = current;
        boolean sameWorld = map != null && map.shape == shape && map.fogStart == fogStart && map.edge == edge;
        double offX = map == null ? Double.MAX_VALUE : Math.abs(cameraX - map.centerX());
        double offZ = map == null ? Double.MAX_VALUE : Math.abs(cameraZ - map.centerZ());
        if ((!sameWorld || offX > RECENTER || offZ > RECENTER) && pending == null) {
            double originX = (Math.floor(cameraX / CELL) - SIZE / 2) * CELL;
            double originZ = (Math.floor(cameraZ / CELL) - SIZE / 2) * CELL;
            pending = CompletableFuture.supplyAsync(() -> build(shape, fogStart, edge, originX, originZ), Util.backgroundExecutor())
                    .exceptionally(e -> {
                        if (!loggedFailure) {
                            loggedFailure = true;
                            LOGGER.error("[thepale] Could not map the Pale for the steam", e);
                        }
                        return null;
                    });
        }
        // Off-centre is fine while the map still reaches well past anything the rays can get to through clear air.
        double usable = SIZE * CELL / 2.0 - RECENTER * 2;
        return sameWorld && offX < usable && offZ < usable;
    }

    static int texture() {
        return texture;
    }

    /** {@return x and z of the map's corner, in blocks} */
    static double originX() {
        return current == null ? 0 : current.originX;
    }

    static double originZ() {
        return current == null ? 0 : current.originZ;
    }

    private static Data build(PaleShape shape, double fogStart, double edge, double originX, double originZ) {
        float[] values = new float[SIZE * SIZE * 2];
        // Squared distances, in cells, to the nearest cell where steam is possible (0 there).
        double[] distance = new double[SIZE * SIZE];
        double far = 1e12;
        for (int j = 0; j < SIZE; j++) {
            double z = originZ + (j + 0.5) * CELL;
            for (int i = 0; i < SIZE; i++) {
                double x = originX + (i + 0.5) * CELL;
                double past = shape.effectiveRadius(x, z) - fogStart;
                values[(j * SIZE + i) * 2] = (float) Mth.clamp(past, -MAX_CLEAR, MAX_CLEAR);
                distance[j * SIZE + i] = past >= -edge ? 0 : far;
            }
        }
        distanceTransform(distance);
        for (int j = 0; j < SIZE; j++) {
            for (int i = 0; i < SIZE; i++) {
                // Past the map's edge nothing is known, so never promise more clear air than there is map.
                int toEdge = Math.min(Math.min(i, SIZE - 1 - i), Math.min(j, SIZE - 1 - j));
                double clear = Math.min(Math.sqrt(distance[j * SIZE + i]), toEdge) * CELL;
                values[(j * SIZE + i) * 2 + 1] = (float) Math.min(clear, MAX_CLEAR);
            }
        }
        return new Data(shape, fogStart, edge, originX, originZ, values);
    }

    /** Exact squared Euclidean distance transform (Felzenszwalb and Huttenlocher), rows then columns, in place. */
    private static void distanceTransform(double[] grid) {
        double[] line = new double[SIZE];
        double[] out = new double[SIZE];
        int[] hull = new int[SIZE];
        double[] bounds = new double[SIZE + 1];
        for (int j = 0; j < SIZE; j++) {
            System.arraycopy(grid, j * SIZE, line, 0, SIZE);
            transformLine(line, out, hull, bounds);
            System.arraycopy(out, 0, grid, j * SIZE, SIZE);
        }
        for (int i = 0; i < SIZE; i++) {
            for (int j = 0; j < SIZE; j++) {
                line[j] = grid[j * SIZE + i];
            }
            transformLine(line, out, hull, bounds);
            for (int j = 0; j < SIZE; j++) {
                grid[j * SIZE + i] = out[j];
            }
        }
    }

    /** The 1D squared distance transform of {@code f} into {@code d}: the lower envelope of parabolas. */
    private static void transformLine(double[] f, double[] d, int[] hull, double[] bounds) {
        int k = 0;
        hull[0] = 0;
        bounds[0] = Double.NEGATIVE_INFINITY;
        bounds[1] = Double.POSITIVE_INFINITY;
        for (int q = 1; q < SIZE; q++) {
            double s = intersection(f, q, hull[k]);
            while (s <= bounds[k]) {
                k--;
                s = intersection(f, q, hull[k]);
            }
            k++;
            hull[k] = q;
            bounds[k] = s;
            bounds[k + 1] = Double.POSITIVE_INFINITY;
        }
        k = 0;
        for (int q = 0; q < SIZE; q++) {
            while (bounds[k + 1] < q) {
                k++;
            }
            double dq = q - hull[k];
            d[q] = dq * dq + f[hull[k]];
        }
    }

    private static double intersection(double[] f, int q, int p) {
        return ((f[q] + (double) q * q) - (f[p] + (double) p * p)) / (2.0 * q - 2.0 * p);
    }

    private static void upload(Data data) {
        current = data;
        int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + PaleSteam.SCRATCH_UNIT);
        int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int previousUnpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        if (texture == 0) {
            texture = GL11.glGenTextures();
        }
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        FloatBuffer buffer = MemoryUtil.memAllocFloat(data.values.length);
        try {
            buffer.put(data.values).flip();
            PaleSteam.withPlainUnpacking(() -> {
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG16F, SIZE, SIZE, 0, GL30.GL_RG, GL11.GL_FLOAT, buffer);
            });
        } finally {
            MemoryUtil.memFree(buffer);
        }
        GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, previousUnpack);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
        GL13.glActiveTexture(previousUnit);
    }
}
