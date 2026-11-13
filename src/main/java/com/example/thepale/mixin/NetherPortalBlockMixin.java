package com.example.thepale.mixin;

import com.example.thepale.config.CommonConfig;
import com.example.thepale.worldgen.PaleCleanse;
import com.example.thepale.worldgen.PaleShape;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.NetherPortalBlock;
import net.minecraft.world.level.border.WorldBorder;
import net.minecraft.world.level.portal.DimensionTransition;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The frozen island can't be reached through the Nether: a portal from the Nether won't open a new portal there (or in
 * the Pale around it). A portal somebody built there after flying in still works, both ways.
 */
@Mixin(NetherPortalBlock.class)
public abstract class NetherPortalBlockMixin {
    @Inject(
            method = "getExitPortal",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/portal/PortalForcer;createPortal(Lnet/minecraft/core/BlockPos;Lnet/minecraft/core/Direction$Axis;)Ljava/util/Optional;"),
            cancellable = true)
    private void thepale$noNewPortalOnTheFrozenIsland(
            ServerLevel destination, Entity entity, BlockPos from, BlockPos target, boolean toNether, WorldBorder border,
            CallbackInfoReturnable<DimensionTransition> cir) {
        if (destination.dimension() != Level.OVERWORLD) {
            return;
        }
        if (PaleCleanse.cleansed() && CommonConfig.get().finale().openColdIslandPortals()) {
            return; // the finale opens the frozen island to the Nether
        }
        PaleShape shape = PaleShape.server();
        if (shape.islands().size() > 1 && shape.nearest(target.getX(), target.getZ()).kind() == PaleShape.Kind.COLD) {
            if (entity instanceof Player player) {
                player.displayClientMessage(
                        Component.translatable("message.thepale.portal_refused").withStyle(ChatFormatting.GRAY), true);
            }
            cir.setReturnValue(null);
        }
    }
}
