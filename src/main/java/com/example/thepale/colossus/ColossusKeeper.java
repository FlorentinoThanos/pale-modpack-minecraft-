package com.example.thepale.colossus;

import com.example.thepale.config.CommonConfig;
import com.example.thepale.worldgen.PaleShape;
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
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
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
 * <p>Every {@code eventMinMinutes} to {@code eventMaxMinutes} of play, if somebody is on the northern (cold) island,
 * it comes out of the Pale beyond that island's coast, wades through the sea, crosses the island in a straight line
 * past that player and wades out into the Pale on the far side, where it is gone. Everybody in the Overworld is told
 * in chat when it comes and when it goes. It walks the whole way watched or not: the world around it stays loaded
 * (see {@link FrostColossus}). The Overworld remembers which colossus is the world's and where it was last; another
 * one that turns up is sent away (the newest one put down by hand wins).
 */
public final class ColossusKeeper extends SavedData {
    private static final String NAME = "thepale_colossus";
    private static final int CHECK_TICKS = 100;
    /** It comes out of the Pale, and goes back into it, this far out past the coast: deep in the fog. */
    private static final double PALE_DEPTH = 210;
    /** Its way passes the player this far to one side, so that it walks past them and not over them. */
    private static final double PASS_BY = 30;
    private static final double PASS_BY_MORE = 40;
    /** Somebody this far past the coast at most counts as on the island. */
    private static final double ON_ISLAND = 40;
    /** When nobody is on the northern island, the next try is this much later, in ticks. */
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
    /** A walk about to begin: where it comes out of the Pale, its heading and where it goes (until it is there). */
    @Nullable
    private double[] pending;

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
        if (tag.contains("Pending")) {
            CompoundTag pending = tag.getCompound("Pending");
            keeper.pending = new double[] {pending.getDouble("X"), pending.getDouble("Z"), pending.getDouble("Yaw"), pending.getDouble("ToX"), pending.getDouble("ToZ")};
        }
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
        if (pending != null) {
            CompoundTag out = new CompoundTag();
            out.putDouble("X", pending[0]);
            out.putDouble("Z", pending[1]);
            out.putDouble("Yaw", pending[2]);
            out.putDouble("ToX", pending[3]);
            out.putDouble("ToZ", pending[4]);
            tag.put("Pending", out);
        }
        return tag;
    }

    /** {@return where it was when last seen (or where it is about to come out of the Pale), or null if it is not in the world} */
    @Nullable
    public Vec3 last() {
        if (pending != null) {
            return new Vec3(pending[0], 64, pending[1]);
        }
        return id == null ? null : last;
    }

    public boolean asleep() {
        return asleep;
    }

    /** {@return how many ticks until its next walk, if it is not out on one and walks are on; else -1} */
    public long nextWalkIn(ServerLevel overworld) {
        if (walking || pending != null || nextWalk < 0 || !CommonConfig.get().colossus().events()) {
            return -1;
        }
        return Math.max(0, nextWalk - overworld.getGameTime());
    }

    /** {@return what it knows, for /thepale colossus status} */
    public String status(ServerLevel overworld) {
        return "World's colossus: " + (id == null ? (pending != null ? String.format("coming out of the Pale at %.0f %.0f", pending[0], pending[1]) : "none")
                        : id + (walking ? " (on its walk)" : " (summoned)"))
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
        if (pending != null) {
            comeOut(level);
            return;
        }
        if (id != null) {
            if (level.getEntity(id) instanceof FrostColossus colossus && colossus.isAlive()) {
                see(colossus);
                return;
            }
            if (walking) {
                // Not loaded (the server was restarted): its place is loaded again, and it walks on from there.
                if (last != null) {
                    ChunkPos chunk = new ChunkPos(BlockPos.containing(last));
                    level.getChunkSource().addRegionTicket(FrostColossus.WALK_TICKET, chunk, 3, chunk);
                }
                if (++missing >= 24) {
                    announce(level, "end");
                    forget(level);
                }
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

    /** The place it comes out of the Pale is kept loaded; once it is, out it comes. */
    private void comeOut(ServerLevel level) {
        BlockPos at = BlockPos.containing(pending[0], 64, pending[1]);
        ChunkPos chunk = new ChunkPos(at);
        level.getChunkSource().addRegionTicket(FrostColossus.WALK_TICKET, chunk, 3, chunk);
        if (!level.isPositionEntityTicking(at)) {
            return;
        }
        double ground = ColossusGround.height(level, pending[0], pending[1]);
        FrostColossus colossus = ColossusRegistry.FROST_COLOSSUS.get().create(level);
        if (colossus == null || Double.isNaN(ground)) {
            return;
        }
        colossus.moveTo(pending[0], ground, pending[1], (float) pending[2], 0);
        colossus.beginJourney(pending[3], pending[4]);
        pending = null;
        setDirty();
        if (level.addFreshEntity(colossus)) {
            track(colossus);
        }
    }

    /**
     * Sends the colossus on its walk past a player: {@code near}, or else somebody on the northern island picked at
     * random. It comes out of the Pale beyond the coast of that player's island, crosses the island past them and goes
     * into the Pale on the far side. Any other colossus gives way to it.
     *
     * @return the player it walks past, or null if nobody is on the island
     */
    @Nullable
    public static ServerPlayer start(ServerLevel level, @Nullable ServerPlayer near) {
        PaleShape shape = PaleShape.server();
        List<ServerPlayer> players = new ArrayList<>();
        if (near != null) {
            players.add(near);
        } else {
            for (ServerPlayer player : level.players()) {
                if (!player.isSpectator() && player.isAlive() && ColossusRange.home(player.getX(), player.getZ())
                        && shape.effectiveRadius(player.getX(), player.getZ()) < shape.landEnd() + ON_ISLAND) {
                    players.add(player);
                }
            }
        }
        if (players.isEmpty()) {
            return null;
        }
        ServerPlayer player = players.get(level.random.nextInt(players.size()));
        if (player.level() != level) {
            return null;
        }
        float yaw = level.random.nextFloat() * 360;
        double radians = Math.toRadians(yaw);
        double forwardX = -Math.sin(radians);
        double forwardZ = Math.cos(radians);
        double side = (level.random.nextBoolean() ? 1 : -1) * (PASS_BY + level.random.nextDouble() * PASS_BY_MORE);
        // The point of its way nearest the player, beside them; from there back to the Pale it comes out of, and on to
        // the Pale it goes into.
        double byX = player.getX() + forwardZ * side;
        double byZ = player.getZ() - forwardX * side;
        double[] from = intoThePale(shape, byX, byZ, -forwardX, -forwardZ);
        double[] to = intoThePale(shape, byX, byZ, forwardX, forwardZ);
        ColossusKeeper keeper = get(level);
        if (keeper.id != null && level.getEntity(keeper.id) instanceof FrostColossus previous) {
            previous.ejectPassengers();
            previous.discard();
        }
        keeper.pending = new double[] {from[0], from[1], yaw, to[0], to[1]};
        keeper.nextWalk = -1;
        keeper.setDirty();
        keeper.comeOut(level);
        Vec3 by = player.position();
        announce(level, "start", player.getDisplayName(), Mth.floor(by.x), Mth.floor(by.z));
        return player;
    }

    /** {@return the place this far out in the Pale, going from here in this direction} */
    private static double[] intoThePale(PaleShape shape, double x, double z, double stepX, double stepZ) {
        for (int along = 0; along < 20_000; along += 4) {
            double atX = x + stepX * along;
            double atZ = z + stepZ * along;
            if (shape.effectiveRadius(atX, atZ) >= shape.landEnd() + PALE_DEPTH) {
                return new double[] {atX, atZ};
            }
        }
        return new double[] {x + stepX * 20_000, z + stepZ * 20_000};
    }

    /** Tells everybody in the Overworld: {@code message.thepale.colossus.event.<what>}. */
    private static void announce(ServerLevel level, String what, Object... arguments) {
        Component line = Component.translatable("message.thepale.colossus.event." + what, arguments).withStyle(ChatFormatting.AQUA);
        for (ServerPlayer player : level.players()) {
            player.sendSystemMessage(line);
        }
    }
}
