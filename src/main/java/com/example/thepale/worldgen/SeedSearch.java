package com.example.thepale.worldgen;

import com.example.thepale.config.CommonConfig;
import com.example.thepale.mixin.PrimaryLevelDataAccessor;
import com.example.thepale.survey.IslandCheck;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.dimension.LevelStem;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.storage.PrimaryLevelData;
import net.minecraft.world.level.storage.WorldData;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import org.slf4j.Logger;

/**
 * For a new world whose seed the game rolled (nobody typed one), tries seeds until one puts every required structure
 * (requiredStructures in the common config) on the island, and uses that one. Runs before the world's levels are
 * created, so nothing has been generated with the old seed. For a seed somebody chose, only reports what's missing.
 */
public final class SeedSearch {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Seeds rolled by {@link WorldOptions#randomSeed()} in this game session. */
    private static final Set<Long> RANDOM_SEEDS = ConcurrentHashMap.newKeySet();
    /** What to tell the first player who joins, if anything. */
    private static volatile List<Component> message = List.of();

    private SeedSearch() {}

    public static void rememberRandomSeed(long seed) {
        RANDOM_SEEDS.add(seed);
    }

    /** One line of requiredStructures: any of several structure ids, each possibly ending in * for "starting with". */
    record Requirement(String text, List<String> patterns) {
        static Requirement parse(String text) {
            List<String> patterns = new ArrayList<>();
            for (String part : text.split("\\|")) {
                if (!part.isBlank()) {
                    patterns.add(part.trim());
                }
            }
            return new Requirement(text.trim(), patterns);
        }

        boolean matches(ResourceLocation id) {
            String name = id.toString();
            for (String pattern : patterns) {
                if (pattern.endsWith("*") ? name.startsWith(pattern.substring(0, pattern.length() - 1)) : name.equals(pattern)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Called once the Pale biome is switched on for the Overworld, before its level exists. */
    public static void onServerAboutToStart(MinecraftServer server, LevelStem overworld) {
        message = List.of();
        StructurePins.set(List.of());
        WorldData data = server.getWorldData();
        if (data.overworldData().isInitialized()) {
            StructurePins.load(server); // an existing world: keep its pins, never change anything
            return;
        }
        if (!(overworld.generator() instanceof NoiseBasedChunkGenerator generator)) {
            return; // no normal terrain (a superflat world, say)
        }
        List<Requirement> requirements = requirements(server.registryAccess().registryOrThrow(Registries.STRUCTURE));
        if (requirements.isEmpty()) {
            return;
        }
        CommonConfig.Settings config = CommonConfig.get();
        int statsSeeds = Integer.getInteger("thepale.debug.seedStats", 0);
        if (statsSeeds > 0) {
            logStats(server, overworld, generator, requirements, statsSeeds);
            PaleShape.useSeed(data.worldGenOptions().seed());
        }
        long started = System.currentTimeMillis();
        long seed = data.worldGenOptions().seed();
        boolean rolled = RANDOM_SEEDS.contains(seed);
        boolean search = rolled && config.searchSeed() && data instanceof PrimaryLevelData;
        long chosen = search ? search(server, overworld, generator, seed, requirements, config) : seed;
        if (chosen != seed) {
            ((PrimaryLevelDataAccessor) data).thepale$setWorldOptions(data.worldGenOptions().withSeed(OptionalLong.of(chosen)));
        }

        // Whatever didn't come up on its own: move a try of its structure set to somewhere on the island it fits.
        List<String> missing = missing(server, overworld, generator, chosen, requirements, false);
        List<String> pinnedNames = new ArrayList<>();
        if (!missing.isEmpty() && config.pinMissingStructures()) {
            IslandCheck.World world = world(server, overworld, generator, chosen);
            List<StructurePins.Pin> pins = new ArrayList<>();
            for (Requirement requirement : requirements) {
                if (missing.contains(requirement.text())) {
                    StructurePins.Pin pin = StructurePins.find(world, config.paleBiomeRadius(), requirement);
                    if (pin != null) {
                        pins.add(pin);
                        StructurePins.set(pins);
                        pinnedNames.add(requirement.text());
                    }
                }
            }
            if (!pins.isEmpty()) {
                StructurePins.save(server, chosen);
                // Check again with the pins in place: a moved try can't have cost anything else, but make sure.
                missing = missing(server, overworld, generator, chosen, requirements, false);
            }
        }
        PaleShape.useSeed(chosen);
        double seconds = (System.currentTimeMillis() - started) / 1000.0;
        String how = search ? (chosen == seed ? "the rolled seed" : "picked by the seed search") : (rolled ? "rolled, search off" : "your own seed, kept");
        report(chosen, how, missing, pinnedNames, seconds);
    }

    /** {@return the first rolled seed that has everything on the island, or the one that came closest in the time allowed} */
    private static long search(
            MinecraftServer server, LevelStem overworld, NoiseBasedChunkGenerator generator, long seed,
            List<Requirement> requirements, CommonConfig.Settings config) {
        long deadline = System.currentTimeMillis() + config.maxSearchSeconds() * 1000L;
        Map<Requirement, Integer> failures = new HashMap<>();
        RandomSource random = RandomSource.create();
        long best = seed;
        int bestMissing = Integer.MAX_VALUE;
        int tried = 0;
        while (true) {
            long candidate = tried == 0 ? seed : random.nextLong();
            tried++;
            // Check the requirements that fail most often first, and give up on a seed as soon as it can't beat the best.
            List<Requirement> order = new ArrayList<>(requirements);
            order.sort(Comparator.comparingInt((Requirement requirement) -> failures.getOrDefault(requirement, 0)).reversed());
            IslandCheck.World world = world(server, overworld, generator, candidate);
            int missing = 0;
            for (Requirement requirement : order) {
                if (IslandCheck.findAny(world, config.paleBiomeRadius(), requirement::matches) == null) {
                    failures.merge(requirement, 1, Integer::sum);
                    if (++missing >= bestMissing) {
                        break;
                    }
                }
            }
            if (missing < bestMissing) {
                bestMissing = missing;
                best = candidate;
            }
            if (bestMissing == 0 || System.currentTimeMillis() > deadline) {
                LOGGER.info("[thepale] Seed search tried {} seeds; the best misses {} of {} requirements", tried, bestMissing, requirements.size());
                return best;
            }
        }
    }

    /** For tuning: how often each requirement is met, over {@code count} random seeds (-Dthepale.debug.seedStats=N). */
    private static void logStats(
            MinecraftServer server, LevelStem overworld, NoiseBasedChunkGenerator generator, List<Requirement> requirements, int count) {
        Map<String, Integer> met = new java.util.LinkedHashMap<>();
        requirements.forEach(requirement -> met.put(requirement.text(), 0));
        Map<Integer, Integer> missingHistogram = new java.util.TreeMap<>();
        RandomSource random = RandomSource.create();
        long started = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            List<String> missing = missing(server, overworld, generator, random.nextLong(), requirements, false);
            requirements.forEach(requirement -> {
                if (!missing.contains(requirement.text())) {
                    met.merge(requirement.text(), 1, Integer::sum);
                }
            });
            missingHistogram.merge(missing.size(), 1, Integer::sum);
        }
        LOGGER.info("[thepale] Seed stats over {} seeds ({} s):", count, (System.currentTimeMillis() - started) / 1000);
        met.forEach((text, n) -> LOGGER.info("[thepale]   {}% {}", Math.round(100.0 * n / count), text));
        LOGGER.info("[thepale]   seeds by number of requirements missing: {}", missingHistogram);
    }

    private static List<Requirement> requirements(Registry<Structure> structures) {
        List<Requirement> requirements = new ArrayList<>();
        for (String line : CommonConfig.get().requiredStructures()) {
            Requirement requirement = Requirement.parse(line);
            if (requirement.patterns().isEmpty()) {
                continue;
            }
            if (structures.keySet().stream().anyMatch(requirement::matches)) {
                requirements.add(requirement);
            } else {
                LOGGER.info("[thepale] Required structure {} isn't in this game (mod not installed?); skipped", requirement.text());
            }
        }
        return requirements;
    }

    private static IslandCheck.World world(MinecraftServer server, LevelStem overworld, NoiseBasedChunkGenerator generator, long seed) {
        PaleShape.useSeed(seed); // the coast differs with the seed, and terrain, biomes and the checks all follow it
        RandomState random = RandomState.create(
                generator.generatorSettings().value(), server.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        DimensionType type = overworld.type().value();
        return new IslandCheck.World(
                server.registryAccess(),
                generator,
                random,
                generator.createState(server.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET), random, seed),
                server.getStructureManager(),
                LevelHeightAccessor.create(type.minY(), type.height()),
                seed);
    }

    private static List<String> missing(
            MinecraftServer server, LevelStem overworld, NoiseBasedChunkGenerator generator, long seed,
            List<Requirement> requirements, boolean stopAtFirst) {
        IslandCheck.World world = world(server, overworld, generator, seed);
        List<String> missing = new ArrayList<>();
        for (Requirement requirement : requirements) {
            if (IslandCheck.findAny(world, CommonConfig.get().paleBiomeRadius(), requirement::matches) == null) {
                missing.add(requirement.text());
                if (stopAtFirst) {
                    break;
                }
            }
        }
        return missing;
    }

    private static void report(long seed, String how, List<String> missing, List<String> pinned, double seconds) {
        LOGGER.info("[thepale] Seed {} ({}, {} s). Pinned onto the island: {}. Not on the island: {}",
                seed, how, Math.round(seconds), pinned.isEmpty() ? "nothing" : String.join(", ", pinned),
                missing.isEmpty() ? "nothing, every required structure is there" : String.join(", ", missing));
        List<Component> lines = new ArrayList<>();
        if (!missing.isEmpty()) {
            lines.add(Component.literal("[The Pale] Not on this island: " + String.join(", ", missing)
                    + " (no room for them on it). /thepale survey lists what is.").withStyle(ChatFormatting.GRAY));
        }
        message = lines;
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        List<Component> lines = message;
        if (!lines.isEmpty()) {
            message = List.of();
            lines.forEach(line -> event.getEntity().sendSystemMessage(line));
        }
    }
}
