package com.example.thepale;

import com.example.thepale.config.CommonConfig;
import com.example.thepale.worldgen.IslandFeatures;
import com.example.thepale.worldgen.PaleShape;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.living.FinalizeSpawnEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import org.slf4j.Logger;

/**
 * Keeps the mobs in the config's notOnStarterIsland (Born in Chaos by default) off the starter island when the world
 * has three islands, the way a mushroom island has no monsters: they are taken out of the spawn list there, so natural
 * spawning never picks them and other mobs spawn in their place. A second check, as a new mob enters the level (before
 * any client ever sees it), catches spawners and mods that spawn by hand. Spawn eggs and /summon are let through.
 */
public final class IslandMobs {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Mobs a player asked for (spawn egg, /summon, dispenser) between their finalizeSpawn and joining the level. */
    private static final Set<Entity> REQUESTED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Map<EntityType<?>, Boolean> BANNED = new IdentityHashMap<>();
    private static List<String> cachedFor = List.of();
    private static List<Pattern> patterns = List.of();
    private static List<Pattern> exceptions = List.of();

    private IslandMobs() {}

    /** Natural spawning: the mobs are simply not on the list here, like monsters on a mushroom island. */
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event) {
        if (event.getMobCategory() != MobCategory.MONSTER || !(event.getLevel() instanceof Level level)
                || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }
        List<MobSpawnSettings.SpawnerData> list = event.getSpawnerDataList();
        List<MobSpawnSettings.SpawnerData> drop = null;
        for (MobSpawnSettings.SpawnerData data : list) {
            if (banned(data.type)) {
                if (drop == null) {
                    drop = new ArrayList<>();
                }
                drop.add(data);
            }
        }
        if (drop == null) {
            return;
        }
        PaleShape shape = PaleShape.server();
        if (shape != null && shape.islands().size() > 1
                && shape.nearest(event.getPos().getX(), event.getPos().getZ()).kind() == PaleShape.Kind.MILD) {
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
        if (event.loadedFromDisk() || level.isClientSide() || level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (REQUESTED.remove(entity) || !(entity instanceof Enemy || entity.getType().getCategory() == MobCategory.MONSTER)) {
            return;
        }
        if (!banned(entity.getType())) {
            return;
        }
        PaleShape shape = PaleShape.server();
        if (shape != null && shape.islands().size() > 1 && shape.nearest(entity.getX(), entity.getZ()).kind() == PaleShape.Kind.MILD) {
            event.setCanceled(true);
        }
    }

    private static synchronized boolean banned(EntityType<?> type) {
        List<String> wanted = CommonConfig.get().notOnStarterIsland();
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
            BANNED.clear();
        }
        return BANNED.computeIfAbsent(type, t -> matches(BuiltInRegistries.ENTITY_TYPE.getKey(t)));
    }

    private static boolean matches(ResourceLocation id) {
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

    /** Logs which mob types the config caught (hostile ones are what gets stopped). */
    public static void onServerStarted(ServerStartedEvent event) {
        if (CommonConfig.get().notOnStarterIsland().isEmpty()) {
            return;
        }
        if (PaleShape.server() == null || PaleShape.server().islands().size() < 2) {
            LOGGER.info("[thepale] One-island world: notOnStarterIsland has nothing to do");
            return;
        }
        List<String> monsters = new ArrayList<>();
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            if (banned(type) && type.getCategory() == MobCategory.MONSTER) {
                monsters.add(BuiltInRegistries.ENTITY_TYPE.getKey(type).toString());
            }
        }
        monsters.sort(null);
        if (monsters.isEmpty()) {
            LOGGER.warn("[thepale] notOnStarterIsland {} matches no hostile mob in this game", cachedFor);
        } else {
            LOGGER.info("[thepale] Kept off the starter island ({}): {}", monsters.size(), String.join(", ", monsters));
        }
    }
}
