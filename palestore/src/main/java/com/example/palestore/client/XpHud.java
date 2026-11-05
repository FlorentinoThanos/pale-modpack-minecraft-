package com.example.palestore.client;

import com.example.palestore.XpUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.RenderGuiLayerEvent;
import net.neoforged.neoforge.client.gui.VanillaGuiLayers;

/**
 * Replaces the vanilla XP display with the player's TOTAL XP points:
 *  - the level number is never drawn (level 7 shows 100, not 7)
 *  - the green XP bar is hidden too when HIDE_XP_BAR is true
 */
public final class XpHud {
    /** Set to false to keep the vanilla green bar and only replace the number. */
    private static final boolean HIDE_XP_BAR = true;

    private static final int VANILLA_GREEN = 0x80FF20;

    public static void onRenderLayer(RenderGuiLayerEvent.Pre event) {
        if (HIDE_XP_BAR && event.getName().equals(VanillaGuiLayers.EXPERIENCE_BAR)) {
            event.setCanceled(true);
            return;
        }
        if (!event.getName().equals(VanillaGuiLayers.EXPERIENCE_LEVEL)) return;

        // Never let vanilla draw its level number.
        event.setCanceled(true);

        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || !mc.gameMode.hasExperience()) return;

        int total = XpUtil.total(player);
        if (total <= 0) return;

        GuiGraphics graphics = event.getGuiGraphics();
        Font font = mc.font;
        String text = Integer.toString(total);

        // Vanilla spot is 35px above the bottom (above the bar). With the bar gone, sit a bit lower.
        int x = (graphics.guiWidth() - font.width(text)) / 2;
        int y = graphics.guiHeight() - (HIDE_XP_BAR ? 32 : 35);
        graphics.drawString(font, text, x + 1, y, 0, false);
        graphics.drawString(font, text, x - 1, y, 0, false);
        graphics.drawString(font, text, x, y + 1, 0, false);
        graphics.drawString(font, text, x, y - 1, 0, false);
        graphics.drawString(font, text, x, y, VANILLA_GREEN, false);
    }

    private XpHud() {}
}
