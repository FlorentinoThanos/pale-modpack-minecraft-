package com.example.thepale.mixin;

import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads a placement's settings, to copy it with a different spacing ({@link com.example.thepale.worldgen.PaleStructures}). */
@Mixin(StructurePlacement.class)
public interface StructurePlacementAccessor {
    @Accessor("locateOffset")
    Vec3i thepale$locateOffset();

    @Accessor("frequencyReductionMethod")
    StructurePlacement.FrequencyReductionMethod thepale$frequencyReductionMethod();

    @Accessor("frequency")
    float thepale$frequency();

    @Accessor("salt")
    int thepale$salt();

    @Accessor("exclusionZone")
    Optional<StructurePlacement.ExclusionZone> thepale$exclusionZone();
}
