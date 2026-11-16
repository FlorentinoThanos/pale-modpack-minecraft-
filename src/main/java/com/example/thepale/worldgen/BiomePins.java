package com.example.thepale.worldgen;

import com.example.thepale.config.CommonConfig;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Random;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BiomeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.Tags;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Biomes pinned to an island (pinnedBiomes in the common config): every new world gets a patch of each, on the spot of
 * its island whose terrain suits the biome best. Only the biome changes, not the terrain, so the spot is picked by the
 * climate values that shape the land (continentalness, erosion, weirdness) and, more lightly, by temperature and
 * humidity. Seas, rivers, beaches and caves inside a patch keep their own biomes. Pins are saved with the world
 * (thepale-biome-pins.json) and loaded whenever it starts; worlds without the file get none.
 */
public final class BiomePins {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "thepale-biome-pins.json";
    /** Candidate spots are this far apart. */
    private static final int GRID = 48;
    /** Patches keep this far (in patch radii) inside where the land starts to sink into the sea. */
    private static final double EDGE_MARGIN = 1.5;
    /** Below this climate depth a column is underground (vanilla's cave biomes start at 0.2). */
    private static final float SURFACE_DEPTH = 0.2F;
    /** Temperature and humidity count this much against the land-shaping parameters when picking a spot. */
    private static final double CLIMATE_WEIGHT = 0.35;

    /** A patch of a biome around (x, z), its edge wavy by its own seed. */
    public record Pin(ResourceLocation biome, String island, int x, int z, int radius, long seed) {}

    /** A pin with its biome looked up, and its edge precomputed. */
    private record Live(Pin pin, Holder<Biome> biome, double[] lobes) {
        boolean covers(int blockX, int blockZ) {
            double dx = blockX - pin.x();
            double dz = blockZ - pin.z();
            double distance2 = dx * dx + dz * dz;
            double outer = pin.radius() * 1.25;
            if (distance2 > outer * outer) {
                return false;
            }
            double angle = Math.atan2(dz, dx);
            double factor = 1 + lobes[0] * Math.sin(2 * angle + lobes[1]) + lobes[2] * Math.sin(3 * angle + lobes[3])
                    + lobes[4] * Math.sin(5 * angle + lobes[5]);
            double reach = pin.radius() * Mth.clamp(factor, 0.75, 1.25);
            return distance2 <= reach * reach;
        }
    }

    private static volatile List<Live> live = List.of();

    private BiomePins() {}

    /**
     * {@return the biome for this column}: {@code biome} as the climate picked it, unless the column is in a pinned
     * patch, near the surface, and not sea, river or beach.
     */
    public static Holder<Biome> apply(Holder<Biome> biome, int blockX, int blockZ, Climate.TargetPoint target) {
        List<Live> pins = live;
        if (pins.isEmpty() || Climate.unquantizeCoord(target.depth()) >= SURFACE_DEPTH) {
            return biome;
        }
        for (Live pin : pins) {
            if (pin.covers(blockX, blockZ)) {
                return keepsOwn(biome) ? biome : pin.biome();
            }
        }
        return biome;
    }

    private static boolean keepsOwn(Holder<Biome> biome) {
        return biome.is(BiomeTags.IS_OCEAN) || biome.is(BiomeTags.IS_RIVER) || biome.is(BiomeTags.IS_BEACH)
                || biome.is(Tags.Biomes.IS_BEACH) || biome.is(Tags.Biomes.IS_STONY_SHORES)
                || biome.is(Tags.Biomes.IS_MUSHROOM);
    }

    static void clear() {
        live = List.of();
    }

    /** {@return the current world's pins} */
    public static List<Pin> get() {
        return live.stream().map(Live::pin).toList();
    }

    private static void set(Registry<Biome> biomes, List<Pin> pins) {
        List<Live> list = new ArrayList<>();
        for (Pin pin : pins) {
            Optional<Holder.Reference<Biome>> holder = biomes.getHolder(ResourceKey.create(Registries.BIOME, pin.biome()));
            if (holder.isEmpty()) {
                LOGGER.warn("[thepale] Pinned biome {} isn't in this game (mod removed?); its patch is off", pin.biome());
                continue;
            }
            Random random = new Random(pin.seed());
            double[] lobes = new double[6];
            for (int i = 0; i < 6; i += 2) {
                lobes[i] = (0.06 + 0.1 * random.nextDouble()) / (1 + i / 2.0);
                lobes[i + 1] = random.nextDouble() * Mth.TWO_PI;
            }
            list.add(new Live(pin, holder.get(), lobes));
        }
        live = List.copyOf(list);
    }

    /**
     * Finds and saves the pins for a new world, once its seed is settled ({@link PaleShape#useSeed} has run). Each
     * biome goes to the best spot of its island that isn't taken by an earlier pin.
     */
    static void create(MinecraftServer server, NoiseBasedChunkGenerator generator, long seed) {
        clear();
        CommonConfig.Settings config = CommonConfig.get();
        if (config.pinnedBiomes().isEmpty()) {
            return;
        }
        BiomeSource source = generator.getBiomeSource();
        if (!(source instanceof PaleBiomeSource paleSource)) {
            return;
        }
        Registry<Biome> biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        RandomState random = RandomState.create(
                generator.generatorSettings().value(), server.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        Climate.Sampler sampler = random.sampler();
        PaleShape shape = PaleShape.server();
        int radius = config.pinnedBiomeRadius();
        int surfaceQuart = QuartPos.fromBlock(generator.getSeaLevel() + 8);
        List<Pin> pins = new ArrayList<>();
        List<String> placed = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        java.util.Map<PaleShape.Kind, List<Candidate>> candidatesByIsland = new java.util.EnumMap<>(PaleShape.Kind.class);
        int index = 0;
        for (String line : draw(config, seed)) {
            index++;
            int colon = line.indexOf(':');
            if (colon <= 0) {
                LOGGER.warn("[thepale] pinnedBiomes line '{}' isn't island:biome; skipped", line);
                continue;
            }
            String islandName = line.substring(0, colon).trim().toLowerCase(Locale.ROOT);
            ResourceLocation biomeId = ResourceLocation.tryParse(line.substring(colon + 1).trim());
            PaleShape.Kind kind = switch (islandName) {
                case "mild", "starter" -> PaleShape.Kind.MILD;
                case "hot", "south" -> PaleShape.Kind.HOT;
                case "cold", "north", "frozen" -> PaleShape.Kind.COLD;
                default -> null;
            };
            if (kind == null || biomeId == null) {
                LOGGER.warn("[thepale] pinnedBiomes line '{}' isn't island:biome (island mild, hot or cold); skipped", line);
                continue;
            }
            PaleShape.Island island = shape.island(kind);
            if (island == null) {
                skipped.add(line + " (no such island)");
                continue;
            }
            Optional<Holder.Reference<Biome>> holder = biomes.getHolder(ResourceKey.create(Registries.BIOME, biomeId));
            if (holder.isEmpty()) {
                skipped.add(line + " (biome not in this game)");
                continue;
            }
            List<Climate.ParameterPoint> cells = new ArrayList<>();
            for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : paleSource.thepale$parameters().values()) {
                Climate.Parameter depth = entry.getFirst().depth();
                if (entry.getSecond().is(holder.get().key()) && depth.min() <= 0 && depth.max() >= 0) {
                    cells.add(entry.getFirst());
                }
            }
            if (cells.isEmpty()) {
                skipped.add(line + " (not a surface biome of the Overworld)");
                continue;
            }
            List<Candidate> spots = candidatesByIsland.computeIfAbsent(kind,
                    k -> candidates(island, shape, config, source, sampler, surfaceQuart, radius));
            int[] spot = bestSpot(spots, cells, radius, pins);
            if (spot == null) {
                skipped.add(line + " (no room left on the island)");
                continue;
            }
            pins.add(new Pin(biomeId, islandName, spot[0], spot[1], radius, seed * 31 + index));
            placed.add(String.format(Locale.ROOT, "%s at %d %d", biomeId, spot[0], spot[1]));
        }
        set(biomes, pins);
        save(server, seed, pins);
        LOGGER.info("[thepale] Pinned biomes: {}{}", placed.isEmpty() ? "none" : String.join(", ", placed),
                skipped.isEmpty() ? "" : ". Skipped: " + String.join(", ", skipped));
    }

    /** A spot an island's patch could go, with the climate and water at five points across it. */
    private record Candidate(int x, int z, Climate.TargetPoint[] probes, boolean[] water) {}

    private static List<Candidate> candidates(
            PaleShape.Island island, PaleShape shape, CommonConfig.Settings config, BiomeSource source,
            Climate.Sampler sampler, int surfaceQuart, int radius) {
        double limit = config.landFalloffStart() - EDGE_MARGIN * radius;
        double reach = island.reach(config.landFalloffStart(), shape.landEnd());
        int half = radius / 2;
        int[][] offsets = {{0, 0}, {half, 0}, {-half, 0}, {0, half}, {0, -half}};
        List<Candidate> list = new ArrayList<>();
        for (double x = island.centerX() - reach; x <= island.centerX() + reach; x += GRID) {
            for (double z = island.centerZ() - reach; z <= island.centerZ() + reach; z += GRID) {
                int bx = (int) x;
                int bz = (int) z;
                if (shape.nearest(bx, bz) != island || shape.effectiveRadius(bx, bz) > limit) {
                    continue;
                }
                Climate.TargetPoint[] probes = new Climate.TargetPoint[offsets.length];
                boolean[] water = new boolean[offsets.length];
                for (int i = 0; i < offsets.length; i++) {
                    int px = bx + offsets[i][0];
                    int pz = bz + offsets[i][1];
                    int qx = QuartPos.fromBlock(px);
                    int qz = QuartPos.fromBlock(pz);
                    water[i] = keepsOwn(source.getNoiseBiome(qx, surfaceQuart, qz, sampler));
                    probes[i] = PaleClimate.shift(sampler.sample(qx, surfaceQuart, qz), px, pz);
                }
                list.add(new Candidate(bx, bz, probes, water));
            }
        }
        return list;
    }

    /** {@return a few lines of pinnedBiomes for each island, drawn at random by the seed} */
    private static List<String> draw(CommonConfig.Settings config, long seed) {
        java.util.Map<String, List<String>> byIsland = new java.util.LinkedHashMap<>();
        for (String line : config.pinnedBiomes()) {
            int colon = line.indexOf(':');
            String island = colon > 0 ? line.substring(0, colon).trim().toLowerCase(Locale.ROOT) : "";
            byIsland.computeIfAbsent(island, k -> new ArrayList<>()).add(line);
        }
        List<String> drawn = new ArrayList<>();
        byIsland.forEach((island, lines) -> {
            int count = switch (island) {
                case "mild", "starter" -> config.pinnedOnMild();
                case "hot", "south" -> config.pinnedOnHot();
                case "cold", "north", "frozen" -> config.pinnedOnCold();
                default -> lines.size(); // reported as a bad line below
            };
            List<String> shuffled = new ArrayList<>(lines);
            java.util.Collections.shuffle(shuffled, new Random(seed ^ island.hashCode() * 0x9E3779B97F4A7C15L));
            drawn.addAll(shuffled.subList(0, Math.min(count, shuffled.size())));
        });
        return drawn;
    }

    @Nullable
    private static int[] bestSpot(List<Candidate> candidates, List<Climate.ParameterPoint> cells, int radius, List<Pin> taken) {
        int[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (Candidate candidate : candidates) {
            if (tooClose(candidate.x(), candidate.z(), radius, taken)) {
                continue;
            }
            double score = 0;
            for (int i = 0; i < candidate.probes().length; i++) {
                // Water or shore counts heavily against a spot: the patch would barely show there.
                score += candidate.water()[i] ? 4 : fit(candidate.probes()[i], cells);
            }
            if (score < bestScore) {
                bestScore = score;
                best = new int[] {candidate.x(), candidate.z()};
            }
        }
        return best;
    }

    private static boolean tooClose(int x, int z, int radius, List<Pin> taken) {
        for (Pin pin : taken) {
            // Far enough apart that even the widest lobes of two patches don't meet.
            double min = 1.25 * (radius + pin.radius()) + GRID;
            double dx = x - pin.x();
            double dz = z - pin.z();
            if (dx * dx + dz * dz < min * min) {
                return true;
            }
        }
        return false;
    }

    /** {@return how far the climate is from the nearest of the biome's cells; 0 inside one} */
    private static double fit(Climate.TargetPoint point, List<Climate.ParameterPoint> cells) {
        double best = Double.MAX_VALUE;
        for (Climate.ParameterPoint cell : cells) {
            double shape = gap(cell.continentalness(), point.continentalness())
                    + gap(cell.erosion(), point.erosion())
                    + gap(cell.weirdness(), point.weirdness());
            double climate = gap(cell.temperature(), point.temperature()) + gap(cell.humidity(), point.humidity());
            best = Math.min(best, shape + CLIMATE_WEIGHT * climate);
        }
        return best;
    }

    private static double gap(Climate.Parameter parameter, long value) {
        double v = Climate.unquantizeCoord(value);
        double min = Climate.unquantizeCoord(parameter.min());
        double max = Climate.unquantizeCoord(parameter.max());
        double d = v < min ? min - v : v > max ? v - max : 0;
        return d * d;
    }

    private static void save(MinecraftServer server, long seed, List<Pin> pins) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
        JsonObject root = new JsonObject();
        root.addProperty("comment", "Biome patches The Pale put on the islands for this world. Deleting this file un-pins them for chunks generated from now on.");
        root.addProperty("seed", seed);
        JsonArray list = new JsonArray();
        for (Pin pin : pins) {
            JsonObject entry = new JsonObject();
            entry.addProperty("biome", pin.biome().toString());
            entry.addProperty("island", pin.island());
            entry.addProperty("x", pin.x());
            entry.addProperty("z", pin.z());
            entry.addProperty("radius", pin.radius());
            entry.addProperty("shapeSeed", pin.seed());
            list.add(entry);
        }
        root.add("pins", list);
        try {
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("[thepale] Could not save the biome pins to {}; they will be lost when the world restarts", path, e);
        }
    }

    /** Loads an existing world's pins, if it has any. */
    static void load(MinecraftServer server) {
        clear();
        Path path = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
        if (!Files.exists(path)) {
            return;
        }
        List<Pin> pins = new ArrayList<>();
        try {
            JsonObject root = GSON.fromJson(Files.readString(path), JsonObject.class);
            for (var element : root.getAsJsonArray("pins")) {
                JsonObject entry = element.getAsJsonObject();
                pins.add(new Pin(
                        ResourceLocation.parse(entry.get("biome").getAsString()),
                        entry.has("island") ? entry.get("island").getAsString() : "",
                        entry.get("x").getAsInt(),
                        entry.get("z").getAsInt(),
                        entry.get("radius").getAsInt(),
                        entry.get("shapeSeed").getAsLong()));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[thepale] Could not read the biome pins from {}", path, e);
        }
        set(server.registryAccess().registryOrThrow(Registries.BIOME), pins);
        if (!pins.isEmpty()) {
            LOGGER.info("[thepale] Loaded {} biome pins: {}", pins.size(), pins.stream().map(pin -> pin.biome().toString()).toList());
        }
    }
}
