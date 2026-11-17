package com.example.thepale.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Whether a rider holds the jump key: on the Frost Colossus's shoulder that points the way (see FrostColossus). */
@Mixin(LivingEntity.class)
public interface LivingEntityAccessor {
    @Accessor("jumping")
    boolean thepale$isJumping();
}
