package com.example.thepale.colossus;

import com.example.thepale.ThePale;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.server.level.ServerPlayer;

/**
 * The colossus's advancements (data/thepale/advancement/colossus/): each has one criterion that only this code
 * grants, when the player sees it, rides it, has ridden it far, and takes a cache off it.
 */
public final class ColossusAdvancements {
    /** «Giant of the North»: seeing it. */
    public static final String SEEN = "colossus/seen";
    /** «On the Giant's Shoulders»: sitting on its shoulder. */
    public static final String RIDE = "colossus/ride";
    /** «Sightseeing»: riding it {@value #EXCURSION_BLOCKS} blocks. */
    public static final String EXCURSION = "colossus/excursion";
    public static final double EXCURSION_BLOCKS = 500;
    /** «The Giant's Cargo»: taking a cache off it. */
    public static final String CACHE = "colossus/cache";
    /** How far a player has ridden it, kept with the player. */
    private static final String RIDDEN = "thepale.colossusRidden";

    private ColossusAdvancements() {}

    /** {@return whether the player has this one already} (also when it can't be found: then there is nothing to give) */
    public static boolean has(ServerPlayer player, String path) {
        AdvancementHolder holder = player.server.getAdvancements().get(ThePale.id(path));
        return holder == null || player.getAdvancements().getOrStartProgress(holder).isDone();
    }

    public static void award(ServerPlayer player, String path) {
        AdvancementHolder holder = player.server.getAdvancements().get(ThePale.id(path));
        if (holder != null && !player.getAdvancements().getOrStartProgress(holder).isDone()) {
            player.getAdvancements().award(holder, "done");
        }
    }

    /** The colossus carried this rider so far this tick. */
    public static void rode(ServerPlayer player, double blocks) {
        if (blocks <= 0 || has(player, EXCURSION)) {
            return;
        }
        double ridden = player.getPersistentData().getDouble(RIDDEN) + blocks;
        player.getPersistentData().putDouble(RIDDEN, ridden);
        if (ridden >= EXCURSION_BLOCKS) {
            award(player, EXCURSION);
        }
    }
}
