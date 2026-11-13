package com.example.thepale.worldgen;

import com.example.thepale.config.CommonConfig;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
 * (Oritech's ores and New Age's thorium by default) generates on the frozen island and nowhere else in the Overworld,
 * and everything in hotIslandOnlyFeatures (Oritech's oil springs) only on the hot island, so the materials that need
 * them are something you earn by crossing the Pale.
 */
public final class IslandFeatures {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Which island each placed feature belongs to, or none; worked out once per feature (identity: PlacedFeature is a record). */
    private static final Map<PlacedFeature, Optional<PaleShape.Kind>> HOME = new IdentityHashMap<>();
    private static List<String> cachedFrozen = List.of();
    private static List<String> cachedHot = List.of();
    private static List<Pattern> frozenPatterns = List.of();
    private static List<Pattern> hotPatterns = List.of();

    private IslandFeatures() {}

    /** {@return whether this feature may be placed in the chunk whose decoration starts at origin} */
    public static boolean allowed(PlacedFeature feature, WorldGenLevel level, BlockPos origin) {
        if (level.getLevel().dimension() != Level.OVERWORLD) {
            return true;
        }
        PaleShape shape = PaleShape.server();
        if (shape == null || shape.islands().size() < 2) {
            return true;
        }
        Optional<PaleShape.Kind> home = home(feature, level.registryAccess().registryOrThrow(Registries.PLACED_FEATURE));
        if (home.isEmpty()) {
            return true;
        }
        // Decoration runs once per chunk from its corner; judge by the chunk's middle. The new lands of the finale
        // belong to no island: everything generates there.
        return PaleCleanse.inNewLands(origin.getX() + 8, origin.getZ() + 8)
                || shape.nearest(origin.getX() + 8, origin.getZ() + 8).kind() == home.get();
    }

    private static Optional<PaleShape.Kind> home(PlacedFeature feature, Registry<PlacedFeature> registry) {
        synchronized (HOME) {
            refreshPatterns();
            Optional<PaleShape.Kind> known = HOME.get(feature);
            if (known == null) {
                ResourceLocation id = registry.getKey(feature);
                known = id == null ? Optional.empty() : home(id);
                HOME.put(feature, known);
            }
            return known;
        }
    }

    private static Optional<PaleShape.Kind> home(ResourceLocation id) {
        if (matches(id, frozenPatterns)) {
            return Optional.of(PaleShape.Kind.COLD);
        }
        if (matches(id, hotPatterns)) {
            return Optional.of(PaleShape.Kind.HOT);
        }
        return Optional.empty();
    }

    /** Recompiles the patterns (and forgets earlier answers) when the config has changed. */
    private static void refreshPatterns() {
        CommonConfig.Settings config = CommonConfig.get();
        if (!config.frozenIslandOnlyFeatures().equals(cachedFrozen) || !config.hotIslandOnlyFeatures().equals(cachedHot)) {
            frozenPatterns = compile(config.frozenIslandOnlyFeatures());
            hotPatterns = compile(config.hotIslandOnlyFeatures());
            cachedFrozen = config.frozenIslandOnlyFeatures();
            cachedHot = config.hotIslandOnlyFeatures();
            HOME.clear();
        }
    }

    private static List<Pattern> compile(List<String> lines) {
        List<Pattern> compiled = new ArrayList<>();
        for (String line : lines) {
            if (!line.isBlank()) {
                compiled.add(glob(line.trim()));
            }
        }
        return compiled;
    }

    private static boolean matches(ResourceLocation id, List<Pattern> patterns) {
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
        synchronized (HOME) {
            cachedFrozen = List.of(); // the registry is new: start over
            cachedHot = List.of();
            refreshPatterns();
        }
        if (frozenPatterns.isEmpty() && hotPatterns.isEmpty()) {
            return;
        }
        if (PaleShape.server().islands().size() < 2) {
            LOGGER.info("[thepale] One-island world: frozenIslandOnlyFeatures and hotIslandOnlyFeatures have nothing to do");
            return;
        }
        Registry<PlacedFeature> registry = server.registryAccess().registryOrThrow(Registries.PLACED_FEATURE);
        report(registry, frozenPatterns, cachedFrozen, "frozenIslandOnlyFeatures", "frozen");
        report(registry, hotPatterns, cachedHot, "hotIslandOnlyFeatures", "hot");
    }

    private static void report(Registry<PlacedFeature> registry, List<Pattern> patterns, List<String> lines, String setting, String island) {
        if (patterns.isEmpty()) {
            return;
        }
        List<String> found = new ArrayList<>();
        for (ResourceLocation id : registry.keySet()) {
            if (matches(id, patterns)) {
                found.add(id.toString());
            }
        }
        found.sort(null);
        if (found.isEmpty()) {
            LOGGER.warn("[thepale] {} {} matches no placed feature in this game", setting, lines);
        } else {
            LOGGER.info("[thepale] Only on the {} island: {}", island, String.join(", ", found));
        }
    }
}
