package com.example.thepale.mixin;

import com.example.thepale.worldgen.IslandStructures;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Frozen-island-only structures (Born in Chaos's dark towers by default) find no place to start anywhere else. Hooked
 * where both generation and the quick presence check (/locate, eyes of ender, maps) ask, so they all agree.
 */
@Mixin(Structure.class)
public abstract class StructureMixin {
    @Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void thepale$onlyOnItsIsland(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        if (!IslandStructures.allowed((Structure) (Object) this, context.registryAccess(), context.biomeSource(), context.chunkPos())) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
