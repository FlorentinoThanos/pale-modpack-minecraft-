package com.example.thepale.worldgen;

import com.example.thepale.config.CommonConfig;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Climate;

/**
 * The island's climate. One island: zones across it, cold in the north (-Z), hot in the south (+Z), wet in the west
 * (-X) and dry in the east (+X). Three islands: each its own climate (mild, hot, frozen; see {@link PaleShape.Kind}),
 * wet to the west and dry to the east on every one. The temperature and humidity the Overworld's biome source picks biomes by are pulled toward what the
 * place on the island calls for, so the zones are there on every seed while the seed's own noise still varies them
 * locally (the natural patchwork of biomes stays). Terrain is untouched: it doesn't depend on either.
 */
public final class PaleClimate {
    /** Temperature the far north and the far south are pulled toward. Colder than vanilla's "snowy" (-0.45) by a margin. */
    private static final double NORTH_TEMPERATURE = -1.6;
    private static final double SOUTH_TEMPERATURE = 1.0;
    /** Humidity the far west (+) and the far east (-) are pulled toward. Vanilla's driest band is under -0.35. */
    private static final double SIDE_HUMIDITY = 1.4;
    /**
     * How much hotter the dry south-east is than the wet south-west. Without it the whole south would turn to desert,
     * which in vanilla is what "hot" means whatever the humidity, and the jungles (warm and wet) would go.
     */
    private static final double SOUTH_EAST_HEAT = 0.35;
    /**
     * The seed's temperature and humidity noise is so large-scale that on an island it barely bends the zone borders,
     * which would come out as straight lines (most visibly in the ocean). The seed's medium-scale terrain noise
     * (weirdness for temperature, erosion for humidity) bends them instead, by up to this much.
     */
    private static final double BORDER_WOBBLE = 0.3;

    // Three islands: each island's own climate. Vanilla's bands: under -0.45 frozen, -0.45 to -0.15 cold, to 0.2
    // temperate, to 0.55 warm (jungle when wet, savanna when dry), over 0.55 hot (desert).
    /** How far across an island (as a share of its radius) its climate reaches its extremes. */
    private static final double ISLAND_CLIMATE_SPAN = 0.8;
    private static final double MILD_TEMPERATURE = 0.0;
    /** Middle of the hot island; its west comes out warm (0.25 to 0.6 with everything added in), its east hot. */
    private static final double HOT_TEMPERATURE = 0.68;
    private static final double HOT_EAST_HEAT = 0.2;
    private static final double COLD_TEMPERATURE = -1.1;
    /** How hard the hot island is pulled to its climate, at least, and how much its borders bend. */
    private static final double THEMED_PULL = 0.88;
    private static final double THEMED_WOBBLE = 0.12;

    private PaleClimate() {}

    /** {@return the climate at this block column, pulled toward its zone on the island} */
    public static Climate.TargetPoint shift(Climate.TargetPoint point, int blockX, int blockZ) {
        return shift(point, blockX, blockZ, 1);
    }

    /** The same, with the pull scaled by {@code amount} (0 to 1): the zones fade out toward the new lands of the finale. */
    public static Climate.TargetPoint shift(Climate.TargetPoint point, int blockX, int blockZ, double amount) {
        CommonConfig.Settings config = CommonConfig.get();
        double temperatureZones = config.temperatureZones() * amount;
        double humidityZones = config.humidityZones() * amount;
        if (temperatureZones == 0 && humidityZones == 0) {
            return point;
        }
        PaleShape.Island island = PaleShape.server().nearest(blockX, blockZ);
        // -1 to 1 across the island; the sign of the strength picks which side is which.
        double scale = island.kind() == PaleShape.Kind.SINGLE ? config.climateRadius() : island.radius() * ISLAND_CLIMATE_SPAN;
        double south = Mth.clamp((blockZ - island.centerZ()) / scale, -1.0, 1.0) * Math.signum(temperatureZones);
        double east = Mth.clamp((blockX - island.centerX()) / scale, -1.0, 1.0) * Math.signum(humidityZones);
        double targetTemperature = switch (island.kind()) {
            case SINGLE -> {
                double warmth = south + SOUTH_EAST_HEAT * east * Math.max(south, 0.0);
                yield warmth < 0 ? -warmth * NORTH_TEMPERATURE : warmth * SOUTH_TEMPERATURE;
            }
            // Temperate, a touch cooler to the north and warmer to the south.
            case MILD -> MILD_TEMPERATURE + 0.15 * south;
            // Warm and wet to the west (jungle), hot and dry to the east (desert, badlands); never temperate.
            case HOT -> HOT_TEMPERATURE + HOT_EAST_HEAT * east;
            // Frozen; its southern shore only just.
            case COLD -> COLD_TEMPERATURE + 0.35 * south;
        };
        // The hot island keeps to its climate: pulled harder, with less of the seed's own variety, so no corner of it
        // falls into a cooler band (taiga on the hot island, say).
        boolean themed = island.kind() == PaleShape.Kind.HOT;
        double temperatureStrength = themed ? Math.max(Math.abs(temperatureZones), THEMED_PULL * amount) : Math.abs(temperatureZones);
        targetTemperature += (themed ? THEMED_WOBBLE : BORDER_WOBBLE) * Climate.unquantizeCoord(point.weirdness());
        double targetHumidity = -east * SIDE_HUMIDITY + BORDER_WOBBLE * Climate.unquantizeCoord(point.erosion());
        return new Climate.TargetPoint(
                pull(point.temperature(), targetTemperature, temperatureStrength),
                pull(point.humidity(), targetHumidity, Math.abs(humidityZones)),
                point.continentalness(),
                point.erosion(),
                point.depth(),
                point.weirdness());
    }

    private static long pull(long quantized, double target, double strength) {
        double value = Climate.unquantizeCoord(quantized);
        return Climate.quantizeCoord((float) (value + (target - value) * strength));
    }
}
