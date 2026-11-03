package com.example.thepale.worldgen;

import com.example.thepale.config.CommonConfig;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.slf4j.Logger;

/**
 * Structure placement on a small island, for the Overworld:
 * <ul>
 *   <li>A structure set places at most one structure per square of {@code spacing} chunks, and some use squares wider
 *       than the island (woodland mansions 80 chunks, Born in Chaos up to 200), so on most seeds they never get a try
 *       on land. Sets spaced wider than maxStructureSpacing are tightened to it. Their biome rules stay, so each still
 *       only appears where it belongs.
 *   <li>The world's {@link StructurePins} move single tries to chunks where a missing structure fits.
 * </ul>
 */
public final class PaleStructures {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static volatile boolean logged;
    private static final int STRONGHOLDS = 3;

    private PaleStructures() {}

    public static List<Holder<StructureSet>> adjust(List<Holder<StructureSet>> sets) {
        int max = CommonConfig.get().maxStructureSpacing();
        Map<ResourceLocation, Map<Long, ChunkPos>> pins = StructurePins.current();
        List<Holder<StructureSet>> result = new ArrayList<>(sets.size());
        int spread = 0;
        for (Holder<StructureSet> holder : sets) {
            StructureSet set = holder.value();
            if (!(set.placement() instanceof RandomSpreadStructurePlacement placement)) {
                result.add(holder);
                continue;
            }
            int spacing = spacing(placement);
            Map<Long, ChunkPos> setPins = holder.unwrapKey().map(key -> pins.get(key.location())).orElse(null);
            if (spacing == placement.spacing() && setPins == null) {
                result.add(holder);
                continue;
            }
            if (spacing != placement.spacing()) {
                spread++;
            }
            PinnedSpreadPlacement adjusted = new PinnedSpreadPlacement(
                    placement, spacing, separation(placement, spacing), setPins == null ? Map.of() : setPins);
            result.add(Holder.direct(new StructureSet(set.structures(), adjusted)));
        }
        if (!logged) {
            logged = true;
            LOGGER.info("[thepale] Spread out {} rare structure sets in the Overworld (spacing over {} chunks)", spread, max);
        }
        return List.copyOf(result);
    }

    /** {@return the spacing a set gets in the Overworld} */
    static int spacing(RandomSpreadStructurePlacement placement) {
        int max = CommonConfig.get().maxStructureSpacing();
        return max > 0 && placement.spacing() > max ? max : placement.spacing();
    }

    /** Keeps the gap between neighbours in proportion, and always below the new spacing. */
    static int separation(RandomSpreadStructurePlacement placement, int spacing) {
        if (spacing == placement.spacing()) {
            return placement.separation();
        }
        return Math.max(0, Math.min(spacing - 1, Math.round((float) placement.separation() * spacing / placement.spacing())));
    }

    /** {@return where the strongholds go on the hot island: three, half way out from its centre, a third of a turn apart} */
    public static List<ChunkPos> strongholds(PaleShape.Island island) {
        List<ChunkPos> chunks = new ArrayList<>();
        for (int i = 0; i < STRONGHOLDS; i++) {
            double angle = Math.toRadians(30 + 120 * i);
            double distance = island.radius() * island.factor(angle) * 0.5;
            chunks.add(new ChunkPos(
                    Math.floorDiv((int) Math.round(island.centerX() + Math.cos(angle) * distance), 16),
                    Math.floorDiv((int) Math.round(island.centerZ() + Math.sin(angle) * distance), 16)));
        }
        return List.copyOf(chunks);
    }

    static void resetLog() {
        logged = false;
    }
}
