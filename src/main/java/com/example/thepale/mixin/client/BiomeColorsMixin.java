package com.example.thepale.mixin.client;

import com.example.thepale.client.PaleWaterTint;
import net.minecraft.client.renderer.BiomeColors;
import net.minecraft.world.level.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Greys the water out gradually with distance from (0, 0), instead of all at once where the Pale biome starts. The
 * target is the lambda behind {@link BiomeColors#WATER_COLOR_RESOLVER}, which both vanilla and Sodium call. Optional:
 * without it the water just changes colour at the biome edge.
 */
@Mixin(BiomeColors.class)
public abstract class BiomeColorsMixin {
    @Inject(method = "lambda$static$1(Lnet/minecraft/world/level/biome/Biome;DD)I", at = @At("RETURN"), cancellable = true, require = 0)
    private static void thepale$fadeWaterToPale(Biome biome, double x, double z, CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(PaleWaterTint.tint(biome, cir.getReturnValueI(), x, z));
    }
}
