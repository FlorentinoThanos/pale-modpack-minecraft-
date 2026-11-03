package com.example.thepale.mixin;

import com.example.thepale.worldgen.PaleBiomeSource;
import com.example.thepale.worldgen.PaleBiomes;
import com.example.thepale.worldgen.PaleClimate;
import com.google.common.collect.ImmutableSet;
import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Past paleBiomeRadius the Overworld's biome source answers the Pale biome, whatever the climate says; inside it, the
 * climate is shifted into zones ({@link PaleClimate}).
 */
@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin implements PaleBiomeSource {
    @Shadow
    public abstract Holder<Biome> getNoiseBiome(Climate.TargetPoint target);

    @Unique
    @Nullable
    private volatile Holder<Biome> thepale$paleBiome;

    @Unique
    @Nullable
    private volatile Set<Holder<Biome>> thepale$possibleWithPale;

    @Override
    public void thepale$setPaleBiome(@Nullable Holder<Biome> biome) {
        thepale$paleBiome = biome;
        thepale$possibleWithPale = null;
    }

    @Override
    @Nullable
    public Holder<Biome> thepale$paleBiome() {
        return thepale$paleBiome;
    }

    @Override
    public Set<Holder<Biome>> thepale$withPale(Set<Holder<Biome>> possible) {
        Set<Holder<Biome>> cached = thepale$possibleWithPale;
        Holder<Biome> pale = thepale$paleBiome;
        if (cached == null && pale != null) {
            cached = ImmutableSet.<Holder<Biome>>builder().addAll(possible).add(pale).build();
            thepale$possibleWithPale = cached;
        }
        return cached != null ? cached : possible;
    }

    @Inject(
            method = "getNoiseBiome(IIILnet/minecraft/world/level/biome/Climate$Sampler;)Lnet/minecraft/core/Holder;",
            at = @At("HEAD"),
            cancellable = true)
    private void thepale$paleBeyondRadius(int x, int y, int z, Climate.Sampler sampler, CallbackInfoReturnable<Holder<Biome>> cir) {
        Holder<Biome> pale = thepale$paleBiome;
        if (pale == null) {
            return; // not the Overworld
        }
        // Quart coordinates; +2 for the centre of the 4x4 cell.
        int blockX = QuartPos.toBlock(x) + 2;
        int blockZ = QuartPos.toBlock(z) + 2;
        if (PaleBiomes.isInPale(blockX, blockZ)) {
            cir.setReturnValue(pale);
        } else {
            cir.setReturnValue(getNoiseBiome(PaleClimate.shift(sampler.sample(x, y, z), blockX, blockZ)));
        }
    }
}
