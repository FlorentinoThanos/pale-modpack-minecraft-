package com.example.thepale.worldgen;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Climate;
import net.neoforged.neoforge.common.Tags;
import org.slf4j.Logger;

/**
 * The biomes the mild island may have. Pulling the climate toward temperate ({@link PaleClimate}) still leaves the
 * seed's own noise some say, and vanilla (and Terralith, and others) put snowy peaks and slopes on temperate
 * mountains anyway, so on the mild island the biome is picked only from the temperate entries of the Overworld's
 * climate table: the nearest temperate biome to the climate there, whichever mod adds it. An entry is temperate when
 * the temperatures it's placed at reach into vanilla's temperate band (that's where each mod itself means it to go:
 * Terralith's hot shrubland, say, has a mild 0.8 of its own but sits in the hot band), and its biome doesn't snow
 * (snowy peaks and slopes, groves: placed in the temperate band too) and isn't tagged a hot or icy one, by the common
 * tags or by Terralith's own temperature tags (its alpine highlands are placed in the temperate band but snow-capped).
 */
public final class MildBiomes {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Below this a biome snows at sea level (0.15) or on its hills: snowy plains and taiga, groves, snowy slopes and
     * peaks, windswept hills (0.2). Taiga (0.25) stays, as the island's cool north.
     */
    private static final float MIN_TEMPERATURE = 0.25F;
    /** Above this a biome is hot whatever band it's placed in: desert, savanna, badlands, mesas (2.0). */
    private static final float MAX_TEMPERATURE = 1.0F;
    /** Vanilla's temperate band of the climate's temperature: colder is taiga and snow, warmer jungle and savanna. */
    private static final long TEMPERATE_MIN = Climate.quantizeCoord(-0.15F);
    private static final long TEMPERATE_MAX = Climate.quantizeCoord(0.2F);

    /** Terralith's own sorting of its biomes by temperature; the ones here aren't temperate. Empty without Terralith. */
    private static final List<TagKey<Biome>> NOT_TEMPERATE = List.of(
            terralith("reference/temperature/cold"),
            terralith("reference/temperature/frozen_all"),
            terralith("reference/temperature/lukewarm"),
            terralith("reference/temperature/warm"));

    private MildBiomes() {}

    private static TagKey<Biome> terralith(String path) {
        return TagKey.create(Registries.BIOME, ResourceLocation.fromNamespaceAndPath("terralith", path));
    }

    public static boolean isMild(Holder<Biome> holder) {
        if (holder.is(Tags.Biomes.IS_SNOWY) || holder.is(Tags.Biomes.IS_ICY)
                || holder.is(Tags.Biomes.IS_DESERT) || holder.is(Tags.Biomes.IS_BADLANDS)
                || holder.is(Tags.Biomes.IS_SAVANNA)) {
            return false;
        }
        for (TagKey<Biome> tag : NOT_TEMPERATE) {
            if (holder.is(tag)) {
                return false;
            }
        }
        Biome biome = holder.value();
        Biome.ClimateSettings climate = biome.getModifiedClimateSettings();
        if (climate.temperatureModifier() == Biome.TemperatureModifier.FROZEN) {
            return false; // frozen and deep frozen ocean
        }
        float temperature = climate.temperature();
        return temperature >= MIN_TEMPERATURE && temperature <= MAX_TEMPERATURE;
    }

    /** {@return the entries of {@code all} with a mild biome, or {@code all} itself if none is} */
    public static Climate.ParameterList<Holder<Biome>> filter(Climate.ParameterList<Holder<Biome>> all) {
        List<Pair<Climate.ParameterPoint, Holder<Biome>>> kept = new ArrayList<>();
        int dropped = 0;
        for (Pair<Climate.ParameterPoint, Holder<Biome>> entry : all.values()) {
            Climate.Parameter temperature = entry.getFirst().temperature();
            boolean temperate = temperature.min() < TEMPERATE_MAX && temperature.max() > TEMPERATE_MIN;
            if (temperate && isMild(entry.getSecond())) {
                kept.add(entry);
            } else {
                dropped++;
            }
        }
        if (kept.isEmpty()) {
            LOGGER.warn("[thepale] No temperate biome in the Overworld's climate table; the mild island keeps them all");
            return all;
        }
        LOGGER.info("[thepale] The mild island picks from {} temperate climate entries ({} cold or hot ones left out)", kept.size(), dropped);
        return new Climate.ParameterList<>(kept);
    }
}
