package com.example.thepale.mixin;

import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.storage.PrimaryLevelData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Swaps a new world's seed before its levels are created ({@link com.example.thepale.worldgen.SeedSearch}). */
@Mixin(PrimaryLevelData.class)
public interface PrimaryLevelDataAccessor {
    @Mutable
    @Accessor("worldOptions")
    void thepale$setWorldOptions(WorldOptions options);
}
