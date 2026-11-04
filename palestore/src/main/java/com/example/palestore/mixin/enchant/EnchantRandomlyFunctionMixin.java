package com.example.palestore.mixin.enchant;

import com.example.palestore.enchant.PaleEnchants;
import com.example.palestore.enchant.Rarity;
import java.util.List;
import java.util.Optional;
import net.minecraft.Util;
import net.minecraft.core.Holder;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.level.storage.loot.functions.EnchantRandomlyFunction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Chest loot (enchant_randomly): when Smashing is picked, nine times in ten another enchantment is picked instead. */
@Mixin(EnchantRandomlyFunction.class)
public abstract class EnchantRandomlyFunctionMixin {
    @Redirect(
            method = "run",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/Util;getRandomSafe(Ljava/util/List;Lnet/minecraft/util/RandomSource;)Ljava/util/Optional;"))
    private <T> Optional<T> paleenchants$rareSmashing(List<T> options, RandomSource random) {
        Optional<T> picked = Util.getRandomSafe(options, random);
        if (picked.isPresent() && picked.get() instanceof Holder<?> holder && holder.is(PaleEnchants.SMASHING.location())
                && !Rarity.keep(paleenchants$cast(holder), random)) {
            List<T> others = options.stream()
                    .filter(option -> !(option instanceof Holder<?> h && h.is(PaleEnchants.SMASHING.location())))
                    .toList();
            return Util.getRandomSafe(others, random);
        }
        return picked;
    }

    @SuppressWarnings("unchecked")
    private static Holder<Enchantment> paleenchants$cast(Holder<?> holder) {
        return (Holder<Enchantment>) holder;
    }
}
