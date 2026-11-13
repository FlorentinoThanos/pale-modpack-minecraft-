package com.example.thepale.finale;

import com.example.thepale.ThePale;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/**
 * The finale's advancements (data/thepale/advancement/finale/). They have no condition of their own: the mod grants
 * them, and the quest book checks for them, so a quest counts for everybody who was there and not only for whoever
 * landed the last blow.
 */
public final class FinaleAdvancements {
    /** The invasion has started. */
    public static final ResourceLocation ROOT = ThePale.id("finale/root");
    /** The Patchwork Abomination died with this player in the arena. */
    public static final ResourceLocation SLAIN = ThePale.id("finale/abomination_slain");
    /** The Pale has been cleansed. */
    public static final ResourceLocation CLEANSED = ThePale.id("finale/cleansed");

    private FinaleAdvancements() {}

    public static void grant(ServerPlayer player, ResourceLocation id) {
        AdvancementHolder advancement = player.server.getAdvancements().get(id);
        if (advancement == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(advancement, criterion);
        }
    }
}
