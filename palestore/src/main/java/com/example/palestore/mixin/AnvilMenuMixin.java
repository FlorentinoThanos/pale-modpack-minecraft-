package com.example.palestore.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Makes the anvil free: no XP level requirement, no level cost, no "Too Expensive!" cap.
 * Materials (iron, books, ...) are still used up as usual.
 */
@Mixin(value = AnvilMenu.class, remap = false)
public abstract class AnvilMenuMixin {
    @Shadow
    @Final
    private DataSlot cost;

    /** The result can be taken whenever one exists (cost > 0 only means "there is a result"). */
    @Inject(method = "mayPickup", at = @At(value = "HEAD", remap = false), cancellable = true, remap = false)
    private void palestore$freePickup(Player player, boolean hasStack, CallbackInfoReturnable<Boolean> cir) {
        cir.setReturnValue(this.cost.get() > 0);
    }

    /** Taking the result no longer takes levels from the player. */
    @Redirect(method = "onTake", remap = false, at = @At(value = "INVOKE", remap = false,
            target = "Lnet/minecraft/world/entity/player/Player;giveExperienceLevels(I)V"))
    private void palestore$noLevelCost(Player player, int levels) {
        // intentionally empty
    }

    /** Remove the "Too Expensive!" limit (vanilla refuses results that cost 40 or more levels). */
    @ModifyConstant(method = "createResult", remap = false, constant = @Constant(intValue = 40))
    private int palestore$noCostCap(int original) {
        return Integer.MAX_VALUE;
    }

    /** Report cost 0 to the screen so the "Enchantment Cost: N" label is not shown at all. */
    @Inject(method = "getCost", at = @At(value = "HEAD", remap = false), cancellable = true, remap = false)
    private void palestore$hideCostLabel(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(0);
    }
}
