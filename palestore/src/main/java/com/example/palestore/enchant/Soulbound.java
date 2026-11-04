package com.example.palestore.enchant;

import java.util.Iterator;
import net.minecraft.core.Holder;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.GameRules;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * Soulbound: the item stays with its owner through death. Each death has a 1 in (level + 1) chance of taking a level
 * off (50% / 33% / 25%); at level I that means losing the enchantment, though the item is still kept that time.
 *
 * <p>The kept items wait in the player's persisted data between death and respawn, so they survive a server restart
 * or a log-out on the death screen.
 */
public final class Soulbound {
    private static final String KEY = "paleenchants:soulbound";

    private Soulbound() {}

    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) {
            return;
        }
        RegistryAccess registries = player.level().registryAccess();
        Holder<Enchantment> soulbound = registries.registryOrThrow(Registries.ENCHANTMENT).getHolder(PaleEnchants.SOULBOUND).orElse(null);
        if (soulbound == null) {
            return;
        }
        ListTag kept = new ListTag();
        Iterator<ItemEntity> drops = event.getDrops().iterator();
        while (drops.hasNext()) {
            ItemStack stack = drops.next().getItem();
            int level = EnchantmentHelper.getItemEnchantmentLevel(soulbound, stack);
            if (level <= 0) {
                continue;
            }
            drops.remove();
            if (player.getRandom().nextInt(level + 1) == 0) {
                EnchantmentHelper.updateEnchantments(stack, enchantments -> enchantments.set(soulbound, level - 1));
            }
            kept.add(stack.save(registries));
        }
        if (!kept.isEmpty()) {
            persisted(player).put(KEY, kept);
        }
    }

    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag data = persisted(player);
        if (!data.contains(KEY, Tag.TAG_LIST)) {
            return;
        }
        ListTag kept = data.getList(KEY, Tag.TAG_COMPOUND);
        data.remove(KEY);
        RegistryAccess registries = player.level().registryAccess();
        for (int i = 0; i < kept.size(); i++) {
            ItemStack stack = ItemStack.parseOptional(registries, kept.getCompound(i));
            if (!stack.isEmpty() && !player.getInventory().add(stack)) {
                player.drop(stack, false);
            }
        }
    }

    /** The part of the player's data NeoForge carries over death (Player.PERSISTED_NBT_TAG). */
    private static CompoundTag persisted(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(Player.PERSISTED_NBT_TAG, Tag.TAG_COMPOUND)) {
            root.put(Player.PERSISTED_NBT_TAG, new CompoundTag());
        }
        return root.getCompound(Player.PERSISTED_NBT_TAG);
    }
}
