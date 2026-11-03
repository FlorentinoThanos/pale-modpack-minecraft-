package com.example.thepale.worldgen;

import com.example.thepale.config.CommonConfig;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.slf4j.Logger;

/**
 * Structures that belong to the frozen island only (Born in Chaos's dark towers by default). Checked where every
 * structure start is made, so world generation, the seed search, the pins and /thepale survey all agree.
 */
public final class IslandStructures {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final Map<Structure, Boolean> FROZEN_ONLY = new IdentityHashMap<>();
    private static List<String> cachedFor = List.of();
    private static List<Pattern> patterns = List.of();

    private IslandStructures() {}

    /** {@return whether the structure may start in this chunk} */
    public static boolean allowed(Structure structure, RegistryAccess registries, BiomeSource biomes, ChunkPos chunk) {
        // Only the Overworld's biome source has the Pale switched on; the Nether's and the End's are left alone.
        if (!(biomes instanceof PaleBiomeSource pale) || pale.thepale$paleBiome() == null) {
            return true;
        }
        PaleShape shape = PaleShape.server();
        if (shape == null || shape.islands().size() < 2 || !frozenOnly(structure, registries.registryOrThrow(Registries.STRUCTURE))) {
            return true;
        }
        return shape.nearest(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()).kind() == PaleShape.Kind.COLD;
    }

    private static boolean frozenOnly(Structure structure, Registry<Structure> registry) {
        synchronized (FROZEN_ONLY) {
            refreshPatterns();
            Boolean known = FROZEN_ONLY.get(structure);
            if (known == null) {
                ResourceLocation id = registry.getKey(structure);
                known = id != null && matches(id);
                FROZEN_ONLY.put(structure, known);
            }
            return known;
        }
    }

    private static void refreshPatterns() {
        List<String> wanted = CommonConfig.get().frozenIslandOnlyStructures();
        if (!wanted.equals(cachedFor)) {
            List<Pattern> compiled = new ArrayList<>();
            for (String line : wanted) {
                if (!line.isBlank()) {
                    compiled.add(IslandFeatures.glob(line.trim()));
                }
            }
            patterns = compiled;
            cachedFor = wanted;
            FROZEN_ONLY.clear();
        }
    }

    private static boolean matches(ResourceLocation id) {
        String name = id.toString();
        for (Pattern pattern : patterns) {
            if (pattern.matcher(name).matches()) {
                return true;
            }
        }
        return false;
    }

    /** Logs which structures the config caught. */
    public static void onServerAboutToStart(MinecraftServer server) {
        synchronized (FROZEN_ONLY) {
            cachedFor = List.of(); // new registries: start over
            refreshPatterns();
        }
        if (patterns.isEmpty() || PaleShape.server().islands().size() < 2) {
            return;
        }
        List<String> found = new ArrayList<>();
        for (ResourceLocation id : server.registryAccess().registryOrThrow(Registries.STRUCTURE).keySet()) {
            if (matches(id)) {
                found.add(id.toString());
            }
        }
        found.sort(null);
        if (found.isEmpty()) {
            LOGGER.warn("[thepale] frozenIslandOnlyStructures {} matches no structure in this game", cachedFor);
        } else {
            LOGGER.info("[thepale] Structures only on the frozen island: {}", String.join(", ", found));
        }
    }
}
