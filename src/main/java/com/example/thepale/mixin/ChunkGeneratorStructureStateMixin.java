package com.example.thepale.mixin;

import com.example.thepale.worldgen.PaleBiomeSource;
import com.example.thepale.worldgen.PaleShape;
import com.example.thepale.worldgen.PaleStructures;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BuiltinStructures;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.level.ChunkPos;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * For the Overworld (the biome source the Pale is switched on for), hands world generation copies of the structure
 * sets with rare structures spread out more densely and the world's pinned structures in place
 * ({@link com.example.thepale.worldgen.PaleStructures}). Everything that asks where structures go (generation, /locate,
 * maps, eyes of ender) goes through this state.
 */
@Mixin(ChunkGeneratorStructureState.class)
public abstract class ChunkGeneratorStructureStateMixin {
    @Shadow
    @Final
    @Mutable
    private List<Holder<StructureSet>> possibleStructureSets;

    /** The island layout this state was made for: set for the Overworld only. */
    @Unique
    @Nullable
    private PaleShape thepale$layout;

    @Unique
    @Nullable
    private List<ChunkPos> thepale$strongholds;

    @Inject(method = "createForNormal", at = @At("RETURN"))
    private static void thepale$spreadOutRareStructures(
            RandomState random, long seed, BiomeSource biomes, HolderLookup<StructureSet> sets,
            CallbackInfoReturnable<ChunkGeneratorStructureState> cir) {
        if (biomes instanceof PaleBiomeSource pale && pale.thepale$paleBiome() != null) {
            ChunkGeneratorStructureStateMixin state = (ChunkGeneratorStructureStateMixin) (Object) cir.getReturnValue();
            state.possibleStructureSets = PaleStructures.adjust(state.possibleStructureSets);
            state.thepale$layout = PaleShape.server();
        }
    }

    /** With three islands the strongholds (and the way to the End) are on the hot island. Eyes of ender find them anywhere. */
    @Inject(method = "getRingPositionsFor", at = @At("HEAD"), cancellable = true)
    private void thepale$strongholdsOnTheHotIsland(ConcentricRingsStructurePlacement placement, CallbackInfoReturnable<List<ChunkPos>> cir) {
        PaleShape layout = thepale$layout;
        if (layout == null || layout.island(PaleShape.Kind.HOT) == null || !thepale$placesStrongholds(placement)) {
            return;
        }
        if (thepale$strongholds == null) {
            thepale$strongholds = PaleStructures.strongholds(layout.island(PaleShape.Kind.HOT));
        }
        cir.setReturnValue(thepale$strongholds);
    }

    @Unique
    private boolean thepale$placesStrongholds(ConcentricRingsStructurePlacement placement) {
        for (Holder<StructureSet> set : possibleStructureSets) {
            if (set.value().placement() == placement) {
                return set.value().structures().stream().anyMatch(entry -> entry.structure().is(BuiltinStructures.STRONGHOLD));
            }
        }
        return false;
    }
}
