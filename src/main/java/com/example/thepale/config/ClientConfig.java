package com.example.thepale.config;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import org.jetbrains.annotations.Nullable;

/** Client config (config/thepale-client.toml): how the Pale looks. Every radius is measured from (0, 0). */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue INNER_RADIUS;
    private static final ModConfigSpec.DoubleValue OUTER_RADIUS;
    private static final ModConfigSpec.IntValue LAYER_COUNT;
    private static final ModConfigSpec.DoubleValue FOG_ENTRY_VISIBILITY;
    private static final ModConfigSpec.DoubleValue FOG_FULL_VISIBILITY;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> DIMENSIONS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        INNER_RADIUS = builder
                .comment("Radius of the innermost cylinder, in blocks. Going past it switches on the Pale camera fog.")
                .defineInRange("innerRadius", 1400.0, 1.0, 30_000_000.0);
        OUTER_RADIUS = builder
                .comment(
                        "Radius of the outermost cylinder, in blocks. The camera fog and the ash are at their thickest from here on.",
                        "Treated as innerRadius + 1 if set lower than that.")
                .defineInRange("outerRadius", 2000.0, 1.0, 30_000_000.0);
        LAYER_COUNT = builder
                .comment("Number of cylinders, spaced evenly from innerRadius to outerRadius.")
                .defineInRange("layerCount", 20, 1, 64);
        FOG_ENTRY_VISIBILITY = builder
                .comment(
                        "How far you can see just inside innerRadius, in blocks (about what the Basalt Deltas give you).",
                        "The fog closes in to this over the last 120 blocks before innerRadius.")
                .defineInRange("fogEntryVisibility", 96.0, 2.0, 1024.0);
        FOG_FULL_VISIBILITY = builder
                .comment("How far you can see at outerRadius and beyond, in blocks. In between the fog keeps getting thicker.")
                .defineInRange("fogFullVisibility", 6.0, 1.0, 1024.0);
        DIMENSIONS = builder
                .comment("Dimensions the wall and its fog appear in.")
                .defineListAllowEmpty(
                        "dimensions",
                        List.of("minecraft:overworld"),
                        () -> "minecraft:overworld",
                        value -> value instanceof String s && ResourceLocation.tryParse(s) != null);
        SPEC = builder.build();
    }

    /** Snapshot of the config, rebuilt whenever the file is loaded or changed. */
    public record Settings(
            double innerRadius,
            double outerRadius,
            int layerCount,
            double fogEntryVisibility,
            double fogFullVisibility,
            Set<ResourceLocation> dimensions) {
        /** Radius of cylinder {@code layer}, where 0 is the innermost. */
        public double layerRadius(int layer) {
            return layerCount == 1 ? innerRadius : innerRadius + (outerRadius - innerRadius) * layer / (layerCount - 1);
        }

        public boolean appliesTo(Level level) {
            return dimensions.contains(level.dimension().location());
        }
    }

    @Nullable
    private static volatile Settings current;

    /** {@return the current settings, or null before the config file has been loaded} */
    @Nullable
    public static Settings get() {
        return current;
    }

    public static void onLoad(ModConfigEvent.Loading event) {
        refresh(event.getConfig());
    }

    public static void onReload(ModConfigEvent.Reloading event) {
        refresh(event.getConfig());
    }

    private static void refresh(ModConfig config) {
        if (config.getSpec() != SPEC) {
            return;
        }
        double inner = INNER_RADIUS.get();
        Set<ResourceLocation> dimensions = DIMENSIONS.get().stream()
                .map(ResourceLocation::tryParse)
                .filter(Objects::nonNull)
                .collect(Collectors.toUnmodifiableSet());
        current = new Settings(
                inner,
                Math.max(OUTER_RADIUS.get(), inner + 1.0),
                LAYER_COUNT.get(),
                FOG_ENTRY_VISIBILITY.get(),
                Math.min(FOG_FULL_VISIBILITY.get(), FOG_ENTRY_VISIBILITY.get()),
                dimensions);
    }
}
