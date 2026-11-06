package com.example.thepale;

import com.example.thepale.config.CommonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RespawnAnchorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

/**
 * A charged respawn anchor in the Nether keeps the player's inventory and experience on death.
 *
 * <p>When a player dies while their spawn point is a charged respawn anchor in the Nether, the inventory and
 * experience are moved into the player's persistent data before anything drops (so a Corpse body stays empty),
 * and put back on the respawned player. Kept in persistent data rather than memory, so a server restart while the
 * player is dead loses nothing. Curios slots (Elytra Slot) are not part of the inventory and drop as usual.
 */
public final class AnchorKeepsInventory {
    private static final String KEY = "thepale_anchor_kept";

    public static void onLivingDeath(LivingDeathEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!CommonConfig.get().anchorKeepsInventory()) return;
        if (player.level().getGameRules().getBoolean(GameRules.RULE_KEEPINVENTORY)) return;
        if (!hasChargedNetherAnchor(player)) return;

        CompoundTag kept = new CompoundTag();
        kept.put("inventory", player.getInventory().save(new ListTag()));
        kept.putInt("level", player.experienceLevel);
        kept.putInt("total", player.totalExperience);
        kept.putFloat("progress", player.experienceProgress);
        player.getPersistentData().put(KEY, kept);

        player.getInventory().clearContent();
        player.experienceLevel = 0;
        player.totalExperience = 0;
        player.experienceProgress = 0.0F;
    }

    public static void onClone(PlayerEvent.Clone event) {
        if (!event.isWasDeath()) return;
        CompoundTag data = event.getOriginal().getPersistentData();
        if (!data.contains(KEY, Tag.TAG_COMPOUND)) return;
        CompoundTag kept = data.getCompound(KEY);
        data.remove(KEY);

        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        player.getInventory().load(kept.getList("inventory", Tag.TAG_COMPOUND));
        player.experienceLevel = kept.getInt("level");
        player.totalExperience = kept.getInt("total");
        player.experienceProgress = kept.getFloat("progress");
        player.displayClientMessage(Component.translatable("message.thepale.anchor_kept")
                .withStyle(ChatFormatting.LIGHT_PURPLE), true);
    }

    private static boolean hasChargedNetherAnchor(ServerPlayer player) {
        BlockPos pos = player.getRespawnPosition();
        if (pos == null || player.getRespawnDimension() != Level.NETHER) return false;
        ServerLevel nether = player.server.getLevel(Level.NETHER);
        if (nether == null) return false;
        BlockState state = nether.getBlockState(pos);
        return state.is(Blocks.RESPAWN_ANCHOR) && state.getValue(RespawnAnchorBlock.CHARGE) > 0;
    }

    private AnchorKeepsInventory() {}
}
