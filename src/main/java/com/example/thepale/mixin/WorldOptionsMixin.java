package com.example.thepale.mixin;

import com.example.thepale.worldgen.SeedSearch;
import net.minecraft.world.level.levelgen.WorldOptions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Remembers every seed the game rolls itself (an empty seed field when creating a world, or an empty level-seed on
 * a server), so the seed search knows which seeds nobody chose and it may replace.
 */
@Mixin(WorldOptions.class)
public abstract class WorldOptionsMixin {
    @Inject(method = "randomSeed", at = @At("RETURN"))
    private static void thepale$rememberRandomSeed(CallbackInfoReturnable<Long> cir) {
        SeedSearch.rememberRandomSeed(cir.getReturnValueJ());
    }
}
