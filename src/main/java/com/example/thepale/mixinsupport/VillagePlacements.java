package com.example.thepale.mixinsupport;

import java.util.List;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Carried by a {@link net.minecraft.world.level.levelgen.RandomState}: the placements of the village structure sets
 * of the world it was made for, as world generation actually uses them (spread out and pinned). The structure start
 * check only sees the random state, so this is how it learns where villages go
 * ({@link com.example.thepale.worldgen.VillageClearance}).
 */
public interface VillagePlacements {
    List<RandomSpreadStructurePlacement> thepale$villagePlacements();

    void thepale$setVillagePlacements(List<RandomSpreadStructurePlacement> placements);
}
