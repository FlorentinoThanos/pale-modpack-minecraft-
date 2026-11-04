package com.example.palestore.mixin;

import com.example.palestore.EnchantingRules;
import com.example.palestore.EnchantingMenuAccess;
import com.example.palestore.XpUtil;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.RegistryAccess;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.EnchantmentMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentInstance;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Replaces enchanting-table level charges with fixed total-XP costs. */
@Mixin(value = EnchantmentMenu.class, remap = false)
public abstract class EnchantmentMenuMixin implements EnchantingMenuAccess {
    @Shadow @Final public int[] costs;
    @Shadow @Final public int[] enchantClue;
    @Shadow @Final public int[] levelClue;
    @Shadow @Final private ContainerLevelAccess access;
    @Shadow @Final private net.minecraft.world.Container enchantSlots;
    @Shadow @Final private RandomSource random;
    @Unique private final int[] palestore$vanillaPowers = new int[3];

    @Shadow
    public int getEnchantmentSeed() {
        throw new AssertionError();
    }

    @Shadow
    private List<EnchantmentInstance> getEnchantmentList(RegistryAccess registryAccess, ItemStack item, int option, int power) {
        throw new AssertionError();
    }

    @Inject(method = "slotsChanged", at = @At("TAIL"), remap = false)
    private void palestore$replaceOffers(net.minecraft.world.Container container, CallbackInfo ci) {
        // Player inventory changes also call slotsChanged. Rebuild offers only after
        // the enchanting input itself has changed and vanilla has finished recalculating.
        if (container != this.enchantSlots) return;
        ItemStack item = this.enchantSlots.getItem(0);
        this.access.execute((level, tablePos) -> {
            RegistryAccess registries = level.registryAccess();
            int validBookshelves = EnchantingRules.countValidBookshelves(level, tablePos);
            for (int option = 0; option < this.costs.length; option++) {
                // Vanilla has just calculated this option's power from the sum of
                // valid bookshelf bonuses. Preserve it before replacing the XP price.
                int vanillaPower = this.costs[option];
                this.palestore$vanillaPowers[option] = vanillaPower;
                this.enchantClue[option] = -1;
                this.levelClue[option] = -1;
                if (validBookshelves < EnchantingRules.bookshelfRequirement(option)
                        || vanillaPower <= 0 || item.isEmpty() || !item.isEnchantable()) {
                    this.costs[option] = 0;
                    continue;
                }

                List<EnchantmentInstance> offers = this.getEnchantmentList(registries, item, option, vanillaPower);
                if (offers.isEmpty()) {
                    this.costs[option] = 0;
                    continue;
                }

                this.costs[option] = EnchantingRules.xpCost(option);
                // getEnchantmentList resets the menu RNG to seed + option. Consume
                // the same next value vanilla uses to choose the displayed clue.
                EnchantmentInstance chosen = offers.get(this.random.nextInt(offers.size()));
                this.enchantClue[option] = registries.registryOrThrow(Registries.ENCHANTMENT)
                        .asHolderIdMap().getId(chosen.enchantment);
                var enchantment = chosen.enchantment.value();
                this.levelClue[option] = Math.max(enchantment.getMinLevel(),
                        Math.min(option + 1, enchantment.getMaxLevel()));
            }
        });
    }

    @Inject(method = "clickMenuButton", at = @At("HEAD"), cancellable = true, remap = false)
    private void palestore$checkPointCostAndBookshelves(Player player, int option, CallbackInfoReturnable<Boolean> cir) {
        if (option < 0 || option >= this.costs.length || this.costs[option] <= 0) return;
        // EnchantmentScreen calls clickMenuButton locally before sending the
        // inventory-button packet. The client menu has ContainerLevelAccess.NULL,
        // so only the server can authoritatively check the table's shelves.
        if (!player.level().isClientSide
                && EnchantingRules.countValidBookshelves(this.access) < EnchantingRules.bookshelfRequirement(option)) {
            cir.setReturnValue(false);
            return;
        }
        if (!player.getAbilities().instabuild && XpUtil.total(player) < EnchantingRules.xpCost(option)) {
            cir.setReturnValue(false);
        }
    }

    @Redirect(method = "clickMenuButton", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Player;experienceLevel:I", ordinal = 0), remap = false)
    private int palestore$firstCostCheckUsesTotalPoints(Player player) {
        return XpUtil.total(player);
    }

    @Redirect(method = "clickMenuButton", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/player/Player;experienceLevel:I", ordinal = 1), remap = false)
    private int palestore$secondCostCheckUsesTotalPoints(Player player) {
        return XpUtil.total(player);
    }

    @Redirect(method = "lambda$clickMenuButton$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/EnchantmentMenu;getEnchantmentList(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/item/ItemStack;II)Ljava/util/List;"), remap = false)
    private List<EnchantmentInstance> palestore$useTierEnchantmentPower(EnchantmentMenu menu,
            RegistryAccess registryAccess, ItemStack item, int option, int displayedXpCost) {
        int requestedLevel = option + 1;
        return this.getEnchantmentList(registryAccess, item, option, this.palestore$vanillaPowers[option]).stream()
                .map(instance -> {
                    var enchantment = instance.enchantment.value();
                    int level = Math.max(enchantment.getMinLevel(),
                            Math.min(requestedLevel, enchantment.getMaxLevel()));
                    return new EnchantmentInstance(instance.enchantment, level);
                })
                .toList();
    }

    @Redirect(method = "lambda$clickMenuButton$1", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;onEnchantmentPerformed(Lnet/minecraft/world/item/ItemStack;I)V"), remap = false)
    private void palestore$chargeExactXpPoints(Player player, ItemStack item, int vanillaLevelCost) {
        player.onEnchantmentPerformed(item, 0);
        if (!player.getAbilities().instabuild) {
            player.giveExperiencePoints(-EnchantingRules.xpCost(vanillaLevelCost - 1));
        }
    }

    @Override
    public int palestore$getBookshelfCount() {
        return EnchantingRules.countValidBookshelves(this.access);
    }
}
