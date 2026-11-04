package com.example.palestore.enchant;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;

/** Leech: every kill made with the weapon heals its wielder one heart per level. */
public final class Leech {
    private static final float HEALTH_PER_LEVEL = 2.0F;

    private Leech() {}

    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide()
                || !(event.getSource().getEntity() instanceof LivingEntity killer)
                || killer == event.getEntity()) {
            return;
        }
        ItemStack weapon = event.getSource().getWeaponItem();
        if (weapon == null) {
            return;
        }
        int level = PaleEnchants.level(killer.level().registryAccess(), PaleEnchants.LEECH, weapon);
        if (level > 0 && killer.isAlive()) {
            killer.heal(level * HEALTH_PER_LEVEL);
        }
    }
}
