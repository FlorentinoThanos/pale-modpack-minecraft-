package com.example.thepale.worldgen;

import com.example.thepale.config.CommonConfig;
import com.example.thepale.mixinsupport.VillagePlacements;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.slf4j.Logger;

/**
 * Keeps pillager outposts (and whatever else is in keepAwayFromVillages) away from villages of every kind. Vanilla
 * keeps its outposts 10 chunks from its own villages only, so Terralith's fortified villages, its desert outposts and
 * villages moved by {@link StructurePins} all ended up with a pillager tower in the middle. Checked where every
 * structure start is made, so generation, /locate, the seed search and the pins agree.
 */
public final class VillageClearance {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<Structure, Boolean> KEPT_AWAY = new IdentityHashMap<>();
    private static List<String> cachedFor = List.of();
    private static List<Pattern> patterns = List.of();
    private static volatile boolean logged;

    private VillageClearance() {}

    /**
     * Hands the random state the placements of the village sets as {@code adjusted} (the sets generation uses, in the
     * same order as {@code original}) has them.
     */
    public static void remember(RandomState random, List<Holder<StructureSet>> original, List<Holder<StructureSet>> adjusted) {
        List<String> wanted = CommonConfig.get().villageStructureSets();
        List<RandomSpreadStructurePlacement> placements = new ArrayList<>();
        List<String> found = new ArrayList<>();
        for (int i = 0; i < original.size() && i < adjusted.size(); i++) {
            ResourceLocation id = original.get(i).unwrapKey().map(ResourceKey::location).orElse(null);
            if (id != null && wanted.contains(id.toString())
                    && adjusted.get(i).value().placement() instanceof RandomSpreadStructurePlacement placement) {
                placements.add(placement);
                found.add(id.toString());
            }
        }
        ((VillagePlacements) (Object) random).thepale$setVillagePlacements(placements);
        if (!found.isEmpty() && !logged) {
            logged = true;
            LOGGER.info("[thepale] Keeping {} at least {} chunks from {}",
                    CommonConfig.get().keepAwayFromVillages(), CommonConfig.get().villageClearance(), String.join(", ", found));
        }
    }

    /** {@return whether the structure may start in this chunk, as far as villages go} */
    public static boolean allowed(Structure structure, RegistryAccess registries, RandomState random, long seed, ChunkPos chunk) {
        int range = CommonConfig.get().villageClearance();
        if (range <= 0) {
            return true;
        }
        List<RandomSpreadStructurePlacement> villages = ((VillagePlacements) (Object) random).thepale$villagePlacements();
        if (villages.isEmpty() || !keptAway(structure, registries.registryOrThrow(Registries.STRUCTURE))) {
            return true;
        }
        for (RandomSpreadStructurePlacement placement : villages) {
            int spacing = placement.spacing();
            for (int regionX = Math.floorDiv(chunk.x - range, spacing); regionX <= Math.floorDiv(chunk.x + range, spacing); regionX++) {
                for (int regionZ = Math.floorDiv(chunk.z - range, spacing); regionZ <= Math.floorDiv(chunk.z + range, spacing); regionZ++) {
                    // Like vanilla's exclusion zone: the spot a village would try counts, whether or not its biome lets it.
                    ChunkPos village = placement.getPotentialStructureChunk(seed, regionX * spacing, regionZ * spacing);
                    if (Math.abs(village.x - chunk.x) <= range && Math.abs(village.z - chunk.z) <= range) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    private static boolean keptAway(Structure structure, Registry<Structure> registry) {
        synchronized (KEPT_AWAY) {
            List<String> wanted = CommonConfig.get().keepAwayFromVillages();
            if (!wanted.equals(cachedFor)) {
                List<Pattern> compiled = new ArrayList<>();
                for (String line : wanted) {
                    if (!line.isBlank()) {
                        compiled.add(IslandFeatures.glob(line.trim()));
                    }
                }
                patterns = compiled;
                cachedFor = wanted;
                KEPT_AWAY.clear();
            }
            Boolean known = KEPT_AWAY.get(structure);
            if (known == null) {
                ResourceLocation id = registry.getKey(structure);
                known = id != null && patterns.stream().anyMatch(pattern -> pattern.matcher(id.toString()).matches());
                KEPT_AWAY.put(structure, known);
            }
            return known;
        }
    }

    /** New registries: forget what was matched. */
    public static void reset() {
        synchronized (KEPT_AWAY) {
            cachedFor = List.of();
            KEPT_AWAY.clear();
        }
        logged = false;
    }
}
