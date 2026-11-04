package com.example.palestore.enchant;

import com.example.palestore.PaleStore;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.loot.IGlobalLootModifier;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Pale Enchantments: Insight, Leech, Smashing, Smelting, Soulbound and Vorpal, part of Pale Store since 0.6.0. The
 * enchantments themselves are data (data/paleenchants/enchantment); this class wires up the parts that need code.
 *
 * <p>The enchanting table (see EnchantmentMenuMixin) offers at most level III; higher levels (Leech IV) only come from
 * combining books on the anvil.
 *
 * <p>The enchantments keep the {@code paleenchants} namespace they had as a separate mod, so items enchanted with
 * Pale Enchantments 1.0.0 keep their enchantments.
 */
public final class PaleEnchants {
    /** Namespace of the enchantments (not a mod id any more). */
    public static final String NAMESPACE = "paleenchants";

    public static final ResourceKey<Enchantment> INSIGHT = key("insight");
    public static final ResourceKey<Enchantment> LEECH = key("leech");
    public static final ResourceKey<Enchantment> SMASHING = key("smashing");
    public static final ResourceKey<Enchantment> SMELTING = key("smelting");
    public static final ResourceKey<Enchantment> SOULBOUND = key("soulbound");
    public static final ResourceKey<Enchantment> VORPAL = key("vorpal");

    private static final DeferredRegister<MapCodec<? extends IGlobalLootModifier>> LOOT_MODIFIERS =
            DeferredRegister.create(NeoForgeRegistries.Keys.GLOBAL_LOOT_MODIFIER_SERIALIZERS, PaleStore.MOD_ID);

    static {
        LOOT_MODIFIERS.register("smashing", () -> SmashingModifier.CODEC);
        LOOT_MODIFIERS.register("smelting", () -> SmeltingModifier.CODEC);
    }

    private PaleEnchants() {}

    public static void register(IEventBus modBus) {
        LOOT_MODIFIERS.register(modBus);
        NeoForge.EVENT_BUS.addListener(Leech::onLivingDeath);
        NeoForge.EVENT_BUS.addListener(Vorpal::onIncomingDamage);
        NeoForge.EVENT_BUS.addListener(Vorpal::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(Soulbound::onLivingDrops);
        NeoForge.EVENT_BUS.addListener(Soulbound::onClone);
    }

    private static ResourceKey<Enchantment> key(String name) {
        return ResourceKey.create(Registries.ENCHANTMENT, ResourceLocation.fromNamespaceAndPath(NAMESPACE, name));
    }

    /** {@return the level of the enchantment on the stack; 0 if it isn't there or the enchantment isn't loaded} */
    public static int level(RegistryAccess registries, ResourceKey<Enchantment> key, ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        return registries.registryOrThrow(Registries.ENCHANTMENT).getHolder(key)
                .map(holder -> EnchantmentHelper.getItemEnchantmentLevel(holder, stack))
                .orElse(0);
    }

    public static boolean is(Holder<Enchantment> holder, ResourceKey<Enchantment> key) {
        return holder.is(key);
    }
}
