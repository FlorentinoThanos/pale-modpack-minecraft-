package com.example.thepale.worldgen;

import com.example.thepale.config.CommonConfig;
import com.mojang.serialization.MapCodec;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.DensityFunction;

/**
 * {@code thepale:radial_falloff}: pulls {@code argument} (the continentalness) toward the ocean the further a column
 * is from (0, 0): {@code target + falloff * (argument - target)}. The falloff is 1 up to landFalloffStartRadius and
 * eases down to oceanVariability at landEndRadius, then stays there; target is oceanContinentalness. All four come
 * from the common config. Same idea as Oceanus by Lizdtre (MIT), without its More Density Functions dependency.
 */
public record RadialFalloff(DensityFunction argument) implements DensityFunction {
    public static final MapCodec<RadialFalloff> DATA_CODEC =
            DensityFunction.HOLDER_HELPER_CODEC.fieldOf("argument").xmap(RadialFalloff::new, RadialFalloff::argument);
    public static final KeyDispatchDataCodec<RadialFalloff> CODEC = KeyDispatchDataCodec.of(DATA_CODEC);
    /** Continentalness an island's core is lifted to at least: vanilla's "mid-inland", low land. */
    private static final double CORE_CONTINENTALNESS = 0.08;
    /** Blocks over which the lift comes in, inward from where the land starts falling off. */
    private static final double LAND_CORE_DEPTH = 350;

    @Override
    public double compute(FunctionContext context) {
        double value = argument.compute(context);
        CommonConfig.Settings config = CommonConfig.get();
        double x = context.blockX();
        double z = context.blockZ();
        PaleShape shape = PaleShape.server();
        double radius = shape.effectiveRadius(x, z);
        if (shape.raisesLand() && radius < config.landFalloffStart()) {
            // An island can lie where the seed has open sea: lift the sea floor in its core, so the middle of every
            // island is land. Where the seed already has land, this changes nothing.
            double core = Mth.clamp((config.landFalloffStart() - radius) / LAND_CORE_DEPTH, 0.0, 1.0);
            core = core * core * (3.0 - 2.0 * core);
            value = Mth.lerp(core, value, smoothMax(value, CORE_CONTINENTALNESS, 0.06));
        }
        double t = Mth.clamp((radius - config.landFalloffStart()) / (config.landEnd() - config.landFalloffStart()), 0.0, 1.0);
        // Smoothstep rather than Oceanus' straight line, so the terrain has no crease where the band starts and ends.
        double falloff = 1.0 - (1.0 - config.oceanVariability()) * t * t * (3.0 - 2.0 * t);
        double target = config.oceanContinentalness();
        return target + falloff * (value - target);
    }

    /** A max without a crease where the two cross: within about {@code width} of each other it rounds off. */
    private static double smoothMax(double a, double b, double width) {
        return 0.5 * (a + b + Math.sqrt((a - b) * (a - b) + width * width));
    }

    @Override
    public void fillArray(double[] array, ContextProvider contextProvider) {
        contextProvider.fillAllDirectly(array, this);
    }

    @Override
    public DensityFunction mapAll(Visitor visitor) {
        return visitor.apply(new RadialFalloff(argument.mapAll(visitor)));
    }

    // The result is always between the argument and the target, since the falloff stays within 0..1.
    @Override
    public double minValue() {
        return Math.min(argument.minValue(), CommonConfig.get().oceanContinentalness());
    }

    @Override
    public double maxValue() {
        return Math.max(argument.maxValue(), CommonConfig.get().oceanContinentalness());
    }

    @Override
    public KeyDispatchDataCodec<? extends DensityFunction> codec() {
        return CODEC;
    }
}
