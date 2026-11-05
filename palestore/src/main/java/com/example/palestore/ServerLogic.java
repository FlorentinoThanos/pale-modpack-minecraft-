package com.example.palestore;

import com.example.palestore.net.Network;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

public final class ServerLogic {

    /** Sync immediately when the player earns an advancement required by an offer. */
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        boolean unlockChanged = ShopOffers.ALL.stream()
                .anyMatch(offer -> offer.requiredAdvancement().equals(event.getAdvancement().id()));
        if (!unlockChanged) return;

        Network.syncTo(player);
        player.containerMenu.broadcastChanges();
        player.displayClientMessage(Component.translatable("palestore.message.unlocked",
                Component.translatable("advancements.story.enter_the_nether.title")), true);
    }

    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Network.syncTo(player);
    }

    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) Network.syncTo(player);
    }

    private ServerLogic() {}
}
