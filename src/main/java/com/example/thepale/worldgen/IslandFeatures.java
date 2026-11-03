package com.example.thepale.worldgen;

import com.example.thepale.config.CommonConfig;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;

/**
 * Features that belong to one island only. With three islands, everything in the config's frozenIslandOnlyFeatures
 * (Oritech's ores by default) generates on the frozen island and nowhere else in the Overworld, so the metals that
 * need them are something you earn by crossing the Pale.
 */
public final class IslandFeatures {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Whether each placed feature is frozen-only, worked out once per feature (identity: PlacedFeature is a record). */
    private static final Map<PlacedFeature, Boolean> FROZEN_ONLY = new IdentityHashMap<>();
    private static List<String> cachedFor = List.of();
    private static List<Pattern> patterns = List.of();

    private IslandFeatures() {}

    /** {@return whether this feature may be placed in the chunk whose decoration starts at origin} */
    public static boolean allowed(PlacedFeature feature, WorldGenLevel level, BlockPos origin) {
        if (level.getLevel().dimension() != Level.OVERWORLD) {
            return true;
        }
        PaleShape shape = PaleShape.server();
        if (shape == null || shape.islands().size() < 2 || !frozenOnly(feature, level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE))) {
            return true;
        }
        // Decoration runs once per chunk from its corner; judge by the chunk's middle.
        return shape.nearest(origin.getX() + 8, origin.getZ() + 8).kind() == PaleShape.Kind.COLD;
    }

    private static boolean frozenOnly(PlacedFeature feature, Registry<PlacedFeature> registry) {
        synchronized (FROZEN_ONLY) {
            refreshPatterns();
            Boolean known = FROZEN_ONLY.get(feature);
            if (known == null) {
                ResourceLocation id = registry.getKey(feature);
                known = id != null && matches(id);
                FROZEN_ONLY.put(feature, known);
            }
            return known;
        }
    }

    /** Recompiles the patterns (and forgets earlier answers) when the config has changed. */
    private static void refreshPatterns() {
        List<String> wanted = CommonConfig.get().frozenIslandOnlyFeatures();
        if (!wanted.equals(cachedFor)) {
            List<Pattern> compiled = new ArrayList<>();
            for (String line : wanted) {
                if (!line.isBlank()) {
                    compiled.add(glob(line.trim()));
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

    /** {@return a pattern for text where * matches any run of characters} */
    public static Pattern glob(String text) {
        StringBuilder regex = new StringBuilder();
        for (String part : text.split("\\*", -1)) {
            if (!regex.isEmpty()) {
                regex.append(".*");
            }
            regex.append(Pattern.quote(part));
        }
        return Pattern.compile(regex.toString());
    }

    /** Logs which features the config caught, so a pattern that matches nothing doesn't go unnoticed. */
    public static void onServerAboutToStart(MinecraftServer server) {
        synchronized (FROZEN_ONLY) {
            cachedFor = List.of(); // the registry is new: start over
            refreshPatterns();
        }
        if (patterns.isEmpty()) {
            return;
        }
        if (PaleShape.server().islands().size() < 2) {
            LOGGER.info("[thepale] One-island world: frozenIslandOnlyFeatures has nothing to do");
            return;
        }
        Registry<PlacedFeature> registry = server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        List<String> found = new ArrayList<>();
        for (ResourceLocation id : registry.keySet()) {
            if (matches(id)) {
                found.add(id.toString());
            }
        }
        found.sort(null);
        if (found.isEmpty()) {
            LOGGER.warn("[thepale] frozenIslandOnlyFeatures {} matches no placed feature in this game", cachedFor);
        } else {
            LOGGER.info("[thepale] Only on the frozen island: {}", String.join(", ", found));
        }
    }
}
