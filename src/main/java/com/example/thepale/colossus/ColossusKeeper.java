package com.example.thepale.colossus;

import com.example.thepale.config.CommonConfig;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the Frost Colossus one of a kind. The Overworld remembers which colossus is the world's, where it was last
 * and whether it slept; another one that turns up is sent away (the newest one put down by hand wins), one that is
 * gone comes back on the cold island after {@code respawnMinutes}, and the very first appears there as soon as a
 * player comes near enough for its home to be loaded.
 *
 * <p>Nothing here loads a chunk: it looks every five seconds at what is loaded already.
 */
public final class ColossusKeeper extends SavedData {
    private static final String NAME = "thepale_colossus";
    private static final int CHECK_TICKS = 100;
    /** It doesn't appear right in front of somebody: nobody may be this near its home. */
    private static final double UNSEEN = 40;

    @Nullable
    private UUID id;
    @Nullable
    private Vec3 last;
    private boolean asleep;
    private boolean everSpawned;
    /** The game time it went at, or -1 while it is in the world. */
    private long goneSince = -1;
    @Nullable
    private BlockPos home;
    /** How many checks in a row its place was loaded but it wasn't there. */
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
        if (tag.contains("LastX")) {
            keeper.last = new Vec3(tag.getDouble("LastX"), tag.getDouble("LastY"), tag.getDouble("LastZ"));
        }
        keeper.asleep = tag.getBoolean("Asleep");
        keeper.everSpawned = tag.getBoolean("EverSpawned");
        keeper.goneSince = tag.contains("GoneSince") ? tag.getLong("GoneSince") : -1;
        keeper.home = NbtUtils.readBlockPos(tag, "Home").orElse(null);
        return keeper;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        if (id != null) {
            tag.putUUID("Id", id);
        }
        if (last != null) {
            tag.putDouble("LastX", last.x);
            tag.putDouble("LastY", last.y);
            tag.putDouble("LastZ", last.z);
        }
        tag.putBoolean("Asleep", asleep);
        tag.putBoolean("EverSpawned", everSpawned);
        tag.putLong("GoneSince", goneSince);
        if (home != null) {
            tag.put("Home", NbtUtils.writeBlockPos(home));
        }
        return tag;
    }

    /** {@return where it was when last seen, or null if it never was} */
    @Nullable
    public Vec3 last() {
        return last;
    }

    public boolean asleep() {
        return asleep;
    }

    /** {@return how many ticks until it comes back, if it is gone; else -1} */
    public long comesBackIn(ServerLevel overworld) {
        if (id != null || !everSpawned || goneSince < 0) {
            return -1;
        }
        return Math.max(0, respawnTicks() - (overworld.getGameTime() - goneSince));
    }

    /** {@return what it knows, for /thepale colossus status} */
    public String status(ServerLevel overworld) {
        return "World's colossus: " + (id == null ? "none" : id)
                + (last == null ? "" : String.format(", last at %.0f %.0f %.0f%s", last.x, last.y, last.z, asleep ? " asleep" : ""))
                + (comesBackIn(overworld) >= 0 ? ", back in " + comesBackIn(overworld) / 20 + " s" : "")
                + (home == null ? "" : ", home " + home.toShortString());
    }

    private static long respawnTicks() {
        return (long) (CommonConfig.get().colossus().respawnMinutes() * 60 * 20);
    }

    private void track(FrostColossus colossus) {
        if (!colossus.getUUID().equals(id) || goneSince >= 0 || !everSpawned) {
            id = colossus.getUUID();
            goneSince = -1;
            everSpawned = true;
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

    private void gone(ServerLevel overworld) {
        id = null;
        goneSince = overworld.getGameTime();
        missing = 0;
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
        if (keeper.id == null || keeper.id.equals(colossus.getUUID())) {
            keeper.track(colossus);
            return;
        }
        if (event.loadedFromDisk()) {
            // An old one, left over in a chunk nobody had loaded since another took its place.
            event.setCanceled(true);
            return;
        }
        Entity old = level.getEntity(keeper.id);
        if (old instanceof FrostColossus previous && previous != colossus) {
            previous.ejectPassengers();
            previous.discard();
        }
        keeper.track(colossus);
    }

    /** Killed or removed, it is gone (and will come back); unloaded with its chunk, it is only out of sight. */
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
            keeper.gone(level);
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
            } else if (last != null && level.isPositionEntityTicking(BlockPos.containing(last))) {
                // Its place is loaded and it isn't there: lost (a broken chunk, an editor). After a while, gone.
                if (++missing >= 3) {
                    gone(level);
                }
            }
            return;
        }
        if (everSpawned && level.getGameTime() - goneSince < respawnTicks()) {
            return;
        }
        if (home == null) {
            home = ColossusRange.findHome(level, 0);
            if (home == null) {
                return;
            }
            setDirty();
        }
        if (!level.isPositionEntityTicking(home)) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.distanceToSqr(home.getX() + 0.5, player.getY(), home.getZ() + 0.5) < UNSEEN * UNSEEN) {
                return;
            }
        }
        FrostColossus colossus = ColossusRegistry.FROST_COLOSSUS.get().create(level);
        if (colossus == null) {
            return;
        }
        double ground = ColossusGround.height(level, home.getX() + 0.5, home.getZ() + 0.5);
        colossus.moveTo(home.getX() + 0.5, Double.isNaN(ground) ? home.getY() : ground, home.getZ() + 0.5, level.random.nextFloat() * 360, 0);
        level.addFreshEntity(colossus);
    }
}
