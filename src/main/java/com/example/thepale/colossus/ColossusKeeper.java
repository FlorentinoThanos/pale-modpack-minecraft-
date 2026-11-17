package com.example.thepale.colossus;

import com.example.thepale.config.CommonConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * The Frost Colossus's walks, and keeping it one of a kind.
 *
 * <p>Every {@code eventMinMinutes} to {@code eventMaxMinutes} of play it appears some 100 blocks from a random player
 * in the Overworld, walks straight past them with its caches, and is taken away once nobody is near it. Everybody in
 * the Overworld is told in chat when it comes and when it goes. The Overworld remembers which colossus is the world's
 * and where it was last; another one that turns up is sent away (the newest one put down by hand wins).
 *
 * <p>Nothing here loads a chunk: it looks every five seconds at what is loaded already.
 */
public final class ColossusKeeper extends SavedData {
    private static final String NAME = "thepale_colossus";
    private static final int CHECK_TICKS = 100;
    /** It appears this far from the player it walks past, at most and at least (as near as the loaded world allows). */
    private static final int FAR = 112;
    private static final int NEAR = 48;
    /** Its way passes the player this far to one side, so that it walks past them and not over them. */
    private static final double PASS_BY = 14;
    private static final double PASS_BY_MORE = 14;
    /** It doesn't appear right in front of somebody: nobody may be this near the place. */
    private static final double UNSEEN = 40;
    /** Once nobody is this near (and nobody rides it), it has gone on its way. */
    private static final double GONE = 128;
    /** How long a walk lasts at least before it may go, in ticks. */
    private static final int LEAST_WALK = 20 * 20;
    /** When no place for it turns up, the next try is this much later, in ticks. */
    private static final int RETRY = 20 * 60;

    @Nullable
    private UUID id;
    /** Whether the world's colossus is out on a walk (else it was put down by a command). */
    private boolean walking;
    @Nullable
    private Vec3 last;
    private boolean asleep;
    /** The game time of the next walk, or -1 until one is set. */
    private long nextWalk = -1;
    /** How many checks in a row the walking colossus was nowhere in the loaded world. */
    private int missing;

    private ColossusKeeper() {}

    public static ColossusKeeper get(ServerLevel overworld) {
        return overworld.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ColossusKeeper::new, ColossusKeeper::load, null), NAME);
    }

    private static ColossusKeeper load(CompoundTag tag, HolderLookup.Provider registries) {
        ColossusKeeper keeper = new ColossusKeeper();
        if (tag.hasUUID("Id")) {
            keeper.id = tag.getUUID("Id");
        }
        keeper.walking = tag.getBoolean("Walking");
        if (tag.contains("LastX")) {
            keeper.last = new Vec3(tag.getDouble("LastX"), tag.getDouble("LastY"), tag.getDouble("LastZ"));
        }
        keeper.asleep = tag.getBoolean("Asleep");
        keeper.nextWalk = tag.contains("NextWalk") ? tag.getLong("NextWalk") : -1;
        return keeper;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (id != null) {
            tag.putUUID("Id", id);
        }
        tag.putBoolean("Walking", walking);
        if (last != null) {
            tag.putDouble("LastX", last.x);
            tag.putDouble("LastY", last.y);
            tag.putDouble("LastZ", last.z);
        }
        tag.putBoolean("Asleep", asleep);
        tag.putLong("NextWalk", nextWalk);
        return tag;
    }

    /** {@return where it was when last seen, or null if it is not in the world} */
    @Nullable
    public Vec3 last() {
        return id == null ? null : last;
    }

    public boolean asleep() {
        return asleep;
    }

    /** {@return how many ticks until its next walk, if it is not out on one and walks are on; else -1} */
    public long nextWalkIn(ServerLevel overworld) {
        if (walking || nextWalk < 0 || !CommonConfig.get().colossus().events()) {
            return -1;
        }
        return Math.max(0, nextWalk - overworld.getGameTime());
    }

    /** {@return what it knows, for /thepale colossus status} */
    public String status(ServerLevel overworld) {
        return "World's colossus: " + (id == null ? "none" : id + (walking ? " (on a walk)" : " (summoned)"))
                + (last == null || id == null ? "" : String.format(", last at %.0f %.0f %.0f%s", last.x, last.y, last.z, asleep ? " asleep" : ""))
                + (nextWalkIn(overworld) >= 0 ? ", next walk in " + nextWalkIn(overworld) / 20 + " s" : "");
    }

    private static long walkInterval(ServerLevel level) {
        CommonConfig.Colossus config = CommonConfig.get().colossus();
        double least = config.eventMinMinutes();
        double most = Math.max(least, config.eventMaxMinutes());
        return (long) ((least + level.random.nextDouble() * (most - least)) * 60 * 20);
    }

    private void track(FrostColossus colossus) {
        if (!colossus.getUUID().equals(id) || walking != colossus.onJourney()) {
            id = colossus.getUUID();
            walking = colossus.onJourney();
            setDirty();
        }
        see(colossus);
    }

    private void see(FrostColossus colossus) {
        missing = 0;
        boolean sleeping = colossus.activity() != FrostColossus.Activity.AWAKE;
        if (last == null || last.distanceToSqr(colossus.position()) > 4 || sleeping != asleep) {
            last = colossus.position();
            asleep = sleeping;
            setDirty();
        }
    }

    /** It is no longer in the world: the next walk is set. */
    private void forget(ServerLevel overworld) {
        id = null;
        walking = false;
        missing = 0;
        nextWalk = overworld.getGameTime() + walkInterval(overworld);
        setDirty();
    }

    // ----- Events -----

    /** The world's colossus is tracked; another is turned away, unless it was just put down (then it is the world's). */
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof FrostColossus colossus) || event.getLevel().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        if (level.dimension() != Level.OVERWORLD) {
            event.setCanceled(true);
            return;
        }
        ColossusKeeper keeper = get(level);
        if (colossus.getUUID().equals(keeper.id)) {
            keeper.track(colossus);
            return;
        }
        if (event.loadedFromDisk()) {
            // An old one, left over in a chunk nobody had loaded since it went on its way or another took its place.
            event.setCanceled(true);
            return;
        }
        if (keeper.id != null && level.getEntity(keeper.id) instanceof FrostColossus previous && previous != colossus) {
            previous.ejectPassengers();
            previous.discard();
        }
        keeper.track(colossus);
    }

    /** Killed or removed, it is gone; unloaded with its chunk, it is only out of sight. */
    public static void onLeave(EntityLeaveLevelEvent event) {
        if (!(event.getEntity() instanceof FrostColossus colossus) || event.getLevel().isClientSide()) {
            return;
        }
        ServerLevel level = (ServerLevel) event.getLevel();
        ColossusKeeper keeper = get(level);
        if (!colossus.getUUID().equals(keeper.id)) {
            return;
        }
        Entity.RemovalReason reason = colossus.getRemovalReason();
        if (reason != null && reason.shouldDestroy()) {
            if (keeper.walking) {
                announce(level, "end");
            }
            keeper.forget(level);
        } else {
            keeper.see(colossus);
        }
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD
                || level.getGameTime() % CHECK_TICKS != 0 || !CommonConfig.get().colossus().enabled()) {
            return;
        }
        get(level).check(level);
    }

    private void check(ServerLevel level) {
        if (id != null) {
            if (level.getEntity(id) instanceof FrostColossus colossus && colossus.isAlive()) {
                see(colossus);
                if (walking && hasGone(level, colossus)) {
                    // It walked on, out of everybody's way: removing it tells everybody (onLeave).
                    colossus.discard();
                }
                return;
            }
            if (walking && ++missing >= 6) {
                // Nowhere in the loaded world for half a minute: it has walked on.
                announce(level, "end");
                forget(level);
            }
            if (walking) {
                return;
            }
        }
        if (!CommonConfig.get().colossus().events()) {
            return;
        }
        if (nextWalk < 0) {
            nextWalk = level.getGameTime() + walkInterval(level);
            setDirty();
        }
        if (level.getGameTime() >= nextWalk && start(level, null) == null) {
            nextWalk = level.getGameTime() + RETRY;
            setDirty();
        }
    }

    /** {@return whether nobody rides it or is near it any more, after a while on its way} */
    private static boolean hasGone(ServerLevel level, FrostColossus colossus) {
        if (colossus.journeyTicks() < LEAST_WALK || !colossus.getPassengers().isEmpty()) {
            return false;
        }
        if (!level.isPositionEntityTicking(colossus.blockPosition())) {
            return true;
        }
        for (ServerPlayer player : level.players()) {
            if (!player.isSpectator() && Mth.square(player.getX() - colossus.getX()) + Mth.square(player.getZ() - colossus.getZ()) < GONE * GONE) {
                return false;
            }
        }
        return true;
    }

    /**
     * Sends the colossus on a walk past a player: {@code near}, or else somebody picked at random. Any other colossus
     * gives way to it.
     *
     * @return the player it walks past, or null if there was nobody, or no place for it near them
     */
    @Nullable
    public static ServerPlayer start(ServerLevel level, @Nullable ServerPlayer near) {
        List<ServerPlayer> players = new ArrayList<>();
        if (near != null) {
            players.add(near);
        } else {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && player.isAlive()) {
                    players.add(player);
                }
            }
        }
        while (!players.isEmpty()) {
            ServerPlayer player = players.remove(level.random.nextInt(players.size()));
            if (player.level() != level) {
                continue;
            }
            double[] place = findStart(level, player);
            if (place == null) {
                continue;
            }
            FrostColossus colossus = ColossusRegistry.FROST_COLOSSUS.get().create(level);
            if (colossus == null) {
                return null;
            }
            colossus.moveTo(place[0], place[1], place[2], (float) place[3], 0);
            colossus.beginJourney((float) place[3]);
            if (!level.addFreshEntity(colossus)) {
                return null;
            }
            ColossusKeeper keeper = get(level);
            keeper.track(colossus);
            keeper.nextWalk = -1;
            keeper.setDirty();
            Vec3 by = player.position();
            announce(level, "start", player.getDisplayName(), Mth.floor(by.x), Mth.floor(by.z));
            for (ServerPlayer listener : level.players()) {
                if (listener.distanceToSqr(colossus) < 200 * 200) {
                    // Far off, the first heavy step.
                    listener.playNotifySound(ColossusRegistry.STEP.get(), SoundSource.NEUTRAL, 1.5f, 0.7f);
                }
            }
            return player;
        }
        return null;
    }

    /**
     * {@return where it appears to walk past this player, and its heading: x, y, z, yaw}, or null where no such place
     * is loaded: dry, inland ground, out of everybody's way, as far from the player as the loaded world allows, with
     * land on its way to them.
     */
    @Nullable
    private static double[] findStart(ServerLevel level, ServerPlayer player) {
        for (int attempt = 0; attempt < 24; attempt++) {
            float yaw = level.random.nextFloat() * 360;
            double radians = Math.toRadians(yaw);
            double forwardX = -Math.sin(radians);
            double forwardZ = Math.cos(radians);
            double side = (level.random.nextBoolean() ? 1 : -1) * (PASS_BY + level.random.nextDouble() * PASS_BY_MORE);
            // The point of its way nearest the player, beside them.
            double byX = player.getX() + forwardZ * side;
            double byZ = player.getZ() - forwardX * side;
            for (int back = FAR; back >= NEAR; back -= 8) {
                double x = byX - forwardX * back;
                double z = byZ - forwardZ * back;
                if (fits(level, x, z) && clearOfPlayers(level, x, z) && landBetween(level, x, z, byX, byZ)) {
                    double ground = ColossusGround.height(level, x, z);
                    return new double[] {x, ground, z, yaw};
                }
            }
        }
        return null;
    }

    private static boolean fits(ServerLevel level, double x, double z) {
        return level.isPositionEntityTicking(BlockPos.containing(x, 64, z))
                && ColossusRange.inland(x, z)
                && !Double.isNaN(ColossusGround.height(level, x, z))
                && ColossusGround.waterDepth(level, x, z) <= 1;
    }

    private static boolean clearOfPlayers(ServerLevel level, double x, double z) {
        for (ServerPlayer other : level.players()) {
            if (Mth.square(other.getX() - x) + Mth.square(other.getZ() - z) < UNSEEN * UNSEEN) {
                return false;
            }
        }
        return true;
    }

    /** {@return whether the way from here to there is land it can wade (no sea, nothing unloaded)} */
    private static boolean landBetween(ServerLevel level, double fromX, double fromZ, double toX, double toZ) {
        double length = Math.sqrt(Mth.square(toX - fromX) + Mth.square(toZ - fromZ));
        for (double along = 8; along < length; along += 8) {
            double x = fromX + (toX - fromX) * along / length;
            double z = fromZ + (toZ - fromZ) * along / length;
            if (!ColossusRange.inland(x, z) || Double.isNaN(ColossusGround.height(level, x, z)) || ColossusGround.waterDepth(level, x, z) > 3) {
                return false;
            }
        }
        return true;
    }

    /** Tells everybody in the Overworld: {@code message.thepale.colossus.event.<what>}. */
    private static void announce(ServerLevel level, String what, Object... arguments) {
        Component line = Component.translatable("message.thepale.colossus.event." + what, arguments).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(line);
        }
    }
}
