package com.example.thepale.worldgen;

import com.example.thepale.survey.IslandCheck;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.storage.LevelResource;
import org.slf4j.Logger;

/**
 * Structures pinned to the island. When a required structure doesn't come up on its own, one try of its structure set
 * is moved to a chunk on the island where it does fit (right biome, workable ground). Pins belong to the world: they're
 * saved next to it (thepale-structure-pins.json) and loaded whenever it starts, so chunks generated later agree.
 */
public final class StructurePins {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "thepale-structure-pins.json";
    /** Structure starts tried per structure set before giving up on pinning it; jigsaw structures are slowish. */
    private static final int MAX_TRIES = 150;
    /** Keep pinned structures this far inside the island's edge. */
    private static final double EDGE_MARGIN = 64;

    /** A structure set's try moved to a chunk. */
    public record Pin(ResourceLocation set, int spacing, ChunkPos chunk) {
        long region() {
            return ChunkPos.asLong(Math.floorDiv(chunk.x, spacing), Math.floorDiv(chunk.z, spacing));
        }
    }

    private static volatile List<Pin> pins = List.of();

    private StructurePins() {}

    /** {@return the current world's pins, by structure set and region} */
    static Map<ResourceLocation, Map<Long, ChunkPos>> current() {
        Map<ResourceLocation, Map<Long, ChunkPos>> bySet = new HashMap<>();
        for (Pin pin : pins) {
            bySet.computeIfAbsent(pin.set(), set -> new HashMap<>()).put(pin.region(), pin.chunk());
        }
        return bySet;
    }

    static void set(List<Pin> newPins) {
        pins = List.copyOf(newPins);
    }

    static List<Pin> get() {
        return pins;
    }

    /**
     * {@return a pin that puts a structure {@code requirement} accepts on the island, or null if there's nowhere it fits}.
     * Tries chunks in an order fixed by the seed, so the same world always gets the same pins.
     */
    static Pin find(IslandCheck.World world, double radius, SeedSearch.Requirement requirement) {
        for (Holder<StructureSet> holder : world.registries().registryOrThrow(Registries.STRUCTURE_SET).holders().toList()) {
            StructureSet set = holder.value();
            if (!(set.placement() instanceof RandomSpreadStructurePlacement placement)) {
                continue;
            }
            List<Structure> wanted = set.structures().stream()
                    .filter(entry -> entry.structure().unwrapKey().map(key -> requirement.matches(key.location())).orElse(false))
                    .map(entry -> entry.structure().value())
                    .toList();
            if (wanted.isEmpty()) {
                continue;
            }
            ResourceLocation setId = holder.unwrapKey().orElseThrow().location();
            Pin pin = findFor(world, radius, requirement, setId, set, placement, wanted);
            if (pin != null) {
                return pin;
            }
        }
        return null;
    }

    private static Pin findFor(
            IslandCheck.World world, double radius, SeedSearch.Requirement requirement, ResourceLocation setId,
            StructureSet set, RandomSpreadStructurePlacement placement, List<Structure> wanted) {
        int spacing = PaleStructures.spacing(placement);
        int separation = PaleStructures.separation(placement, spacing);
        Set<Long> takenRegions = new HashSet<>();
        for (Pin pin : pins) {
            if (pin.set().equals(setId)) {
                takenRegions.add(pin.region());
            }
        }
        PaleShape shape = PaleShape.server();
        List<ChunkPos> chunks = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (int[] box : IslandCheck.islandBoxes(radius)) {
            for (int x = box[0]; x <= box[2]; x++) {
                for (int z = box[1]; z <= box[3]; z++) {
                    ChunkPos chunk = new ChunkPos(x, z);
                    if (shape.effectiveRadius(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()) <= radius - EDGE_MARGIN
                            && seen.add(chunk.toLong())) {
                        chunks.add(chunk);
                    }
                }
            }
        }
        Collections.shuffle(chunks, new java.util.Random(world.seed() ^ setId.hashCode()));
        int tries = 0;
        for (ChunkPos chunk : chunks) {
            if (!biomeFits(world, chunk, wanted)) {
                continue;
            }
            long region = ChunkPos.asLong(Math.floorDiv(chunk.x, spacing), Math.floorDiv(chunk.z, spacing));
            if (takenRegions.contains(region)) {
                continue;
            }
            PinnedSpreadPlacement pinned = new PinnedSpreadPlacement(placement, spacing, separation, Map.of(region, chunk));
            if (!pinned.isStructureChunk(world.structureState(), chunk.x, chunk.z)) {
                continue; // a frequency roll or another structure's exclusion zone rules this chunk out
            }
            StructureStart start = IslandCheck.start(new StructureSet(set.structures(), pinned), chunk, world);
            if (start != null) {
                ResourceLocation id = world.registries().registryOrThrow(Registries.STRUCTURE).getKey(start.getStructure());
                BlockPos center = start.getBoundingBox().getCenter();
                if (id != null && requirement.matches(id) && shape.effectiveRadius(center.getX(), center.getZ()) < radius) {
                    return new Pin(setId, spacing, chunk);
                }
            }
            if (++tries >= MAX_TRIES) {
                break;
            }
        }
        return null;
    }

    /**
     * A cheap first check: is the chunk's middle in a biome one of these structures accepts, at the surface's usual
     * height or deep down (ancient cities start at y -27, in the deep dark)?
     */
    private static boolean biomeFits(IslandCheck.World world, ChunkPos chunk, List<Structure> structures) {
        int seaLevel = world.generator().getSeaLevel();
        for (int y : new int[] {seaLevel + 4, seaLevel - 30, -27, -45}) {
            Holder<Biome> biome = world.generator().getBiomeSource().getNoiseBiome(
                    QuartPos.fromBlock(chunk.getMiddleBlockX()), QuartPos.fromBlock(y),
                    QuartPos.fromBlock(chunk.getMiddleBlockZ()), world.random().sampler());
            for (Structure structure : structures) {
                HolderSet<Biome> biomes = structure.biomes();
                if (biomes.contains(biome)) {
                    return true;
                }
            }
        }
        return false;
    }

    static void save(MinecraftServer server, long seed) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
        JsonObject root = new JsonObject();
        root.addProperty("comment", "Structures The Pale moved onto the island for this world. Deleting this file un-pins them for chunks generated from now on.");
        root.addProperty("seed", seed);
        JsonArray list = new JsonArray();
        for (Pin pin : pins) {
            JsonObject entry = new JsonObject();
            entry.addProperty("set", pin.set().toString());
            entry.addProperty("spacing", pin.spacing());
            entry.addProperty("chunkX", pin.chunk().x);
            entry.addProperty("chunkZ", pin.chunk().z);
            list.add(entry);
        }
        root.add("pins", list);
        try {
            Files.writeString(path, GSON.toJson(root));
        } catch (IOException e) {
            LOGGER.error("[thepale] Could not save the structure pins to {}; they will be lost when the world restarts", path, e);
        }
    }

    /** Loads the world's pins, if it has any. Pins whose structure set has since changed spacing are dropped. */
    static void load(MinecraftServer server) {
        Path path = server.getWorldPath(LevelResource.ROOT).resolve(FILE);
        pins = List.of();
        if (!Files.exists(path)) {
            return;
        }
        List<Pin> loaded = new ArrayList<>();
        try {
            JsonObject root = GSON.fromJson(Files.readString(path), JsonObject.class);
            var sets = server.registryAccess().registryOrThrow(Registries.STRUCTURE_SET);
            for (var element : root.getAsJsonArray("pins")) {
                JsonObject entry = element.getAsJsonObject();
                ResourceLocation setId = ResourceLocation.parse(entry.get("set").getAsString());
                int spacing = entry.get("spacing").getAsInt();
                StructureSet set = sets.get(setId);
                if (set == null || !(set.placement() instanceof RandomSpreadStructurePlacement placement)
                        || PaleStructures.spacing(placement) != spacing) {
                    LOGGER.warn("[thepale] Structure pin for {} no longer matches its structure set; dropped", setId);
                    continue;
                }
                loaded.add(new Pin(setId, spacing, new ChunkPos(entry.get("chunkX").getAsInt(), entry.get("chunkZ").getAsInt())));
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.error("[thepale] Could not read the structure pins from {}", path, e instanceof JsonParseException ? null : e);
        }
        pins = List.copyOf(loaded);
        if (!loaded.isEmpty()) {
            LOGGER.info("[thepale] Loaded {} structure pins: {}", loaded.size(), loaded.stream().map(pin -> pin.set().toString()).toList());
        }
    }
}
