package com.example.thepale.finale;

import com.example.thepale.ThePale;
import com.example.thepale.finale.boss.PatchworkAbomination;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The finale's entity and items. */
public final class FinaleRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ThePale.MOD_ID);
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(ThePale.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<PatchworkAbomination>> PATCHWORK_ABOMINATION = ENTITIES.register(
            "patchwork_abomination",
            () -> EntityType.Builder.<PatchworkAbomination>of(PatchworkAbomination::new, MobCategory.MONSTER)
                    .sized(9f, 9f)
                    .fireImmune()
                    // Never saved with its chunk: the invasion brings it back (see PatchworkAbomination).
                    .noSave()
                    .clientTrackingRange(12)
                    .updateInterval(1)
                    .build("patchwork_abomination"));

    public static final DeferredItem<Item> HEART_OF_THE_PALE = ITEMS.register(
            "heart_of_the_pale", () -> new HeartOfThePaleItem(new Item.Properties().stacksTo(1).rarity(Rarity.EPIC).fireResistant()));

    public static final DeferredItem<Item> ABOMINATION_SPAWN_EGG = ITEMS.register(
            "patchwork_abomination_spawn_egg", () -> new DeferredSpawnEggItem(PATCHWORK_ABOMINATION, 0x7A6880, 0xE8E0F5, new Item.Properties()));

    private FinaleRegistry() {}

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        ITEMS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) ->
                event.put(PATCHWORK_ABOMINATION.get(), PatchworkAbomination.createAttributes().build()));
        modBus.addListener((BuildCreativeModeTabContentsEvent event) -> {
            if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
                event.accept(ABOMINATION_SPAWN_EGG.get());
            } else if (event.getTabKey() == CreativeModeTabs.INGREDIENTS) {
                event.accept(HEART_OF_THE_PALE.get());
            }
        });
    }
}
