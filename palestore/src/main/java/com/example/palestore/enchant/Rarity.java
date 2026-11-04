package com.example.palestore.enchant;

import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;

/**
 * Smashing is ten times rarer than its weight allows (weight 1 is already the lowest, shared with Silk Touch): when it
 * would be offered or rolled, it stays only one time in ten.
 */
public final class Rarity {
    /** Share of the times Smashing comes up that it is actually kept. */
    public static final float SMASHING_KEPT = 0.1F;

    private Rarity() {}

    /** {@return whether Smashing, chosen by the game, is kept this time; uses the game's own random for repeatability} */
    public static boolean keep(Holder<Enchantment> enchantment, RandomSource random) {
        return !enchantment.is(PaleEnchants.SMASHING) || random.nextFloat() < SMASHING_KEPT;
    }
}
