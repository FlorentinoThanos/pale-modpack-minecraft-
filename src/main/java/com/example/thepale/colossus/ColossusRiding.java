package com.example.thepale.colossus;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * What riding the Frost Colossus takes besides the colossus itself: clicks on it reach it and nobody else (a mod
 * that picks mobs up by a click doesn't get the colossus or its riders), a rider isn't hurt by walls or by falling,
 * and a rider who dies or leaves the game is put on the ground first.
 */
public final class ColossusRiding {
    /** Whoever came off the colossus other than by its hand, and at which tick of theirs: their next fall doesn't hurt. */
    private static final Map<UUID, Integer> SOFT_LANDINGS = new HashMap<>();
    /** Long enough to drift down from a shoulder and across a valley. */
    private static final int SOFT_LANDING_TICKS = 20 * 30;

    private ColossusRiding() {}

    /** A click on the colossus is the colossus's own business; so is a click on somebody on its shoulder. */
    public static void onInteract(PlayerInteractEvent.EntityInteract event) {
        Entity target = event.getTarget();
        if (target instanceof ColossusPart part) {
            event.setCancellationResult(part.getParent().interactPart(part, event.getEntity(), event.getHand()));
            event.setCanceled(true);
        } else if (target instanceof FrostColossus || target.getVehicle() instanceof FrostColossus) {
            event.setCancellationResult(InteractionResult.PASS);
            event.setCanceled(true);
        }
    }

    public static void onInteractAt(PlayerInteractEvent.EntityInteractSpecific event) {
        Entity target = event.getTarget();
        if (target instanceof ColossusPart || target instanceof FrostColossus || target.getVehicle() instanceof FrostColossus) {
            // Falls through to the plain click, above.
            event.setCancellationResult(InteractionResult.PASS);
            event.setCanceled(true);
        }
    }

    /** On its shoulder or standing on it, a wall the colossus walks them through doesn't smother them and no fall hurts them. */
    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity entity = event.getEntity();
        boolean wall = event.getSource().is(DamageTypes.IN_WALL);
        boolean fall = event.getSource().is(DamageTypeTags.IS_FALL);
        if (!wall && !fall) {
            return;
        }
        if (entity.getVehicle() instanceof FrostColossus) {
            event.setCanceled(true);
        } else if (wall && entity instanceof Player player && !player.level().getEntitiesOfClass(FrostColossus.class,
                player.getBoundingBox().inflate(16, 40, 16), colossus -> colossus.standsOn(player)).isEmpty()) {
            // Standing on it as it walks through the trees.
            event.setCanceled(true);
        } else if (fall && entity instanceof ServerPlayer player) {
            Integer since = SOFT_LANDINGS.remove(player.getUUID());
            if (since != null && player.tickCount >= since && player.tickCount <= since + SOFT_LANDING_TICKS) {
                event.setCanceled(true);
            }
        }
    }

    /** Off the colossus in mid-air: the fall that follows doesn't hurt. */
    static void protectFromFall(ServerPlayer player) {
        SOFT_LANDINGS.put(player.getUUID(), player.tickCount);
    }

    /** Once they stand on something again, the next fall is their own. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (SOFT_LANDINGS.isEmpty() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        Integer since = SOFT_LANDINGS.get(player.getUUID());
        if (since == null) {
            return;
        }
        int ticks = player.tickCount - since;
        if (ticks < 0 || ticks > SOFT_LANDING_TICKS || (ticks > 10 && player.onGround())) {
            SOFT_LANDINGS.remove(player.getUUID());
        }
    }

    /** Dying on its shoulder, they are put on the ground first: what they leave behind lies there, not in the air. */
    public static void onDeath(LivingDeathEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getVehicle() instanceof FrostColossus colossus) {
            colossus.putDown(player);
        }
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            SOFT_LANDINGS.remove(player.getUUID());
            if (player.getVehicle() instanceof FrostColossus colossus) {
                colossus.putDown(player);
            }
        }
    }
}
