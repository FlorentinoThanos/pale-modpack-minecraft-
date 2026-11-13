package com.example.thepale.finale;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;

/**
 * The Heart of the Pale, dropped by the boss. Held and used (three seconds, in the Overworld) it cleanses the Pale.
 * The cleansing is the player's to start, and not the boss's death, because it changes the world for good: the sea
 * opens and new lands generate beyond it. So everybody can be there for it, and a boss summoned by hand to try the
 * fight doesn't change the world by dying.
 */
public final class HeartOfThePaleItem extends Item {
    private static final int USE_TICKS = 60;

    public HeartOfThePaleItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return USE_TICKS;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        return UseAnim.BOW;
    }

    @Override
    public ItemStack finishUsingItem(ItemStack stack, Level level, LivingEntity entity) {
        if (!level.isClientSide && entity instanceof Player player && Cleansing.useHeart(player)) {
            stack.consume(1, player);
        }
        return stack;
    }

    /** Lying on the ground it never despawns, so the way to end the Pale can't be lost by leaving it there. */
    @Override
    public int getEntityLifespan(ItemStack stack, Level level) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean onEntityItemUpdate(ItemStack stack, ItemEntity entity) {
        if (!entity.isInvulnerable()) {
            entity.setInvulnerable(true);
        }
        return false;
    }

    @Override
    public boolean isFoil(ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("item.thepale.heart_of_the_pale.tooltip").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("item.thepale.heart_of_the_pale.warning").withStyle(ChatFormatting.DARK_PURPLE));
    }
}
