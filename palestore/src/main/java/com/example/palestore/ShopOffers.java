package com.example.palestore;

import java.util.List;
import java.util.Optional;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Items unlocked by advancement progress. Add offers here to require more advancements.
 * The lock works on the crafted ITEM: every crafting recipe that outputs it is blocked until unlocked.
 */
public final class ShopOffers {
    public record Offer(ResourceLocation id, Item result, ResourceLocation requiredAdvancement) {}

    public static final List<Offer> ALL = List.of(
            new Offer(PaleStore.id("diamond_pickaxe"), Items.DIAMOND_PICKAXE,
                    ResourceLocation.withDefaultNamespace("story/enter_the_nether"))
    );

    public static Optional<Offer> byId(ResourceLocation id) {
        return ALL.stream().filter(o -> o.id().equals(id)).findFirst();
    }

    public static Optional<Offer> byResult(ItemStack stack) {
        return ALL.stream().filter(o -> stack.is(o.result())).findFirst();
    }

    private ShopOffers() {}
}
