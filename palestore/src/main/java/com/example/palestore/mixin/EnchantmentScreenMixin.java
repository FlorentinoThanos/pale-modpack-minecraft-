package com.example.palestore.mixin;

import com.example.palestore.EnchantingRules;
import com.example.palestore.EnchantingMenuAccess;
import com.example.palestore.AbstractContainerScreenAccess;
import com.example.palestore.XpUtil;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.EnchantmentScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.EnchantmentMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Displays point prices and bookshelf lock states in the enchanting-table screen. */
@Mixin(value = EnchantmentScreen.class, remap = false)
public abstract class EnchantmentScreenMixin {
    private EnchantmentMenu palestore$menu() {
        return (EnchantmentMenu) ((AbstractContainerScreenAccess) this).palestore$getMenu();
    }

    @Inject(method = "renderBg", at = @At("HEAD"), remap = false)
    private void palestore$updateTierAvailability(GuiGraphics graphics, float partialTick,
            int mouseX, int mouseY, CallbackInfo ci) {
        EnchantmentMenu menu = this.palestore$menu();
        for (int option = 0; option < menu.costs.length; option++) {
            // The client-side menu has ContainerLevelAccess.NULL and cannot inspect
            // the table's surroundings. The server-synced clue is authoritative.
            boolean hasOffer = menu.enchantClue[option] != -1;
            menu.costs[option] = hasOffer ? EnchantingRules.xpCost(option) : 0;
        }
    }

    @Redirect(method = "renderBg", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I"), remap = false)
    private int palestore$showAffordableOptionsByPoints(LocalPlayer player) {
        return XpUtil.total(player);
    }

    @Redirect(method = "render", at = @At(value = "FIELD",
            target = "Lnet/minecraft/client/player/LocalPlayer;experienceLevel:I"), remap = false)
    private int palestore$showTooltipByPoints(LocalPlayer player) {
        return XpUtil.total(player);
    }

    @ModifyConstant(method = "render", constant = @org.spongepowered.asm.mixin.injection.Constant(
            stringValue = "container.enchant.level.requirement"), remap = false)
    private String palestore$renameRequirementToXp(String original) {
        return "palestore.enchant.xp.requirement";
    }
}
