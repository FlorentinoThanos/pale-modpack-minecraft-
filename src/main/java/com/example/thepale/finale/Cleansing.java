package com.example.thepale.finale;

import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;

/** The cleansing of the Pale. Part 3 fills this in; until then the Heart of the Pale does nothing. */
public final class Cleansing {
    private Cleansing() {}

    /** {@return whether the heart was used up} */
    static boolean useHeart(Player player) {
        player.displayClientMessage(Component.translatable("message.thepale.finale.heart_silent"), true);
        return false;
    }
}
