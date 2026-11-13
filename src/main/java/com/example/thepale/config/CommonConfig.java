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
    private static final ModConfigSpec.ConfigValue<List<? extends String>> HOT_ISLAND_ONLY_FEATURES;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> NOT_ON_STARTER_ISLAND;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> FROZEN_ISLAND_ONLY_MOBS;
    private static final ModConfigSpec.ConfigValue<List<? extends Number>> RAMPAGE_SPEED_BONUS;
    private static final ModConfigSpec.IntValue DARK_METAL_SET_STRENGTH;
    private static final ModConfigSpec.BooleanValue ANCHOR_KEEPS_INVENTORY;

    // [finale]
    private static final ModConfigSpec.DoubleValue APPROACH_DAYS;
    private static final ModConfigSpec.DoubleValue APPROACH_START_DISTANCE;
    private static final ModConfigSpec.DoubleValue SILHOUETTE_FROM_DAY;
    private static final ModConfigSpec.DoubleValue SPAWN_RANGE;
    private static final ModConfigSpec.DoubleValue ARENA_RADIUS;
    private static final ModConfigSpec.DoubleValue BOSS_LEASH;
    private static final ModConfigSpec.DoubleValue RETREAT_MINUTES;
    private static final ModConfigSpec.BooleanValue ANNOUNCE_COORDINATES;
    private static final ModConfigSpec.IntValue BLOCK_BREAK_RADIUS;
    private static final ModConfigSpec.DoubleValue BODY_HEALTH;
    private static final ModConfigSpec.DoubleValue TENTACLE_HEALTH;
    private static final ModConfigSpec.DoubleValue FACE_HEALTH;
    private static final ModConfigSpec.DoubleValue HAND_HEALTH;
    private static final ModConfigSpec.DoubleValue EXTRA_PLAYER_HEALTH;
    private static final ModConfigSpec.DoubleValue ARMORED_DAMAGE_FACTOR;
    private static final ModConfigSpec.DoubleValue CLOSED_EYE_DAMAGE_FACTOR;
    private static final ModConfigSpec.DoubleValue EYE_DAMAGE_FACTOR;
    private static final ModConfigSpec.DoubleValue SEVER_DAMAGE_FRACTION;
    private static final ModConfigSpec.IntValue PHASE2_TENTACLES_LEFT;
    private static final ModConfigSpec.DoubleValue FALL_HEALTH_FRACTION;
    private static final ModConfigSpec.DoubleValue SLAM_DAMAGE;
    private static final ModConfigSpec.IntValue SLAM_WINDUP_TICKS;
    private static final ModConfigSpec.DoubleValue SLAM_RADIUS;
    private static final ModConfigSpec.IntValue SLAM_INTERVAL_TICKS;
    private static final ModConfigSpec.DoubleValue SCREAM_INTERVAL_SECONDS;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> SUMMON_MOBS;
    private static final ModConfigSpec.IntValue SUMMON_COUNT;
    private static final ModConfigSpec.IntValue MAX_SUMMONS;
    private static final ModConfigSpec.DoubleValue POISON_CLOUD_SECONDS;
    private static final ModConfigSpec.DoubleValue GRAB_DAMAGE;
    private static final ModConfigSpec.DoubleValue EYE_OPEN_SECONDS;
    private static final ModConfigSpec.DoubleValue EYE_CLOSED_SECONDS;
    private static final ModConfigSpec.DoubleValue CRAWL_DAMAGE;
    private static final ModConfigSpec.IntValue BOSS_EXPERIENCE;
    private static final ModConfigSpec.DoubleValue FADE_SECONDS;
    private static final ModConfigSpec.DoubleValue PRESERVE_MARGIN;
    private static final ModConfigSpec.DoubleValue TRANSITION_WIDTH;
    private static final ModConfigSpec.IntValue CHUNKS_PER_TICK;
    private static final ModConfigSpec.BooleanValue OPEN_COLD_ISLAND_PORTALS;

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
                .defineListAllowEmpty("frozenIslandOnlyFeatures", List.of("oritech:ore_*", "oritech:uranium_*", "create_new_age:thorium_ore"), () -> "", value -> value instanceof String);
        HOT_ISLAND_ONLY_FEATURES = builder
                .comment(
                        "The same for the hot southern island: these placed features generate there and nowhere else in the",
                        "Overworld (oritech:oil_spring* = Oritech's oil springs). Same patterns as frozenIslandOnlyFeatures.",
                        "Create Diesel Generators' oil isn't a feature: The Pale limits it to desert, badlands and savanna biomes",
                        "through the createdieselgenerators:oil_biomes tag instead.")
                .defineListAllowEmpty("hotIslandOnlyFeatures", List.of("oritech:oil_spring*"), () -> "", value -> value instanceof String);
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
        DARK_METAL_SET_STRENGTH = builder
                .comment(
                        "Level of Strength a player gets while wearing all four pieces of Born in Chaos' Dark Metal armour",
                        "(1 = Strength I). 0 turns this off. Strength also keeps the Dark Warblade from making its wielder tired.")
                .defineInRange("darkMetalSetStrength", 1, 0, 5);
        builder.pop();

        builder.push("respawn");
        ANCHOR_KEEPS_INVENTORY = builder
                .comment(
                        "A player whose spawn point is a charged respawn anchor in the Nether keeps their inventory and",
                        "experience on death. Curios slots (Elytra Slot) drop as usual.")
                .define("anchorKeepsInventory", true);
        builder.pop();

        builder.comment(
                "The finale: the invasion (/thepale invasion start, called by the quest), the boss and the cleansing of the Pale.",
                "The state of the finale itself is saved with the world (data/thepale_finale.dat), not here.").push("finale");
        APPROACH_DAYS = builder
                .comment(
                        "How many game days the boss takes to reach the shore once the invasion has started. Counted in game",
                        "time, so sleeping or /time add brings it closer. It comes ashore on the first night after that.")
                .defineInRange("approachDays", 3.0, 0.01, 100.0);
        APPROACH_START_DISTANCE = builder
                .comment("How far out in the Pale the boss starts, in blocks from the shore it is heading for.")
                .defineInRange("approachStartDistance", 1800.0, 200.0, 10000.0);
        SILHOUETTE_FROM_DAY = builder
                .comment("After this many days of the approach its silhouette shows on the horizon (before that only the sky darkens).")
                .defineInRange("silhouetteFromDay", 1.0, 0.0, 100.0);
        SPAWN_RANGE = builder
                .comment(
                        "Once it has arrived it waits off the shore; it comes ashore at night when a player is within this",
                        "many blocks of the landing point.")
                .defineInRange("spawnRange", 96.0, 16.0, 512.0);
        ARENA_RADIUS = builder
                .comment("Players within this many blocks of the landing point are in the fight: they see the boss bar and are counted for its health.")
                .defineInRange("arenaRadius", 64.0, 16.0, 256.0);
        BOSS_LEASH = builder
                .comment("How far the boss moves from the landing point. It never goes inland past this, so a base away from the shore is safe.")
                .defineInRange("bossLeash", 14.0, 0.0, 64.0);
        RETREAT_MINUTES = builder
                .comment(
                        "With no living player in the arena for this many minutes the boss goes back into the sea and returns the",
                        "next night at full health. The finale can't be lost.")
                .defineInRange("retreatMinutes", 3.0, 0.1, 60.0);
        ANNOUNCE_COORDINATES = builder
                .comment("Tell everybody the coordinates of the landing point when the boss arrives.")
                .define("announceCoordinates", true);
        BLOCK_BREAK_RADIUS = builder
                .comment("Blocks around a tentacle's strike that are broken (0 = the boss breaks nothing; also needs the mobGriefing game rule).")
                .defineInRange("blockBreakRadius", 0, 0, 4);

        builder.comment("The Patchwork Abomination. Numbers are for a player in full netherite or Born in Chaos gear with Better Combat.").push("boss");
        BODY_HEALTH = builder
                .comment("Health of the body: the boss bar. The boss dies when it runs out.")
                .defineInRange("bodyHealth", 600.0, 1.0, 100000.0);
        TENTACLE_HEALTH = builder
                .comment("Health of each of the six tentacles. At 0 the tentacle is severed.")
                .defineInRange("tentacleHealth", 50.0, 1.0, 100000.0);
        FACE_HEALTH = builder
                .comment("Health of the stone face on its tentacle.")
                .defineInRange("faceHealth", 100.0, 1.0, 100000.0);
        HAND_HEALTH = builder
                .comment("Health of the clawed hand on its tentacle.")
                .defineInRange("handHealth", 100.0, 1.0, 100000.0);
        EXTRA_PLAYER_HEALTH = builder
                .comment("Every player in the arena after the first adds this share to all of the boss's health (0.5 = +50% each).")
                .defineInRange("extraPlayerHealth", 0.5, 0.0, 10.0);
        ARMORED_DAMAGE_FACTOR = builder
                .comment("Phases 1 and 2: share of a hit on the body that gets through while it still has tentacles.")
                .defineInRange("armoredDamageFactor", 0.2, 0.0, 1.0);
        CLOSED_EYE_DAMAGE_FACTOR = builder
                .comment("Phase 3: share of a hit on the body that gets through while the main eye is shut.")
                .defineInRange("closedEyeDamageFactor", 0.1, 0.0, 1.0);
        EYE_DAMAGE_FACTOR = builder
                .comment("Phase 3: a hit on the open main eye is multiplied by this.")
                .defineInRange("eyeDamageFactor", 1.5, 0.0, 10.0);
        SEVER_DAMAGE_FRACTION = builder
                .comment("Severing a tentacle, the face or the hand takes this share of the body's full health off the boss bar.")
                .defineInRange("severDamageFraction", 0.05, 0.0, 0.12);
        PHASE2_TENTACLES_LEFT = builder
                .comment("Phase 2 (the face and the hand come down and fight) starts when this many tentacles are left.")
                .defineInRange("phase2TentaclesLeft", 3, 0, 5);
        FALL_HEALTH_FRACTION = builder
                .comment("Phase 4 (the body falls and crawls, and takes full damage) starts at this share of its health.")
                .defineInRange("fallHealthFraction", 0.25, 0.01, 0.6);
        SLAM_DAMAGE = builder
                .comment("Damage of a tentacle's strike, before armour.")
                .defineInRange("slamDamage", 26.0, 0.0, 1000.0);
        SLAM_WINDUP_TICKS = builder
                .comment("Ticks the warning ring of particles shows on the ground before a tentacle strikes (20 = one second).")
                .defineInRange("slamWindupTicks", 24, 5, 200);
        SLAM_RADIUS = builder
                .comment("Radius of a tentacle's strike, in blocks.")
                .defineInRange("slamRadius", 3.0, 0.5, 16.0);
        SLAM_INTERVAL_TICKS = builder
                .comment("Ticks between strikes with all six tentacles alive. Fewer tentacles strike a little more often.")
                .defineInRange("slamIntervalTicks", 50, 10, 1200);
        SCREAM_INTERVAL_SECONDS = builder
                .comment("Seconds between the face's spells (scream, summon, poison cloud, in turn).")
                .defineInRange("screamIntervalSeconds", 9.0, 2.0, 600.0);
        SUMMON_MOBS = builder
                .comment(
                        "Mobs the face summons, one entity id per line, picked at random. Ids of mods that aren't installed are",
                        "skipped; if none is left, zombies come.")
                .defineListAllowEmpty("summonMobs", List.of(
                        "born_in_chaos_v1:decaying_zombie",
                        "born_in_chaos_v1:decrepit_skeleton",
                        "born_in_chaos_v1:restless_spirit",
                        "born_in_chaos_v1:dread_hound"), () -> "", value -> value instanceof String);
        SUMMON_COUNT = builder
                .comment("How many mobs one summon brings.")
                .defineInRange("summonCount", 3, 0, 32);
        MAX_SUMMONS = builder
                .comment("No more summons while this many summoned mobs are still alive.")
                .defineInRange("maxSummons", 9, 0, 128);
        POISON_CLOUD_SECONDS = builder
                .comment("How long a poison cloud stays (it gives the Pale's Wither and Hunger to players inside).")
                .defineInRange("poisonCloudSeconds", 8.0, 1.0, 120.0);
        GRAB_DAMAGE = builder
                .comment("Damage a second while the hand holds a player. Hitting the hand makes it let go.")
                .defineInRange("grabDamage", 5.0, 0.0, 1000.0);
        EYE_OPEN_SECONDS = builder
                .comment("Phase 3: how long the main eye stays open.")
                .defineInRange("eyeOpenSeconds", 8.0, 1.0, 120.0);
        EYE_CLOSED_SECONDS = builder
                .comment("Phase 3: how long the main eye stays shut in between.")
                .defineInRange("eyeClosedSeconds", 6.0, 1.0, 120.0);
        CRAWL_DAMAGE = builder
                .comment("Phase 4: damage of touching the crawling body, and of its shockwaves.")
                .defineInRange("crawlDamage", 16.0, 0.0, 1000.0);
        BOSS_EXPERIENCE = builder
                .comment("Experience the boss drops.")
                .defineInRange("experience", 2500, 0, 1000000);
        builder.pop();

        builder.comment("The cleansing of the Pale, started by using the Heart of the Pale the boss drops.").push("cleansing");
        FADE_SECONDS = builder
                .comment("How long the fog wall, the fog, the grey water and the sea of clouds take to fade for players.")
                .defineInRange("fadeSeconds", 60.0, 1.0, 600.0);
        PRESERVE_MARGIN = builder
                .comment(
                        "New chunks keep generating as open sea up to this many blocks past the farthest chunk that existed when",
                        "the Pale was cleansed, so old and new chunks never meet at a seam.")
                .defineInRange("preserveMargin", 200.0, 64.0, 5000.0);
        TRANSITION_WIDTH = builder
                .comment("Past that margin the sea turns into the seed's normal terrain over this many blocks: new lands across the sea.")
                .defineInRange("transitionWidth", 400.0, 64.0, 5000.0);
        CHUNKS_PER_TICK = builder
                .comment("How many loaded chunks a tick have their Pale biome turned into ocean after the cleansing.")
                .defineInRange("chunksPerTick", 4, 1, 64);
        OPEN_COLD_ISLAND_PORTALS = builder
                .comment("After the cleansing, Nether portals may open on the frozen island like anywhere else.")
                .define("openColdIslandPortals", true);
        builder.pop();
        builder.pop();

        SPEC = builder.build();
    }

    /** The [finale] section; see the comments in the config file. */
    public record Finale(
            double approachDays,
            double approachStartDistance,
            double silhouetteFromDay,
            double spawnRange,
            double arenaRadius,
            double bossLeash,
            double retreatMinutes,
            boolean announceCoordinates,
            int blockBreakRadius,
            double bodyHealth,
            double tentacleHealth,
            double faceHealth,
            double handHealth,
            double extraPlayerHealth,
            double armoredDamageFactor,
            double closedEyeDamageFactor,
            double eyeDamageFactor,
            double severDamageFraction,
            int phase2TentaclesLeft,
            double fallHealthFraction,
            double slamDamage,
            int slamWindupTicks,
            double slamRadius,
            int slamIntervalTicks,
            double screamIntervalSeconds,
            List<String> summonMobs,
            int summonCount,
            int maxSummons,
            double poisonCloudSeconds,
            double grabDamage,
            double eyeOpenSeconds,
            double eyeClosedSeconds,
            double crawlDamage,
            int experience,
            double fadeSeconds,
            double preserveMargin,
            double transitionWidth,
            int chunksPerTick,
            boolean openColdIslandPortals) {
        /** Ticks of game time the approach takes. */
        public long approachTicks() {
            return Math.max(1L, Math.round(approachDays * 24000.0));
        }
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
            List<String> hotIslandOnlyFeatures,
            List<String> notOnStarterIsland,
            List<String> frozenIslandOnlyMobs,
            List<Double> rampageSpeedBonus,
            int darkMetalSetStrength,
            boolean anchorKeepsInventory,
            Finale finale) {}

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
                List.copyOf(defaults ? HOT_ISLAND_ONLY_FEATURES.getDefault() : HOT_ISLAND_ONLY_FEATURES.get()),
                List.copyOf(defaults ? NOT_ON_STARTER_ISLAND.getDefault() : NOT_ON_STARTER_ISLAND.get()),
                List.copyOf(defaults ? FROZEN_ISLAND_ONLY_MOBS.getDefault() : FROZEN_ISLAND_ONLY_MOBS.get()),
                (defaults ? RAMPAGE_SPEED_BONUS.getDefault() : RAMPAGE_SPEED_BONUS.get()).stream()
                        .map(Number::doubleValue).toList(),
                defaults ? DARK_METAL_SET_STRENGTH.getDefault() : DARK_METAL_SET_STRENGTH.get(),
                defaults ? ANCHOR_KEEPS_INVENTORY.getDefault() : ANCHOR_KEEPS_INVENTORY.get(),
                readFinale(defaults));
    }

    private static Finale readFinale(boolean d) {
        return new Finale(
                value(APPROACH_DAYS, d),
                value(APPROACH_START_DISTANCE, d),
                value(SILHOUETTE_FROM_DAY, d),
                value(SPAWN_RANGE, d),
                value(ARENA_RADIUS, d),
                value(BOSS_LEASH, d),
                value(RETREAT_MINUTES, d),
                value(ANNOUNCE_COORDINATES, d),
                value(BLOCK_BREAK_RADIUS, d),
                value(BODY_HEALTH, d),
                value(TENTACLE_HEALTH, d),
                value(FACE_HEALTH, d),
                value(HAND_HEALTH, d),
                value(EXTRA_PLAYER_HEALTH, d),
                value(ARMORED_DAMAGE_FACTOR, d),
                value(CLOSED_EYE_DAMAGE_FACTOR, d),
                value(EYE_DAMAGE_FACTOR, d),
                value(SEVER_DAMAGE_FRACTION, d),
                value(PHASE2_TENTACLES_LEFT, d),
                value(FALL_HEALTH_FRACTION, d),
                value(SLAM_DAMAGE, d),
                value(SLAM_WINDUP_TICKS, d),
                value(SLAM_RADIUS, d),
                value(SLAM_INTERVAL_TICKS, d),
                value(SCREAM_INTERVAL_SECONDS, d),
                List.copyOf(d ? SUMMON_MOBS.getDefault() : SUMMON_MOBS.get()),
                value(SUMMON_COUNT, d),
                value(MAX_SUMMONS, d),
                value(POISON_CLOUD_SECONDS, d),
                value(GRAB_DAMAGE, d),
                value(EYE_OPEN_SECONDS, d),
                value(EYE_CLOSED_SECONDS, d),
                value(CRAWL_DAMAGE, d),
                value(BOSS_EXPERIENCE, d),
                value(FADE_SECONDS, d),
                value(PRESERVE_MARGIN, d),
                value(TRANSITION_WIDTH, d),
                value(CHUNKS_PER_TICK, d),
                value(OPEN_COLD_ISLAND_PORTALS, d));
    }

    private static int value(ModConfigSpec.IntValue value, boolean useDefault) {
        return useDefault ? value.getDefault() : value.get();
    }

    private static boolean value(ModConfigSpec.BooleanValue value, boolean useDefault) {
        return useDefault ? value.getDefault() : value.get();
    }

    private static double value(ModConfigSpec.DoubleValue value, boolean useDefault) {
        return useDefault ? value.getDefault() : value.get();
    }
}
