package com.example.thepale.config;

import java.util.List;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Common config (config/thepale-common.toml): the world itself, so the server's copy is the one that counts.
 * Every radius is measured from (0, 0) in a circle. Changing the world radii after chunks have generated leaves
 * seams between old and new terrain.
 */
public final class CommonConfig {
    public static final ModConfigSpec SPEC;

    private static final ModConfigSpec.DoubleValue LAND_FALLOFF_START;
    private static final ModConfigSpec.DoubleValue LAND_END;
    private static final ModConfigSpec.DoubleValue OCEAN_CONTINENTALNESS;
    private static final ModConfigSpec.DoubleValue OCEAN_VARIABILITY;
    private static final ModConfigSpec.DoubleValue PALE_BIOME_RADIUS;
    private static final ModConfigSpec.DoubleValue COAST_ROUGHNESS;
    private static final ModConfigSpec.BooleanValue THREE_ISLANDS;
    private static final ModConfigSpec.DoubleValue TEMPERATURE_ZONES;
    private static final ModConfigSpec.DoubleValue HUMIDITY_ZONES;
    private static final ModConfigSpec.DoubleValue CLIMATE_RADIUS;
    private static final ModConfigSpec.IntValue MAX_STRUCTURE_SPACING;
    private static final ModConfigSpec.BooleanValue SEARCH_SEED;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> REQUIRED_STRUCTURES;
    private static final ModConfigSpec.IntValue MAX_SEARCH_SECONDS;
    private static final ModConfigSpec.BooleanValue PIN_MISSING_STRUCTURES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FROZEN_ISLAND_ONLY_STRUCTURES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> KEEP_AWAY_FROM_VILLAGES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> VILLAGE_STRUCTURE_SETS;
    private static final ModConfigSpec.IntValue VILLAGE_CLEARANCE;
    private static final ModConfigSpec.DoubleValue POISON_START;
    private static final ModConfigSpec.DoubleValue POISON_FULL;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FROZEN_ISLAND_ONLY_FEATURES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> NOT_ON_STARTER_ISLAND;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FROZEN_ISLAND_ONLY_MOBS;
    private static final ModConfigSpec.ConfigValue<List<? extends Number>> RAMPAGE_SPEED_BONUS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.push("world");
        LAND_FALLOFF_START = builder
                .comment("Radius up to which terrain is untouched. From here land sinks into the ocean.")
                .defineInRange("landFalloffStartRadius", 1050.0, 0.0, 30_000_000.0);
        LAND_END = builder
                .comment("Radius where land ends. Past it there is only ocean. Treated as landFalloffStartRadius + 1 if lower.")
                .defineInRange("landEndRadius", 1250.0, 0.0, 30_000_000.0);
        OCEAN_CONTINENTALNESS = builder
                .comment(
                        "Continentalness the ocean past landEndRadius is pulled toward. -0.75 is deep ocean in vanilla and Terralith.",
                        "Do not go below -0.85: with Terralith, values under about -0.92 are its island zone and make land.")
                .defineInRange("oceanContinentalness", -0.75, -1.2, 1.0);
        OCEAN_VARIABILITY = builder
                .comment("How much of the normal continentalness still varies the ocean past landEndRadius (0 = flat target, 1 = no change).")
                .defineInRange("oceanVariability", 0.15, 0.0, 1.0);
        PALE_BIOME_RADIUS = builder
                .comment("Radius from which the Overworld is entirely the Pale biome.")
                .defineInRange("paleBiomeRadius", 1300.0, 0.0, 30_000_000.0);
        COAST_ROUGHNESS = builder
                .comment(
                        "How ragged the island's outline is: every radius here (and the fog wall's) is stretched or shrunk by up to",
                        "this share depending on the direction, in a pattern of lobes, bays and headlands that differs with the seed.",
                        "0 = a circle. Fixed for each world when it is created (saved in thepale-world.json); worlds from before",
                        "this setting stay round.")
                .defineInRange("coastRoughness", 0.25, 0.0, 0.6);
        THREE_ISLANDS = builder
                .comment(
                        "New worlds get three islands: a mild starter island at (0, 0), a hot one 3500 to 4500 blocks south and a",
                        "frozen one as far north, each 1000 to 1500 blocks in radius, with the Pale between them. false = one island.",
                        "With three islands the radii in this file are measured from each island's coast: landEndRadius is the",
                        "coast itself, paleBiomeRadius 50 past it, and so on. Fixed for each world when it is created.")
                .define("threeIslands", true);
        builder.pop();

        builder.push("climate");
        TEMPERATURE_ZONES = builder
                .comment(
                        "Cold north (-Z), hot south (+Z): how much the place on the island decides the temperature, from 0 (the seed",
                        "decides, as in vanilla) to 1 (only the place does). In between, the seed's noise still varies it locally.",
                        "The south-east ends up the hottest, so the wet south-west keeps its jungles. Negative swaps north and south.")
                .defineInRange("temperatureZones", 0.7, -1.0, 1.0);
        HUMIDITY_ZONES = builder
                .comment(
                        "Wet west (-X), dry east (+X): how much the place on the island decides the humidity, 0 to 1 as above.",
                        "Negative swaps west and east.")
                .defineInRange("humidityZones", 0.6, -1.0, 1.0);
        CLIMATE_RADIUS = builder
                .comment(
                        "Distance from (0, 0) at which the zones are at their extremes. Between there and the centre they change",
                        "evenly. Only affects newly generated chunks; already generated ones keep their biomes.")
                .defineInRange("climateRadius", 1000.0, 1.0, 30_000_000.0);
        builder.pop();

        builder.push("structures");
        MAX_STRUCTURE_SPACING = builder
                .comment(
                        "Overworld structures normally spaced wider than this many chunks apart (woodland mansions: 80, Born in Chaos:",
                        "up to 200) are spaced this far apart instead, so they get a few tries on the island. Their biome rules stay.",
                        "0 leaves every structure as it is. Only affects newly generated chunks.")
                .defineInRange("maxStructureSpacing", 40, 0, 4096);
        SEARCH_SEED = builder
                .comment(
                        "When a new world is created with a random seed (the seed field left empty), try seeds until one puts every",
                        "structure in requiredStructures on the island, and use it. A seed you type in is kept; the mod only tells you",
                        "what's missing on it. Existing worlds are never touched.")
                .define("searchSeed", true);
        REQUIRED_STRUCTURES = builder
                .comment(
                        "Structures the seed search wants on the island. One line per requirement; a line is met by any of the",
                        "structures in it, separated by |, and a name ending in * means every structure whose name starts with it",
                        "(minecraft:village_* = any village). Lines naming only structures from mods that aren't installed are",
                        "skipped. /thepale survey lists the structures your game has.")
                .defineListAllowEmpty("requiredStructures", List.of(
                        "minecraft:village_*|terralith:fortified_*",
                        "minecraft:desert_pyramid",
                        "minecraft:jungle_pyramid",
                        "minecraft:swamp_hut|terralith:witch_hut",
                        "minecraft:igloo|terralith:igloo",
                        "minecraft:mansion",
                        "minecraft:monument",
                        "minecraft:ancient_city",
                        "minecraft:trial_chambers",
                        "minecraft:pillager_outpost",
                        "minecraft:trail_ruins",
                        "born_in_chaos_v1:dark_tower_*",
                        "born_in_chaos_v1:clown_caravan_*",
                        "born_in_chaos_v1:observation_tower_*",
                        "born_in_chaos_v1:farm",
                        "born_in_chaos_v1:firewell",
                        "born_in_chaos_v1:mound_of_hounds",
                        "born_in_chaos_v1:infernal_pumpkin",
                        "born_in_chaos_v1:grave*"), () -> "", value -> value instanceof String);
        MAX_SEARCH_SECONDS = builder
                .comment("How long the seed search may take before it settles for the seed that came closest.")
                .defineInRange("maxSearchSeconds", 60, 1, 3600);
        PIN_MISSING_STRUCTURES = builder
                .comment(
                        "For a new world, whatever in requiredStructures still isn't on the island (with any seed, your own too) gets",
                        "one try of its structure set moved to a spot on the island where it fits: the right biome and workable",
                        "ground. A structure whose biome isn't on the island at all can't be placed. Saved with the world",
                        "(thepale-structure-pins.json).")
                .define("pinMissingStructures", true);
        FROZEN_ISLAND_ONLY_STRUCTURES = builder
                .comment(
                        "Structures that generate only on the frozen northern island when the world has three islands; anywhere",
                        "else in the Overworld they don't start. Their own biome rules still apply on that island. The seed search",
                        "and the pins respect this. One structure id per line; * matches any run of characters",
                        "(born_in_chaos_v1:dark_tower_* = Born in Chaos's dark towers). Only affects newly generated chunks.")
                .defineListAllowEmpty("frozenIslandOnlyStructures", List.of("born_in_chaos_v1:dark_tower_*"), () -> "", value -> value instanceof String);
        KEEP_AWAY_FROM_VILLAGES = builder
                .comment(
                        "Structures that never start within villageClearance chunks of a village (of any set in villageStructureSets).",
                        "Vanilla keeps its pillager outposts away from its own villages only, so Terralith's fortified villages",
                        "got pillager towers in the middle. One structure id per line; * matches any run of characters.",
                        "Only affects newly generated chunks.")
                .defineListAllowEmpty("keepAwayFromVillages", List.of("minecraft:pillager_outpost", "terralith:desert_outpost"), () -> "", value -> value instanceof String);
        VILLAGE_STRUCTURE_SETS = builder
                .comment(
                        "Structure sets whose structures count as villages for keepAwayFromVillages (minecraft:villages = the",
                        "vanilla villages, terralith:rare_village = Terralith's fortified and fortified desert villages).")
                .defineListAllowEmpty("villageStructureSets", List.of("minecraft:villages", "terralith:rare_village"), () -> "", value -> value instanceof String);
        VILLAGE_CLEARANCE = builder
                .comment(
                        "How many chunks keepAwayFromVillages structures stay from the spot a village tries (in a square, as",
                        "vanilla's own 10 chunks between outposts and villages). 0 turns this off.")
                .defineInRange("villageClearance", 10, 0, 64);
        builder.pop();

        builder.push("poison");
        POISON_START = builder
                .comment("Radius where the poison starts (Overworld, survival and adventure only).")
                .defineInRange("poisonStartRadius", 1500.0, 0.0, 30_000_000.0);
        POISON_FULL = builder
                .comment("Radius where the poison reaches full strength. Treated as poisonStartRadius + 1 if lower.")
                .defineInRange("poisonFullRadius", 2000.0, 0.0, 30_000_000.0);
        builder.pop();

        builder.push("ores");
        FROZEN_ISLAND_ONLY_FEATURES = builder
                .comment(
                        "Placed features (ores and the like) that generate only on the frozen northern island when the world has",
                        "three islands; on the starter and hot islands, and in the Pale nearest them, they are skipped. One feature",
                        "id per line; * matches any run of characters (oritech:ore_* = Oritech's nickel and platinum, oritech:uranium_*",
                        "= its uranium). The server log lists which features matched at start-up. Overworld only (the End keeps its",
                        "platinum). Only affects newly generated chunks; one-island worlds are untouched.")
                .defineListAllowEmpty("frozenIslandOnlyFeatures", List.of("oritech:ore_*", "oritech:uranium_*"), () -> "", value -> value instanceof String);
        builder.pop();

        builder.push("mobs");
        NOT_ON_STARTER_ISLAND = builder
                .comment(
                        "Hostile mobs that never appear on the starter island when the world has three islands: natural spawns,",
                        "spawners, mod events, all of them. Only hostile mobs (monster category) are stopped, and spawn eggs and",
                        "/summon still work. Mobs already in the world are left alone. One entity id per line; * matches any run",
                        "of characters (born_in_chaos_v1:* = every hostile mob from Born in Chaos); a line starting with ! is an exception",
                        "(the player's own summoned allies). The server log lists the matches.")
                .defineListAllowEmpty("notOnStarterIsland", List.of("born_in_chaos_v1:*", "!born_in_chaos_v1:controlled_*", "!born_in_chaos_v1:*_controlled"), () -> "", value -> value instanceof String);
        FROZEN_ISLAND_ONLY_MOBS = builder
                .comment(
                        "Mobs that appear only on the frozen northern island when the world has three islands: on the starter and",
                        "hot islands, in the Pale and in the other dimensions they don't spawn (natural spawns, spawners, mod events,",
                        "summons). Born in Chaos' Transmuting Elixir is refused on an Infernal Evil Pumpkin away from the north, so",
                        "the elixir and the pumpkin are kept. Spawn eggs and /summon still work, and mobs already in the world are",
                        "left alone. Same patterns as notOnStarterIsland (* and ! exceptions). The server log lists the matches.")
                .defineListAllowEmpty("frozenIslandOnlyMobs", List.of(
                        "born_in_chaos_v1:lord_pumpkinhead*",
                        "born_in_chaos_v1:krampus",
                        "born_in_chaos_v1:missioner",
                        "born_in_chaos_v1:nightmare_stalker",
                        "born_in_chaos_v1:dire_hound_leader",
                        "born_in_chaos_v1:lifestealer*",
                        "born_in_chaos_v1:mother_spider"), () -> "", value -> value instanceof String);
        builder.pop();

        builder.push("effects");
        RAMPAGE_SPEED_BONUS = builder
                .comment(
                        "Movement speed bonus of Born in Chaos' Rampage, in percent, for its five levels from Light to Rampant",
                        "(Born in Chaos itself gives 15, 30, 50, 75, 100). Applies to every source of Rampage: the Great Reaper Axe,",
                        "the full Dark Metal set at low health and the potion. Damage and attack speed stay as Born in Chaos has them.",
                        "An effect already running keeps its old bonus until it ends.")
                .defineList("rampageSpeedBonus", List.<Number>of(5.0, 10.0, 15.0, 20.0, 25.0), () -> 0.0,
                        value -> value instanceof Number n && n.doubleValue() >= 0.0 && n.doubleValue() <= 1000.0);
        builder.pop();

        SPEC = builder.build();
    }

    public record Settings(
            double landFalloffStart,
            double landEnd,
            double oceanContinentalness,
            double oceanVariability,
            double paleBiomeRadius,
            double coastRoughness,
            boolean threeIslands,
            double temperatureZones,
            double humidityZones,
            double climateRadius,
            int maxStructureSpacing,
            boolean searchSeed,
            List<String> requiredStructures,
            int maxSearchSeconds,
            boolean pinMissingStructures,
            List<String> frozenIslandOnlyStructures,
            List<String> keepAwayFromVillages,
            List<String> villageStructureSets,
            int villageClearance,
            double poisonStart,
            double poisonFull,
            List<String> frozenIslandOnlyFeatures,
            List<String> notOnStarterIsland,
            List<String> frozenIslandOnlyMobs,
            List<Double> rampageSpeedBonus) {}

    private static volatile Settings current = read(true);

    /** {@return the current settings; the defaults until the config file has been loaded} */
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
        if (config.getSpec() == SPEC) {
            current = read(false);
            com.example.thepale.EffectTweaks.onConfigChanged();
        }
    }

    private static Settings read(boolean defaults) {
        double falloffStart = value(LAND_FALLOFF_START, defaults);
        double poisonStart = value(POISON_START, defaults);
        return new Settings(
                falloffStart,
                Math.max(value(LAND_END, defaults), falloffStart + 1.0),
                value(OCEAN_CONTINENTALNESS, defaults),
                value(OCEAN_VARIABILITY, defaults),
                value(PALE_BIOME_RADIUS, defaults),
                value(COAST_ROUGHNESS, defaults),
                defaults ? THREE_ISLANDS.getDefault() : THREE_ISLANDS.get(),
                value(TEMPERATURE_ZONES, defaults),
                value(HUMIDITY_ZONES, defaults),
                value(CLIMATE_RADIUS, defaults),
                defaults ? MAX_STRUCTURE_SPACING.getDefault() : MAX_STRUCTURE_SPACING.get(),
                defaults ? SEARCH_SEED.getDefault() : SEARCH_SEED.get(),
                List.copyOf(defaults ? REQUIRED_STRUCTURES.getDefault() : REQUIRED_STRUCTURES.get()),
                defaults ? MAX_SEARCH_SECONDS.getDefault() : MAX_SEARCH_SECONDS.get(),
                defaults ? PIN_MISSING_STRUCTURES.getDefault() : PIN_MISSING_STRUCTURES.get(),
                List.copyOf(defaults ? FROZEN_ISLAND_ONLY_STRUCTURES.getDefault() : FROZEN_ISLAND_ONLY_STRUCTURES.get()),
                List.copyOf(defaults ? KEEP_AWAY_FROM_VILLAGES.getDefault() : KEEP_AWAY_FROM_VILLAGES.get()),
                List.copyOf(defaults ? VILLAGE_STRUCTURE_SETS.getDefault() : VILLAGE_STRUCTURE_SETS.get()),
                defaults ? VILLAGE_CLEARANCE.getDefault() : VILLAGE_CLEARANCE.get(),
                poisonStart,
                Math.max(value(POISON_FULL, defaults), poisonStart + 1.0),
                List.copyOf(defaults ? FROZEN_ISLAND_ONLY_FEATURES.getDefault() : FROZEN_ISLAND_ONLY_FEATURES.get()),
                List.copyOf(defaults ? NOT_ON_STARTER_ISLAND.getDefault() : NOT_ON_STARTER_ISLAND.get()),
                List.copyOf(defaults ? FROZEN_ISLAND_ONLY_MOBS.getDefault() : FROZEN_ISLAND_ONLY_MOBS.get()),
                (defaults ? RAMPAGE_SPEED_BONUS.getDefault() : RAMPAGE_SPEED_BONUS.get()).stream()
                        .map(Number::doubleValue).toList());
    }

    private static double value(ModConfigSpec.DoubleValue value, boolean useDefault) {
        return useDefault ? value.getDefault() : value.get();
    }
}
