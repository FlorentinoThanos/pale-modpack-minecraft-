package com.example.thepale.worldgen;

import com.example.thepale.ThePale;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Registers the mod's worldgen types. The worldgen data itself is in data/minecraft and data/thepale. */
public final class PaleWorldgen {
    private static final DeferredRegister<MapCodec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, ThePale.MOD_ID);

    static {
        DENSITY_FUNCTION_TYPES.register("radial_falloff", () -> RadialFalloff.DATA_CODEC);
    }

    private PaleWorldgen() {}

    public static void register(IEventBus modBus) {
        DENSITY_FUNCTION_TYPES.register(modBus);
    }
}
