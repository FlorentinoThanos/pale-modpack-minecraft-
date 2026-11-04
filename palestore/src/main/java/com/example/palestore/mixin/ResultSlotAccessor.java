package com.example.palestore.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ResultSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ResultSlot.class, remap = false)
public interface ResultSlotAccessor {
    @Accessor(value = "player", remap = false)
    Player palestore$getPlayer();
}
