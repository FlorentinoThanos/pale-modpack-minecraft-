package com.example.thepale.colossus.client;

import com.example.thepale.colossus.ColossusPart;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.EntityHitResult;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** The line over the hotbar that says the colossus can be climbed (or a cache on it opened), while the crosshair is on it. */
public final class ColossusHints {
    private static final Component CLIMB = Component.translatable("message.thepale.colossus.climb");
    private static final Component CACHE = Component.translatable("message.thepale.colossus.cache");
    private static boolean shown;
    /** After a click the colossus answers in the same line; the hint keeps out of its way for a while. */
    private static int quiet;

    private ColossusHints() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            shown = false;
            return;
        }
        if (quiet > 0) {
            quiet--;
        }
        boolean onCache = minecraft.screen == null
                && minecraft.hitResult instanceof EntityHitResult cacheHit
                && cacheHit.getEntity() instanceof ColossusPart cachePart
                && cachePart.isCache()
                && !minecraft.player.isSpectator();
        if (onCache && quiet == 0) {
            minecraft.gui.setOverlayMessage(CACHE, false);
            shown = true;
            if (minecraft.options.keyUse.isDown()) {
                quiet = 40;
            }
            return;
        }
        boolean aimed = minecraft.screen == null
                && minecraft.hitResult instanceof EntityHitResult hit
                && hit.getEntity() instanceof ColossusPart part
                && part.getParent().offersRide()
                && minecraft.player.getMainHandItem().isEmpty()
                && !minecraft.player.isPassenger()
                && !minecraft.player.isSpectator();
        if (aimed && minecraft.options.keyUse.isDown()) {
            quiet = 60;
            shown = false;
            return;
        }
        if (aimed && quiet == 0) {
            minecraft.gui.setOverlayMessage(CLIMB, false);
            shown = true;
        } else if (shown) {
            // Looked away: the line goes at once instead of lingering.
            minecraft.gui.setOverlayMessage(Component.empty(), false);
            shown = false;
        }
    }
}
