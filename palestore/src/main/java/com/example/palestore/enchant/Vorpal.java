package com.example.palestore.enchant;

import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ResolvableProfile;
import net.neoforged.neoforge.event.entity.living.LivingDropsEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;

/**
 * Vorpal: a chance of a devastating strike (10% / 15% / 20% for 1.5x / 1.75x / 2x damage), and a chance of the victim
 * dropping its head (5% / 10% / 15%): zombies, skeletons, wither skeletons, creepers, piglins, the dragon and players.
 */
public final class Vorpal {
    private Vorpal() {}

    public static void onIncomingDamage(LivingIncomingDamageEvent event) {
        LivingEntity target = event.getEntity();
        if (!(target.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof LivingEntity attacker)) {
            return;
        }
        // Only a direct hit with the weapon, not arrows or thorns.
        if (event.getSource().getDirectEntity() != attacker) {
            return;
        }
        ItemStack weapon = event.getSource().getWeaponItem();
        int enchantLevel = weapon == null ? 0 : PaleEnchants.level(level.registryAccess(), PaleEnchants.VORPAL, weapon);
        if (enchantLevel <= 0) {
            return;
        }
        float chance = 0.05F + 0.05F * enchantLevel;
        if (attacker.getRandom().nextFloat() < chance) {
            event.setAmount(event.getAmount() * (1.25F + 0.25F * enchantLevel));
            level.playSound(null, target.getX(), target.getY(), target.getZ(),
                    SoundEvents.PLAYER_ATTACK_CRIT, SoundSource.PLAYERS, 1.0F, 0.8F);
            level.sendParticles(ParticleTypes.ENCHANTED_HIT, target.getX(), target.getY(0.5), target.getZ(),
                    16, target.getBbWidth() * 0.4, target.getBbHeight() * 0.3, target.getBbWidth() * 0.4, 0.2);
        }
    }

    public static void onLivingDrops(LivingDropsEvent event) {
        LivingEntity victim = event.getEntity();
        if (!(victim.level() instanceof ServerLevel level) || !(event.getSource().getEntity() instanceof LivingEntity killer)) {
            return;
        }
        ItemStack weapon = event.getSource().getWeaponItem();
        int enchantLevel = weapon == null ? 0 : PaleEnchants.level(level.registryAccess(), PaleEnchants.VORPAL, weapon);
        if (enchantLevel <= 0 || killer.getRandom().nextFloat() >= 0.05F * enchantLevel) {
            return;
        }
        ItemStack head = headOf(victim);
        if (head.isEmpty()) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            if (drop.getItem().is(head.getItem())) {
                return; // it dropped one anyway
            }
        }
        event.getDrops().add(new ItemEntity(level, victim.getX(), victim.getY(), victim.getZ(), head));
    }

    private static ItemStack headOf(Entity victim) {
        EntityType<?> type = victim.getType();
        Item head;
        if (type == EntityType.ZOMBIE) {
            head = Items.ZOMBIE_HEAD;
        } else if (type == EntityType.SKELETON) {
            head = Items.SKELETON_SKULL;
        } else if (type == EntityType.WITHER_SKELETON) {
            head = Items.WITHER_SKELETON_SKULL;
        } else if (type == EntityType.CREEPER) {
            head = Items.CREEPER_HEAD;
        } else if (type == EntityType.PIGLIN) {
            head = Items.PIGLIN_HEAD;
        } else if (type == EntityType.ENDER_DRAGON) {
            head = Items.DRAGON_HEAD;
        } else if (victim instanceof Player player) {
            ItemStack stack = new ItemStack(Items.PLAYER_HEAD);
            stack.set(DataComponents.PROFILE, new ResolvableProfile(player.getGameProfile()));
            return stack;
        } else {
            return ItemStack.EMPTY;
        }
        return new ItemStack(head);
    }
}
