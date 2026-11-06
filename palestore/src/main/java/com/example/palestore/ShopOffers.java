package com.example.palestore;

import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * Locked recipes. The lock works on the crafted ITEM: every crafting recipe whose result matches is blocked until
 * the player has the offer's advancement.
 *
 * The quest-book offers use advancements under {@code palestore:unlock/} that have no trigger of their own
 * ({@code minecraft:impossible}): an FTB Quests command reward grants them with
 * {@code /advancement grant @p only palestore:unlock/<name>}.
 */
public final class ShopOffers {
    /**
     * @param id          offer id (also the JEI recipe id)
     * @param matches     which crafted stacks this offer locks
     * @param icon        item shown in JEI and in the unlock message
     * @param requiredAdvancement advancement that unlocks it
     */
    public record Offer(ResourceLocation id, Predicate<ItemStack> matches, Item icon, ResourceLocation requiredAdvancement) {
        public String requirementKey() {
            return "palestore.offer." + id.getPath() + ".requirement";
        }
    }

    public static final List<Offer> ALL = List.of(
            questOffer("bed", ItemTags.BEDS, Items.RED_BED),
            questOffer("ladder", Items.LADDER),
            questOffer("diamond_pickaxe", Items.DIAMOND_PICKAXE)
    );

    private static Offer questOffer(String name, Item item) {
        return new Offer(PaleStore.id(name), stack -> stack.is(item), item, PaleStore.id("unlock/" + name));
    }

    private static Offer questOffer(String name, TagKey<Item> tag, Item icon) {
        return new Offer(PaleStore.id(name), stack -> stack.is(tag), icon, PaleStore.id("unlock/" + name));
    }

    public static Optional<Offer> byId(ResourceLocation id) {
        return ALL.stream().filter(o -> o.id().equals(id)).findFirst();
    }

    public static Optional<Offer> byResult(ItemStack stack) {
        if (stack.isEmpty()) return Optional.empty();
        return ALL.stream().filter(o -> o.matches().test(stack)).findFirst();
    }

    private ShopOffers() {}
}
