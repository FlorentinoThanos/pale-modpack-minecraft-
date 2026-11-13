package com.example.thepale;

import com.example.thepale.config.CommonConfig;
import com.example.thepale.worldgen.IslandFeatures;
import com.example.thepale.worldgen.PaleCleanse;
import com.example.thepale.worldgen.PaleShape;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Two mob rules for worlds with three islands, both from the common config:
 * <ul>
 * <li>{@code notOnStarterIsland} (Born in Chaos by default) keeps hostile mobs off the starter island, the way a
 * mushroom island has no monsters.</li>
 * <li>{@code frozenIslandOnlyMobs} (Born in Chaos' bosses and strongest mobs) lets mobs appear only on the frozen
 * northern island: nowhere else in the Overworld and not in other dimensions.</li>
 * </ul>
 * Natural spawning simply doesn't get the mobs on its list where they're kept out, so other mobs spawn in their place.
 * A second check, as a new mob enters the level (before any client ever sees it), catches spawners and mods that spawn
 * by hand. Spawn eggs, /summon and dispensers are let through. Born in Chaos' Transmuting Elixir is refused on an
 * Infernal Evil Pumpkin outside the north before it is used up, since the boss it summons would be stopped anyway.
 */
public final class IslandMobs {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Born in Chaos' boss summon: this item used on one of these blocks spawns {@link #SUMMONED_BOSS}. */
    private static final ResourceLocation ELIXIR = ResourceLocation.fromNamespaceAndPath("born_in_chaos_v1", "transmuting_elixir");
    private static final Set<ResourceLocation> SUMMONING_BLOCKS = Set.of(
            ResourceLocation.fromNamespaceAndPath("born_in_chaos_v1", "infernal_evil_pumpkin"),
            ResourceLocation.fromNamespaceAndPath("born_in_chaos_v1", "infernal_evil_pumpkin_s"));
    private static final ResourceLocation SUMMONED_BOSS = ResourceLocation.fromNamespaceAndPath("born_in_chaos_v1", "lord_pumpkinhead");

    /** Players within this distance of a stopped north-only mob are told why it didn't come. */
    private static final double NOTICE_DISTANCE = 24.0;
    private static final long NOTICE_COOLDOWN_TICKS = 200;

    /** Mobs a player asked for (spawn egg, /summon, dispenser) between their finalizeSpawn and joining the level. */
    private static final Set<Entity> REQUESTED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<UUID, Long> LAST_NOTICE = new HashMap<>();

    private static final MobList STARTER = new MobList(() -> CommonConfig.get().notOnStarterIsland());
    private static final MobList FROZEN_ONLY = new MobList(() -> CommonConfig.get().frozenIslandOnlyMobs());

    private IslandMobs() {}

    /** Lets this entity into the level whatever the island rules say (the boss's summons), as a spawn egg would. */
    public static void allow(Entity entity) {
        REQUESTED.add(entity);
    }

    /** Natural spawning: the mobs are simply not on the list where they're kept out. */
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide()) {
            return;
        }
        boolean hostile = event.getMobCategory() == MobCategory.MONSTER;
        List<MobSpawnSettings.SpawnerData> drop = null;
        for (MobSpawnSettings.SpawnerData data : event.getSpawnerDataList()) {
            if (keptOut(data.type, hostile, level, event.getPos().getX(), event.getPos().getZ())) {
                if (drop == null) {
                    drop = new ArrayList<>();
                }
                drop.add(data);
            }
        }
        if (drop != null) {
            drop.forEach(event::removeSpawnerData);
        }
    }

    public static void onFinalizeSpawn(FinalizeSpawnEvent event) {
        MobSpawnType type = event.getSpawnType();
        if (type == MobSpawnType.SPAWN_EGG || type == MobSpawnType.COMMAND || type == MobSpawnType.DISPENSER) {
            REQUESTED.add(event.getEntity());
        }
    }

    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        Level level = event.getLevel();
        if (event.loadedFromDisk() || level.isClientSide() || REQUESTED.remove(entity)) {
            return;
        }
        boolean hostile = entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER;
        if (keptOut(entity.getType(), hostile, level, entity.getX(), entity.getZ())) {
            event.setCanceled(true);
            if (northOnly(entity.getType(), level) && level instanceof ServerLevel server) {
                tellPlayersNear(server, entity.getX(), entity.getY(), entity.getZ());
            }
        }
    }

    /** Stops Born in Chaos' Transmuting Elixir from being used up on a boss that wouldn't be let in. */
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (!ELIXIR.equals(BuiltInRegistries.ITEM.getKey(event.getItemStack().getItem()))
                || !SUMMONING_BLOCKS.contains(BuiltInRegistries.BLOCK.getKey(level.getBlockState(event.getPos()).getBlock()))) {
            return;
        }
        EntityType<?> boss = BuiltInRegistries.ENTITY_TYPE.getOptional(SUMMONED_BOSS).orElse(null);
        BlockPos pos = event.getPos();
        if (boss == null || !northOnly(boss, level) || onFrozenIsland(level, pos.getX() + 0.5, pos.getZ() + 0.5)) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.FAIL);
        if (event.getEntity() instanceof ServerPlayer player) {
            notice(player, level.getGameTime(), true);
        }
    }

    /** {@return whether a mob of this type is kept out of this spot} */
    private static boolean keptOut(EntityType<?> type, boolean hostile, Level level, double x, double z) {
        if (northOnly(type, level) && !onFrozenIsland(level, x, z)) {
            return true;
        }
        if (!hostile || level.dimension() != Level.OVERWORLD || !threeIslands(level) || !STARTER.matches(type)) {
            return false;
        }
        return shape(level).nearest(x, z).kind() == PaleShape.Kind.MILD && !newLands(level, x, z);
    }

    /** {@return whether the frozenIslandOnlyMobs rule is on for this type in this world} */
    private static boolean northOnly(EntityType<?> type, Level level) {
        return threeIslands(level) && FROZEN_ONLY.matches(type);
    }

    /** The frozen island itself, not the Pale around it (the Pale has no spawns anyway). */
    private static boolean onFrozenIsland(Level level, double x, double z) {
        if (level.dimension() != Level.OVERWORLD) {
            return false;
        }
        if (newLands(level, x, z)) {
            return true;
        }
        PaleShape shape = shape(level);
        return shape.nearest(x, z).kind() == PaleShape.Kind.COLD
                && shape.effectiveRadius(x, z) < CommonConfig.get().paleBiomeRadius();
    }

    /**
     * The new lands that generate past the old sea once the Pale is cleansed (the finale) belong to no island: none of
     * the islands' rules hold there. Server side only; a client never decides spawns.
     */
    private static boolean newLands(Level level, double x, double z) {
        return !level.isClientSide() && PaleCleanse.inNewLands(x, z);
    }

    /**
     * The world layout. On a server it is only known for the Overworld's world; other dimensions of the same server
     * share it, which is what makes "three islands" a property of the whole world here.
     */
    private static PaleShape shape(Level level) {
        return level.isClientSide() ? PaleShape.client() : PaleShape.server();
    }

    private static boolean threeIslands(Level level) {
        PaleShape shape = shape(level);
        return shape != null && shape.islands().size() > 1;
    }

    private static void tellPlayersNear(ServerLevel level, double x, double y, double z) {
        long now = level.getGameTime();
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(x, y, z) <= NOTICE_DISTANCE * NOTICE_DISTANCE) {
                notice(player, now, false);
            }
        }
    }

    private static void notice(ServerPlayer player, long now, boolean always) {
        synchronized (LAST_NOTICE) {
            Long last = LAST_NOTICE.get(player.getUUID());
            if (!always && last != null && now - last < NOTICE_COOLDOWN_TICKS && now >= last) {
                return;
            }
            LAST_NOTICE.put(player.getUUID(), now);
        }
        player.displayClientMessage(Component.translatable("message.thepale.north_only").withStyle(ChatFormatting.GRAY), true);
    }

    /** Logs which mob types each rule caught. */
    public static void onServerStarted(ServerStartedEvent event) {
        boolean three = PaleShape.server() != null && PaleShape.server().islands().size() >= 2;
        report("notOnStarterIsland", STARTER, three, true, "Kept off the starter island");
        report("frozenIslandOnlyMobs", FROZEN_ONLY, three, false, "Only on the frozen island");
    }

    private static void report(String name, MobList list, boolean three, boolean hostileOnly, String what) {
        if (list.lines().isEmpty()) {
            return;
        }
        if (!three) {
            LOGGER.info("[thepale] One-island world: {} has nothing to do", name);
            return;
        }
        List<String> found = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (list.matches(type) && (!hostileOnly || type.getCategory() == MobCategory.MONSTER)) {
                found.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            }
        }
        found.sort(null);
        if (found.isEmpty()) {
            LOGGER.warn("[thepale] {} {} matches no mob in this game", name, list.lines());
        } else {
            LOGGER.info("[thepale] {} ({}): {}", what, found.size(), String.join(", ", found));
        }
    }

    /** A config list of entity id patterns ({@code *} wildcards, {@code !} exceptions), compiled on change. */
    private static final class MobList {
        private final Supplier<List<String>> source;
        private final Map<EntityType<?>, Boolean> cache = new IdentityHashMap<>();
        private List<String> cachedFor = List.of();
        private List<Pattern> patterns = List.of();
        private List<Pattern> exceptions = List.of();

        MobList(Supplier<List<String>> source) {
            this.source = source;
        }

        List<String> lines() {
            return source.get();
        }

        synchronized boolean matches(EntityType<?> type) {
            List<String> wanted = source.get();
            if (!wanted.equals(cachedFor)) {
                List<Pattern> compiled = new ArrayList<>();
                List<Pattern> except = new ArrayList<>();
                for (String line : wanted) {
                    String text = line.trim();
                    if (text.startsWith("!")) {
                        except.add(IslandFeatures.glob(text.substring(1).trim()));
                    } else if (!text.isEmpty()) {
                        compiled.add(IslandFeatures.glob(text));
                    }
                }
                patterns = compiled;
                exceptions = except;
                cachedFor = wanted;
                cache.clear();
            }
            return cache.computeIfAbsent(type, t -> matches(BuiltInRegistries.ENTITY_TYPE.getKey(t)));
        }

        private boolean matches(ResourceLocation id) {
            String name = id.toString();
            for (Pattern pattern : exceptions) {
                if (pattern.matcher(name).matches()) {
                    return false;
                }
            }
            for (Pattern pattern : patterns) {
                if (pattern.matcher(name).matches()) {
                    return true;
                }
            }
            return false;
        }
    }
}
