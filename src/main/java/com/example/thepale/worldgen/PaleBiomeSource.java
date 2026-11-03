package com.example.thepale.worldgen;

import java.util.Set;
import net.minecraft.core.Holder;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/** Added to MultiNoiseBiomeSource by a mixin. Only the Overworld's source gets a Pale biome set; others keep null. */
public interface PaleBiomeSource {
    void thepale$setPaleBiome(@Nullable Holder<Biome> biome);

    @Nullable
    Holder<Biome> thepale$paleBiome();

    /** {@code possible} plus the Pale biome, cached (possible is itself cached by BiomeSource). */
    Set<Holder<Biome>> thepale$withPale(Set<Holder<Biome>> possible);
}
