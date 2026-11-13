package com.example.thepale.client;

import com.example.thepale.config.ClientConfig;
import com.example.thepale.worldgen.PaleShape;
import com.example.thepale.worldgen.PaleBiomes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.util.FastColor;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;

/**
 * The water colour on the way into the Pale: it turns from the ocean's blue to the Pale grey over the last
 * {@link #BLEND} blocks before innerRadius, whatever biome the water is in. Called from chunk-building threads.
 */
public final class PaleWaterTint {
    private static final double BLEND = 250;
    /** The Pale biome's own water colour (the_pale.json), reached at innerRadius. */
    private static final int PALE_WATER = 0x8C8F94;
    /** What the Pale biome's water blends from: plain ocean blue, as if the ocean carried on. */
    private static final int OCEAN_WATER = 0x3F76E4;

    /** The Pale biome as the current level's registries have it, looked up again when they change. */
    private record PaleLookup(RegistryAccess registries, @Nullable Biome pale) {}

    @Nullable
    private static volatile PaleLookup lookup;

    private PaleWaterTint() {}

    public static int tint(Biome biome, int color, double x, double z) {
        ClientConfig.Settings config = ClientConfig.get();
        ClientLevel level = Minecraft.getInstance().level;
        if (config == null || level == null || !config.appliesTo(level)) {
            return color;
        }
        double start = config.innerRadius() - BLEND;
        double radius = PaleShape.client().effectiveRadius(x, z);
        if (radius <= start) {
            return color;
        }
        int from = biome == paleBiome(level) ? OCEAN_WATER : color;
        float t = (float) Mth.clamp((radius - start) / BLEND, 0, 1);
        t = t * t * (3f - 2f * t) * com.example.thepale.finale.FinaleView.paleStrength();
        return FastColor.ARGB32.lerp(t, from, PALE_WATER) & 0xFFFFFF;
    }

    @Nullable
    private static Biome paleBiome(ClientLevel level) {
        RegistryAccess registries = level.registryAccess();
        PaleLookup current = lookup;
        if (current == null || current.registries() != registries) {
            current = new PaleLookup(registries, registries.registryOrThrow(Registries.BIOME).get(PaleBiomes.THE_PALE));
            lookup = current;
        }
        return current.pale();
    }
}
