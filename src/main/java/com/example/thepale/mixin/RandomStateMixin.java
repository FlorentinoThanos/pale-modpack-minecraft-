package com.example.thepale.mixin;

import com.example.thepale.mixinsupport.VillagePlacements;
import java.util.List;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Lets a random state carry its world's village placements ({@link VillagePlacements}). */
@Mixin(RandomState.class)
public abstract class RandomStateMixin implements VillagePlacements {
    @Unique
    private volatile List<RandomSpreadStructurePlacement> thepale$villages = List.of();

    @Override
    public List<RandomSpreadStructurePlacement> thepale$villagePlacements() {
        return thepale$villages;
    }

    @Override
    public void thepale$setVillagePlacements(List<RandomSpreadStructurePlacement> placements) {
        thepale$villages = List.copyOf(placements);
    }
}
