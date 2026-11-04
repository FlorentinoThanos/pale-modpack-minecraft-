package com.example.palestore;

import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.EnchantingTableBlock;

/** Shared rules for the three enchanting-table choices. */
public final class EnchantingRules {
    private static final int[] XP_COSTS = {125, 250, 500};
    private static final int[] BOOKSHELF_REQUIREMENTS = {0, 8, 15};

    public static int xpCost(int option) {
        return XP_COSTS[option];
    }

    public static int bookshelfRequirement(int option) {
        return BOOKSHELF_REQUIREMENTS[option];
    }

    public static int countValidBookshelves(ContainerLevelAccess access) {
        AtomicInteger count = new AtomicInteger();
        access.execute((level, tablePos) -> count.set(countValidBookshelves(level, tablePos)));
        return count.get();
    }

    public static int countValidBookshelves(Level level, BlockPos tablePos) {
        int count = 0;
        for (BlockPos offset : EnchantingTableBlock.BOOKSHELF_OFFSETS) {
            if (!EnchantingTableBlock.isValidBookShelf(level, tablePos, offset)) continue;
            BlockPos shelfPos = tablePos.offset(offset);
            if (level.getBlockState(shelfPos).getEnchantPowerBonus(level, shelfPos) > 0.0F) count++;
        }
        return count;
    }

    private EnchantingRules() {}
}
