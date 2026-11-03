package com.example.thepale.mixin;

import com.example.thepale.worldgen.IslandFeatures;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skips frozen-island-only features (Oritech's ores by default) everywhere else; see {@link IslandFeatures}. */
@Mixin(PlacedFeature.class)
public abstract class PlacedFeatureMixin {
    @Inject(method = "placeWithBiomeCheck", at = @At("HEAD"), cancellable = true)
    private void thepale$onlyOnItsIsland(
            WorldGenLevel level, ChunkGenerator generator, RandomSource random, BlockPos origin, CallbackInfoReturnable<Boolean> cir) {
        if (!IslandFeatures.allowed((PlacedFeature) (Object) this, level, origin)) {
            cir.setReturnValue(false);
        }
    }
}
