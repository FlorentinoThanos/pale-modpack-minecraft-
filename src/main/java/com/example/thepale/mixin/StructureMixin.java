package com.example.thepale.mixin;

import com.example.thepale.worldgen.IslandStructures;
import com.example.thepale.worldgen.VillageClearance;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Frozen-island-only structures (Born in Chaos's dark towers by default) find no place to start anywhere else, and
 * pillager outposts none near a village ({@link VillageClearance}). Hooked
 * where both generation and the quick presence check (/locate, eyes of ender, maps) ask, so they all agree.
 */
@Mixin(Structure.class)
public abstract class StructureMixin {
    @Inject(method = "findValidGenerationPoint", at = @At("HEAD"), cancellable = true)
    private void thepale$onlyOnItsIsland(Structure.GenerationContext context, CallbackInfoReturnable<Optional<Structure.GenerationStub>> cir) {
        Structure structure = (Structure) (Object) this;
        if (!IslandStructures.allowed(structure, context.registryAccess(), context.biomeSource(), context.chunkPos())
                || !VillageClearance.allowed(structure, context.registryAccess(), context.randomState(), context.seed(), context.chunkPos())) {
            cir.setReturnValue(Optional.empty());
        }
    }
}
