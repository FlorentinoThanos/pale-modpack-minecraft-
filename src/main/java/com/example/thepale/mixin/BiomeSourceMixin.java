package com.example.thepale.mixin;

import com.example.thepale.worldgen.PaleBiomeSource;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Lists the Pale biome among the Overworld's possible biomes. Feature ordering and structure placement are built
 * from this set; a biome missing from it breaks both. Hooked on the result rather than the memoised collection, so
 * it works even if the set was first asked for before the Pale biome was switched on.
 */
@Mixin(BiomeSource.class)
public abstract class BiomeSourceMixin {
    @Inject(method = "possibleBiomes", at = @At("RETURN"), cancellable = true)
    private void thepale$includePale(CallbackInfoReturnable<Set<Holder<Biome>>> cir) {
        if ((Object) this instanceof PaleBiomeSource source && source.thepale$paleBiome() != null) {
            cir.setReturnValue(source.thepale$withPale(cir.getReturnValue()));
        }
    }
}
