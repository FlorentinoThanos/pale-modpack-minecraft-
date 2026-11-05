package com.example.palestore.mixin;

import com.example.palestore.AbstractContainerScreenAccess;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = AbstractContainerScreen.class, remap = false)
public interface AbstractContainerScreenAccessorMixin extends AbstractContainerScreenAccess {
    @Override
    @Accessor(value = "menu", remap = false)
    AbstractContainerMenu palestore$getMenu();
}
