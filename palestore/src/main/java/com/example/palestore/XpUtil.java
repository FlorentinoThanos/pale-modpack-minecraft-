package com.example.palestore;

import net.minecraft.world.entity.player.Player;

public final class XpUtil {
    /** Total XP points needed to reach the given level (vanilla formula). */
    public static int pointsForLevel(int level) {
        if (level <= 16) return level * level + 6 * level;
        if (level <= 31) return (int) (2.5 * level * level - 40.5 * level + 360);
        return (int) (4.5 * level * level - 162.5 * level + 2220);
    }

    /** Total XP points the player currently has (levels + progress to the next level). */
    public static int total(Player player) {
        return pointsForLevel(player.experienceLevel)
                + Math.round(player.experienceProgress * player.getXpNeededForNextLevel());
    }

    private XpUtil() {}
}
