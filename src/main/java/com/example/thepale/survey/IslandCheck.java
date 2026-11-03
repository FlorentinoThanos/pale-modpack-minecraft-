package com.example.thepale.survey;

import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.stream.Stream;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import com.example.thepale.worldgen.PaleShape;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

/**
 * Which structures a world generator puts on the island, worked out without generating any chunks: for every chunk
 * where a structure set may start a structure, the same choice and the same start that
 * {@link ChunkGenerator#createStructures} makes. Thread-safe, as world generation is.
 */
public final class IslandCheck {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final java.util.concurrent.atomic.AtomicInteger FAILURES_LOGGED = new java.util.concurrent.atomic.AtomicInteger();

    /** Everything about one world (or one candidate seed) that the check needs. */
    public record World(
            RegistryAccess registries,
            ChunkGenerator generator,
            RandomState random,
            ChunkGeneratorStructureState structureState,
            StructureTemplateManager templates,
            LevelHeightAccessor height,
            long seed) {}

    private IslandCheck() {}

    /** {@return the structures that start within {@code radius} blocks of (0, 0), by id, each with where it starts} */
    public static Map<ResourceLocation, List<BlockPos>> structures(World world, double radius) {
        Map<ResourceLocation, List<BlockPos>> found = new ConcurrentHashMap<>();
        candidates(world, radius, id -> true).parallelStream().forEach(candidate -> {
            Found start = check(candidate, world, radius);
            if (start != null) {
                found.computeIfAbsent(start.id(), key -> Collections.synchronizedList(new ArrayList<>())).add(start.center());
            }
        });
        return new TreeMap<>(found);
    }

    /**
     * {@return where one structure whose id {@code wanted} accepts starts within {@code radius} blocks of (0, 0), or
     * null if none does}. Only tries the structure sets that can place one, and stops at the first.
     */
    public static Found findAny(World world, double radius, Predicate<ResourceLocation> wanted) {
        return candidates(world, radius, wanted).parallelStream()
                .map(candidate -> check(candidate, world, radius))
                .filter(start -> start != null && wanted.test(start.id()))
                .findAny()
                .orElse(null);
    }

    /** A structure start: what it is and the middle of its bounding box. */
    public record Found(ResourceLocation id, BlockPos center) {}

    /** The chunks where a set that can place a structure {@code wanted} accepts gets a try, on the island. */
    private static List<Candidate> candidates(World world, double radius, Predicate<ResourceLocation> wanted) {
        List<Candidate> candidates = new ArrayList<>();
        for (Holder<StructureSet> set : world.structureState().possibleStructureSets()) {
            boolean relevant = set.value().structures().stream()
                    .anyMatch(entry -> entry.structure().unwrapKey().map(key -> wanted.test(key.location())).orElse(false));
            if (relevant) {
                for (ChunkPos chunk : candidateChunks(set.value().placement(), world, radius)) {
                    candidates.add(new Candidate(set.value(), chunk));
                }
            }
        }
        return candidates;
    }

    private static Found check(Candidate candidate, World world, double radius) {
        StructurePlacement placement = candidate.set().placement();
        ChunkPos chunk = candidate.chunk();
        if (!placement.isStructureChunk(world.structureState(), chunk.x, chunk.z)) {
            return null;
        }
        StructureStart start = start(candidate.set(), chunk, world);
        if (start == null) {
            return null;
        }
        BlockPos center = start.getBoundingBox().getCenter();
        if (PaleShape.server().effectiveRadius(center.getX(), center.getZ()) >= radius) {
            return null;
        }
        ResourceLocation id = world.registries().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
        return id == null ? null : new Found(id, center);
    }

    /** {@return every structure the generator could place anywhere in this world, by id} */
    public static List<ResourceLocation> possibleStructures(World world) {
        return world.structureState().possibleStructureSets().stream()
                .flatMap(set -> set.value().structures().stream())
                .map(entry -> entry.structure().unwrapKey().map(key -> key.location()).orElse(null))
                .filter(id -> id != null)
                .distinct()
                .sorted()
                .toList();
    }

    private record Candidate(StructureSet set, ChunkPos chunk) {}

    private static List<ChunkPos> candidateChunks(StructurePlacement placement, World world, double radius) {
        List<int[]> boxes = islandBoxes(radius);
        java.util.Set<Long> seen = new java.util.HashSet<>();
        List<ChunkPos> chunks = new ArrayList<>();
        if (placement instanceof RandomSpreadStructurePlacement spread) {
            int spacing = spread.spacing();
            for (int[] box : boxes) {
                for (int rx = Math.floorDiv(box[0], spacing); rx <= Math.floorDiv(box[2], spacing); rx++) {
                    for (int rz = Math.floorDiv(box[1], spacing); rz <= Math.floorDiv(box[3], spacing); rz++) {
                        ChunkPos chunk = spread.getPotentialStructureChunk(world.seed(), rx * spacing, rz * spacing);
                        if (seen.add(chunk.toLong())) {
                            chunks.add(chunk);
                        }
                    }
                }
            }
        } else if (placement instanceof ConcentricRingsStructurePlacement rings) {
            List<ChunkPos> positions = world.structureState().getRingPositionsFor(rings);
            if (positions != null) {
                chunks.addAll(positions);
            }
        } else {
            // A placement type from another mod: try every chunk.
            for (int[] box : boxes) {
                for (int x = box[0]; x <= box[2]; x++) {
                    for (int z = box[1]; z <= box[3]; z++) {
                        ChunkPos chunk = new ChunkPos(x, z);
                        if (seen.add(chunk.toLong())) {
                            chunks.add(chunk);
                        }
                    }
                }
            }
        }
        return chunks;
    }

    /** {@return for each island, the square of chunks (min x, min z, max x, max z) holding everything inside {@code radius}} */
    public static List<int[]> islandBoxes(double radius) {
        PaleShape shape = PaleShape.server();
        List<int[]> boxes = new ArrayList<>();
        for (PaleShape.Island island : shape.islands()) {
            double reach = island.reach(radius, shape.landEnd()) + 16;
            boxes.add(new int[] {
                Mth.floor((island.centerX() - reach) / 16.0), Mth.floor((island.centerZ() - reach) / 16.0),
                Mth.floor((island.centerX() + reach) / 16.0), Mth.floor((island.centerZ() + reach) / 16.0)});
        }
        return boxes;
    }

    /** The structure {@link ChunkGenerator#createStructures} would start in this chunk for this set, or null. */
    public static StructureStart start(StructureSet set, ChunkPos chunk, World world) {
        List<StructureSet.StructureSelectionEntry> entries = set.structures();
        if (entries.size() == 1) {
            return tryStart(entries.get(0).structure().value(), chunk, world);
        }
        List<StructureSet.StructureSelectionEntry> left = new ArrayList<>(entries);
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureSeed(world.seed(), chunk.x, chunk.z);
        int totalWeight = left.stream().mapToInt(StructureSet.StructureSelectionEntry::weight).sum();
        while (!left.isEmpty()) {
            int pick = random.nextInt(totalWeight);
            int index = 0;
            for (StructureSet.StructureSelectionEntry entry : left) {
                pick -= entry.weight();
                if (pick < 0) {
                    break;
                }
                index++;
            }
            StructureSet.StructureSelectionEntry entry = left.get(index);
            StructureStart start = tryStart(entry.structure().value(), chunk, world);
            if (start != null) {
                return start;
            }
            left.remove(index);
            totalWeight -= entry.weight();
        }
        return null;
    }

    private static StructureStart tryStart(Structure structure, ChunkPos chunk, World world) {
        try {
            StructureStart start = structure.generate(
                    world.registries(), world.generator(), world.generator().getBiomeSource(), world.random(),
                    world.templates(), world.seed(), chunk, 0, world.height(), structure.biomes()::contains);
            return start.isValid() ? start : null;
        } catch (RuntimeException e) {
            if (FAILURES_LOGGED.getAndIncrement() < 3) {
                LOGGER.warn("[thepale] Structure check failed for {} at {}",
                        world.registries().registryOrThrow(Registries.STRUCTURE).getKey(structure), chunk, e);
            }
            return null;
        }
    }

    /** For tests: the ids in {@code wanted} that {@code found} lacks. */
    public static List<ResourceLocation> missing(List<ResourceLocation> wanted, Map<ResourceLocation, ?> found) {
        return wanted.stream().filter(id -> !found.containsKey(id)).toList();
    }

    static Stream<String> describe(Map<ResourceLocation, List<BlockPos>> found) {
        return found.entrySet().stream().map(entry -> {
            BlockPos first = entry.getValue().get(0);
            return String.format("%s x%d (e.g. %d %d)", entry.getKey(), entry.getValue().size(), first.getX(), first.getZ());
        });
    }
}
