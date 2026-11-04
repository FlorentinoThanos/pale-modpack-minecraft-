package com.example.palestore.mixin.enchant;

import com.example.palestore.enchant.PaleEnchants;
import com.example.palestore.enchant.Rarity;
import java.util.List;
import java.util.stream.Stream;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Enchanting table and enchant_with_levels loot: nine times in ten, Smashing is left out of the candidates. The roll
 * uses the table's own seeded random, so the offer shown and the enchantment applied stay the same.
 */
@Mixin(EnchantmentHelper.class)
public abstract class EnchantmentHelperMixin {
    @Unique
    private static final ThreadLocal<Boolean> paleenchants$inner = ThreadLocal.withInitial(() -> false);

    @Inject(method = "selectEnchantment", at = @At("HEAD"), cancellable = true)
    private static void paleenchants$rareSmashing(
            RandomSource random, ItemStack stack, int level, Stream<Holder<Enchantment>> possible,
            CallbackInfoReturnable<List<EnchantmentInstance>> cir) {
        if (paleenchants$inner.get()) {
            return;
        }
        if (random.nextFloat() < Rarity.SMASHING_KEPT) {
            return; // Smashing may come up this time
        }
        paleenchants$inner.set(true);
        try {
            cir.setReturnValue(EnchantmentHelper.selectEnchantment(random, stack, level,
                    possible.filter(holder -> !holder.is(PaleEnchants.SMASHING))));
        } finally {
            paleenchants$inner.set(false);
        }
    }
}
