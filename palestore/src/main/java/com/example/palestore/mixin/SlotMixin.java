package com.example.palestore.mixin;

import com.example.palestore.ShopOffers;
import com.example.palestore.Unlocks;
import com.example.palestore.client.ClientUnlocks;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Suppresses locked crafting results at the slot read, before rendering or click handling. */
@Mixin(value = Slot.class, remap = false)
public abstract class SlotMixin {
    @Shadow @Final public Container container;
    @Shadow public int index;

    @Inject(method = "getItem", at = @At("HEAD"), cancellable = true, remap = false)
    private void palestore$hideLockedCraftResult(CallbackInfoReturnable<ItemStack> cir) {
        if (!((Object) this instanceof ResultSlot resultSlot)) return;

        ItemStack actual = this.container.getItem(this.index);
        ShopOffers.Offer offer = ShopOffers.byResult(actual).orElse(null);
        if (offer == null) return;

        Player owner = ((ResultSlotAccessor) (Object) resultSlot).palestore$getPlayer();
        boolean unlocked = owner instanceof ServerPlayer serverPlayer
                ? Unlocks.has(serverPlayer, offer.id())
                : ClientUnlocks.has(offer.id());
        if (!unlocked) cir.setReturnValue(ItemStack.EMPTY);
    }
}
