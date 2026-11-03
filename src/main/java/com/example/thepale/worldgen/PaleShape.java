package com.example.thepale.worldgen;

import com.example.thepale.config.CommonConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.BiomeManager;
import net.minecraft.world.level.storage.LevelResource;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Where the land is: the world's islands, each with its own centre, size and ragged coast. Everything in the mod that
 * asks "how far out is this?" uses {@link #effectiveRadius}: the distance past the coast of the nearest island,
 * expressed in the config's radii (landEndRadius at the coast, paleBiomeRadius 50 blocks past it, and so on). So the
 * Pale, the poison and the fog wall follow every coast at the same distance, whatever the island's size.
 *
 * <p>New worlds get three islands: a mild starter island at (0, 0), a hot one to the south and a cold one to the
 * north (see {@link Kind}). The layout is fixed when the world is created and saved with it (thepale-world.json), and
 * the server sends it to every client that joins ({@link ShapePayload}).
 */
public final class PaleShape {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String FILE = "thepale-world.json";

    /** Directions per turn in each coast's lookup table; in between, the factor is interpolated. */
    private static final int STEPS = 4096;
    /** The waves around a coast: the slowest goes once around, the fastest this many times (bays ~250 blocks wide). */
    private static final int WAVES = 24;

    // The three-island layout.
    private static final double MIN_RADIUS = 1000;
    private static final double MAX_RADIUS = 1500;
    private static final double MIN_DISTANCE = 3500;
    private static final double MAX_DISTANCE = 4500;
    private static final double MAX_SIDEWAYS = 500;
    /** Open water always left between two coasts, so the poison between islands is never thin. */
    private static final double MIN_GAP = 1000;

    /** What an island is like. */
    public enum Kind {
        /** The only island, with climate zones across it (cold north, hot south, wet west, dry east). */
        SINGLE,
        /** The starter island at (0, 0): temperate. */
        MILD,
        /** The southern island: hot, wet to the west (jungle) and dry to the east (desert). */
        HOT,
        /** The northern island: frozen. */
        COLD
    }

    /** One island. Distances and angles are measured from its centre. */
    public static final class Island {
        private final Kind kind;
        private final double centerX;
        private final double centerZ;
        private final double radius;
        private final long seed;
        private final double roughness;
        /** Worlds from 1.6.0: every radius scaled by the coast factor, instead of measured from the coast. */
        private final boolean scaled;
        private final float[] factors;
        private final double minFactor;
        private final double maxFactor;

        Island(Kind kind, double centerX, double centerZ, double radius, long seed, double roughness, boolean scaled) {
            this.kind = kind;
            this.centerX = centerX;
            this.centerZ = centerZ;
            this.radius = radius;
            this.seed = seed;
            this.roughness = roughness;
            this.scaled = scaled;
            this.factors = new float[STEPS + 1];
            if (roughness <= 0) {
                Arrays.fill(factors, 1f);
                minFactor = 1;
                maxFactor = 1;
                return;
            }
            Random random = new Random(seed);
            double[] weights = new double[WAVES + 1];
            double[] phases = new double[WAVES + 1];
            for (int k = 1; k <= WAVES; k++) {
                // Slower waves are bigger; the once-around wave is held back so the island isn't simply pushed aside.
                weights[k] = (0.5 + 0.5 * random.nextDouble()) / Math.pow(k, 0.9) * (k == 1 ? 0.5 : 1.0);
                phases[k] = random.nextDouble() * Mth.TWO_PI;
            }
            double[] raw = new double[STEPS + 1];
            double largest = 0;
            for (int i = 0; i <= STEPS; i++) {
                double angle = i * Mth.TWO_PI / STEPS;
                double sum = 0;
                for (int k = 1; k <= WAVES; k++) {
                    sum += weights[k] * Math.sin(k * angle + phases[k]);
                }
                raw[i] = sum;
                largest = Math.max(largest, Math.abs(sum));
            }
            double min = Double.MAX_VALUE;
            double max = 0;
            for (int i = 0; i <= STEPS; i++) {
                double factor = 1 + roughness * raw[i] / largest;
                factors[i] = (float) factor;
                min = Math.min(min, factor);
                max = Math.max(max, factor);
            }
            minFactor = min;
            maxFactor = max;
        }

        public Kind kind() {
            return kind;
        }

        public double centerX() {
            return centerX;
        }

        public double centerZ() {
            return centerZ;
        }

        /** The coast's average distance from the centre. */
        public double radius() {
            return radius;
        }

        /** {@return how far the coast reaches in the direction of this angle, as a share of the radius} */
        public double factor(double angle) {
            if (roughness <= 0) {
                return 1;
            }
            double position = (angle / Mth.TWO_PI - Math.floor(angle / Mth.TWO_PI)) * STEPS;
            int index = (int) position;
            return Mth.lerp(position - index, factors[index], factors[index + 1]);
        }

        /** {@return how far out a point is from this island, in config radii; see {@link PaleShape#effectiveRadius}} */
        double effective(double x, double z, double landEnd) {
            double dx = x - centerX;
            double dz = z - centerZ;
            double distance = Math.sqrt(dx * dx + dz * dz);
            double factor = distance == 0 ? 1 : factor(Math.atan2(dz, dx));
            return scaled ? distance / factor : landEnd + distance - radius * factor;
        }

        /**
         * {@return the distance from the centre, in the direction of this angle, of the line at {@code effective}
         * (in config radii)}: e.g. where the fog wall's layer of that radius stands.
         */
        public double distanceAt(double effective, double angle, double landEnd) {
            double factor = factor(angle);
            return scaled ? effective * factor : radius * factor + effective - landEnd;
        }

        /** {@return the farthest any point of the line at {@code effective} gets from the centre} */
        public double reach(double effective, double landEnd) {
            return scaled ? effective * maxFactor : radius * maxFactor + effective - landEnd;
        }

        /** {@return the nearest any point of the line at {@code effective} gets to the centre} */
        public double nearest(double effective, double landEnd) {
            return Math.max(0, scaled ? effective * minFactor : radius * minFactor + effective - landEnd);
        }

        @Override
        public String toString() {
            return String.format("%s island at (%.0f, %.0f), radius %.0f", kind.name().toLowerCase(java.util.Locale.ROOT), centerX, centerZ, radius);
        }
    }

    private static final PaleShape CIRCLE_SHAPE = new PaleShape(List.of(new Island(Kind.SINGLE, 0, 0, 1250, 0, 0, true)), 1250);

    private static volatile PaleShape server = CIRCLE_SHAPE;
    private static volatile PaleShape client = CIRCLE_SHAPE;

    /** How this world's layout is made from its seed, kept for when the seed search changes the seed. */
    private enum Layout {
        /** Worlds from before 1.6.0. */
        CIRCLE,
        /** Worlds from 1.6.0: one ragged island, radii scaled by the coast. */
        RAGGED_ONE,
        /** One island, radii measured from the coast. */
        ONE,
        THREE
    }

    private static volatile Layout worldLayout = Layout.CIRCLE;
    private static volatile double worldRoughness;

    private final List<Island> islands;
    private final double landEnd;

    PaleShape(List<Island> islands, double landEnd) {
        this.islands = List.copyOf(islands);
        this.landEnd = landEnd;
    }

    public static PaleShape circle() {
        return CIRCLE_SHAPE;
    }

    public List<Island> islands() {
        return islands;
    }

    /** The landEndRadius the layout was made with (the server's, on a client). */
    public double landEnd() {
        return landEnd;
    }

    /**
     * {@return how far out a point is, in the units of the config radii}: the config's landEndRadius plus how far it
     * is past the coast of the nearest island (negative inside). So paleBiomeRadius, the poison radii and the fog
     * wall's radii all mean "this far past the coast", for any island.
     */
    public double effectiveRadius(double x, double z) {
        double best = Double.MAX_VALUE;
        for (Island island : islands) {
            best = Math.min(best, island.effective(x, z, landEnd));
        }
        return best;
    }

    /** {@return the island this point belongs to: the one it is least far out from} */
    public Island nearest(double x, double z) {
        Island best = islands.get(0);
        double bestRadius = Double.MAX_VALUE;
        for (Island island : islands) {
            double radius = island.effective(x, z, landEnd);
            if (radius < bestRadius) {
                bestRadius = radius;
                best = island;
            }
        }
        return best;
    }

    /** Whether island cores are lifted to land (layouts measured from the coast; not worlds from 1.6.0 and before). */
    public boolean raisesLand() {
        return !islands.get(0).scaled;
    }

    @Nullable
    public Island island(Kind kind) {
        for (Island island : islands) {
            if (island.kind() == kind) {
                return island;
            }
        }
        return null;
    }

    /** The world generator's layout (server side). */
    public static PaleShape server() {
        return server;
    }

    /** The layout the server sent this client, one round island until it has. */
    public static PaleShape client() {
        return client;
    }

    public static void setClient(PaleShape shape) {
        client = shape;
    }

    /** Uses the current world's layout for this seed (the seed search tries several). */
    static void useSeed(long worldSeed) {
        server = forSeed(worldSeed, worldLayout, worldRoughness);
    }

    private static PaleShape forSeed(long worldSeed, Layout layout, double roughness) {
        double landEnd = CommonConfig.get().landEnd();
        long seed = BiomeManager.obfuscateSeed(worldSeed);
        return switch (layout) {
            case CIRCLE -> new PaleShape(List.of(new Island(Kind.SINGLE, 0, 0, landEnd, seed, 0, true)), landEnd);
            case RAGGED_ONE -> new PaleShape(List.of(new Island(Kind.SINGLE, 0, 0, landEnd, seed, roughness, true)), landEnd);
            case ONE -> new PaleShape(List.of(new Island(Kind.SINGLE, 0, 0, landEnd, seed, roughness, false)), landEnd);
            case THREE -> threeIslands(seed, roughness, landEnd);
        };
    }

    private static PaleShape threeIslands(long seed, double roughness, double landEnd) {
        Random random = new Random(seed ^ 0x5A1E_15_1A_4D5L);
        Island mild = new Island(Kind.MILD, 0, 0, size(random), seed, roughness, false);
        Island hot = placeFrom(mild, Kind.HOT, 1, random, seed * 31 + 1, roughness);
        Island cold = placeFrom(mild, Kind.COLD, -1, random, seed * 31 + 2, roughness);
        return new PaleShape(List.of(mild, hot, cold), landEnd);
    }

    private static double size(Random random) {
        return MIN_RADIUS + (MAX_RADIUS - MIN_RADIUS) * random.nextDouble();
    }

    /** An island south (+1) or north (-1) of {@code from}, pushed further out if their coasts would come too close. */
    private static Island placeFrom(Island from, Kind kind, int southward, Random random, long seed, double roughness) {
        double radius = size(random);
        double distance = MIN_DISTANCE + (MAX_DISTANCE - MIN_DISTANCE) * random.nextDouble();
        double sideways = (random.nextDouble() * 2 - 1) * MAX_SIDEWAYS;
        double dz = southward * Math.sqrt(Math.max(0, distance * distance - sideways * sideways));
        Island island = new Island(kind, from.centerX() + sideways, from.centerZ() + dz, radius, seed, roughness, false);
        double towards = Math.atan2(dz, sideways);
        double coasts = from.radius() * from.factor(towards) + radius * island.factor(towards + Math.PI);
        if (coasts + MIN_GAP > distance) {
            double grow = (coasts + MIN_GAP) / distance;
            island = new Island(kind, from.centerX() + sideways * grow, from.centerZ() + dz * grow, radius, seed, roughness, false);
        }
        return island;
    }

    /**
     * Picks the world's layout, before its levels exist: a new world takes the config's and saves it; an existing one
     * keeps the one it was made with.
     */
    public static void onServerAboutToStart(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
        if (server.getWorldData().overworldData().isInitialized()) {
            read(path);
        } else {
            CommonConfig.Settings config = CommonConfig.get();
            worldLayout = config.threeIslands() ? Layout.THREE : Layout.ONE;
            worldRoughness = config.coastRoughness();
            write(path);
        }
        useSeed(server.getWorldData().worldGenOptions().seed());
        LOGGER.info("[thepale] World layout: {}", PaleShape.server.islands);
    }

    private static void read(Path path) {
        worldLayout = Layout.CIRCLE;
        worldRoughness = 0;
        if (!Files.exists(path)) {
            return; // made before the coast could be ragged
        }
        try {
            JsonObject root = new Gson().fromJson(Files.readString(path), JsonObject.class);
            worldRoughness = root.has("coastRoughness") ? Mth.clamp(root.get("coastRoughness").getAsDouble(), 0, 0.6) : 0;
            String layout = root.has("layout") ? root.get("layout").getAsString() : "";
            worldLayout = switch (layout) {
                case "three_islands" -> Layout.THREE;
                case "one_island" -> Layout.ONE;
                default -> Layout.RAGGED_ONE; // 1.6.0 saved only the roughness
            };
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[thepale] Could not read {}; the world falls back to one round island", path, e);
        }
    }

    private static void write(Path path) {
        JsonObject root = new JsonObject();
        root.addProperty("comment", "Settings The Pale fixed when this world was created. Changing them changes only chunks generated from now on.");
        root.addProperty("layout", worldLayout == Layout.THREE ? "three_islands" : "one_island");
        root.addProperty("coastRoughness", worldRoughness);
        try {
            Files.writeString(path, new GsonBuilder().setPrettyPrinting().create().toJson(root));
        } catch (IOException e) {
            LOGGER.error("[thepale] Could not save {}; the world will be treated as one round island when it restarts", path, e);
        }
    }

    /** For {@link ShapePayload}. */
    List<double[]> describe() {
        List<double[]> list = new ArrayList<>();
        for (Island island : islands) {
            list.add(new double[] {island.kind.ordinal(), island.centerX, island.centerZ, island.radius, island.roughness, island.scaled ? 1 : 0});
        }
        return list;
    }

    List<Long> seeds() {
        return islands.stream().map(island -> island.seed).toList();
    }

    static PaleShape fromDescription(List<double[]> islands, List<Long> seeds, double landEnd) {
        List<Island> list = new ArrayList<>();
        for (int i = 0; i < islands.size(); i++) {
            double[] d = islands.get(i);
            Kind kind = Kind.values()[Mth.clamp((int) d[0], 0, Kind.values().length - 1)];
            list.add(new Island(kind, d[1], d[2], d[3], seeds.get(i), d[4], d[5] != 0));
        }
        return list.isEmpty() ? CIRCLE_SHAPE : new PaleShape(list, landEnd);
    }
}
