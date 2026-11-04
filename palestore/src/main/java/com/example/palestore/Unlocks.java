package com.example.palestore;

import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** Advancement-backed unlock checks. Minecraft saves the advancement progress per player. */
public final class Unlocks {
    public static Set<ResourceLocation> get(ServerPlayer player) {
        return ShopOffers.ALL.stream()
                .filter(offer -> has(player, offer.id()))
                .map(ShopOffers.Offer::id)
                .collect(Collectors.toUnmodifiableSet());
    }

    public static boolean has(ServerPlayer player, ResourceLocation id) {
        ShopOffers.Offer offer = ShopOffers.byId(id).orElse(null);
        if (offer == null) return false;
        var advancement = player.server.getAdvancements().get(offer.requiredAdvancement());
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    private Unlocks() {}
}
