package com.example.thepale;

import com.example.thepale.config.CommonConfig;
import com.example.thepale.worldgen.PaleAltitude;
import com.example.thepale.worldgen.PaleShape;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * The poison of the Pale, server side. Once a second, past poisonStartRadius in the Overworld (survival and adventure
 * only): Hunger III, Wither II (III from 35% of the way to poisonFullRadius, V from 70%) and direct wither damage
 * from 1 to 6.
 */
public final class PalePoison {
    private static final int INTERVAL_TICKS = 20;
    private static final int EFFECT_TICKS = 60;
    private static final int HUNGER_III = 2;

    private PalePoison() {}

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        Level level = player.level();
        if (level.isClientSide()
                || player.tickCount % INTERVAL_TICKS != 0
                || player.isCreative()
                || player.isSpectator()
                || level.dimension() != Level.OVERWORLD) {
            return;
        }
        CommonConfig.Settings config = CommonConfig.get();
        double x = player.getX();
        double z = player.getZ();
        double depth = (PaleShape.server().effectiveRadius(x, z) - config.poisonStart()) / (config.poisonFull() - config.poisonStart());
        // High above the Pale (elytra) the air is clean.
        depth = Math.min(depth, 1.0) * PaleAltitude.strength(player.getY());
        if (depth <= 0) {
            return;
        }
        int witherAmplifier = depth >= 0.7 ? 4 : depth >= 0.35 ? 2 : 1; // V, III, II
        player.addEffect(new MobEffectInstance(MobEffects.WITHER, EFFECT_TICKS, witherAmplifier, false, true, true));
        player.addEffect(new MobEffectInstance(MobEffects.HUNGER, EFFECT_TICKS, HUNGER_III, false, true, true));
        player.hurt(player.damageSources().wither(), (float) (1 + Math.min(depth, 1) * 5));
    }
}
