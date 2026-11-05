package com.example.thepale;

import com.example.thepale.config.CommonConfig;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import org.slf4j.Logger;

/**
 * Changes other mods' effects: the movement speed bonus of Born in Chaos' five Rampage effects
 * ({@code rampageSpeedBonus} in the common config), and Strength for the full Dark Metal armour set
 * ({@code darkMetalSetStrength}). Rewrites the effect's own attribute template, so it applies to
 * every source of Rampage (Great Reaper Axe, Dark Metal armour, the potion). An effect already running keeps the
 * old bonus until it ends. Does nothing when Born in Chaos is not installed.
 */
public final class EffectTweaks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BIC = "born_in_chaos_v1";
    /** Rampage effects from Light to Rampant, in the order of the config list. */
    private static final List<String> RAMPAGE = List.of(
            "light_rampage", "medium_rampage", "strong_rampage", "furious_rampage", "rampant_rampage");

    /** Dark Metal armour pieces, one per armour slot. */
    private static final List<EquipmentSlot> ARMOR_SLOTS =
            List.of(EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final List<String> DARK_METAL = List.of(
            "dark_metal_armor_helmet", "dark_metal_armor_chestplate", "dark_metal_armor_leggings", "dark_metal_armor_boots");
    private static final int SET_INTERVAL_TICKS = 20;
    private static final int SET_EFFECT_TICKS = 60;

    private static volatile boolean ready;

    private EffectTweaks() {}

    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            ready = true;
            apply();
        });
    }

    /** Re-applies the settings after a config reload; ignored before the registries are ready. */
    public static void onConfigChanged() {
        if (ready) {
            apply();
        }
    }

    private static synchronized void apply() {
        List<Double> bonus = CommonConfig.get().rampageSpeedBonus();
        int changed = 0;
        for (int i = 0; i < RAMPAGE.size() && i < bonus.size(); i++) {
            ResourceLocation key = ResourceLocation.fromNamespaceAndPath(BIC, RAMPAGE.get(i));
            var effect = BuiltInRegistries.MOB_EFFECT.get(key);
            if (effect == null) {
                continue;
            }
            // Same modifier id and operation as Born in Chaos, so this replaces its template instead of adding one.
            effect.addAttributeModifier(
                    Attributes.MOVEMENT_SPEED,
                    ResourceLocation.fromNamespaceAndPath(BIC, "effect." + RAMPAGE.get(i) + "_0"),
                    bonus.get(i) / 100.0,
                    AttributeModifier.Operation.ADD_MULTIPLIED_BASE);
            changed++;
        }
        if (changed > 0) {
            LOGGER.info("[thepale] Rampage movement speed set to {}% (Light to Rampant)", bonus);
        }
    }

    /** Once a second, server side: Strength while all four pieces of Dark Metal armour are worn. */
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Player player = event.getEntity();
        if (player.level().isClientSide() || player.tickCount % SET_INTERVAL_TICKS != 0) {
            return;
        }
        int level = CommonConfig.get().darkMetalSetStrength();
        if (level <= 0 || !wearsDarkMetalSet(player)) {
            return;
        }
        MobEffectInstance current = player.getEffect(MobEffects.DAMAGE_BOOST);
        // Leave a stronger or longer Strength (a potion, the Dark Ritual Dagger) alone.
        if (current != null && (current.getAmplifier() > level - 1
                || current.getAmplifier() == level - 1 && current.getDuration() > SET_EFFECT_TICKS)) {
            return;
        }
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_BOOST, SET_EFFECT_TICKS, level - 1, true, false, true));
    }

    private static boolean wearsDarkMetalSet(Player player) {
        for (int i = 0; i < ARMOR_SLOTS.size(); i++) {
            var key = BuiltInRegistries.ITEM.getKey(player.getItemBySlot(ARMOR_SLOTS.get(i)).getItem());
            if (!BIC.equals(key.getNamespace()) || !DARK_METAL.get(i).equals(key.getPath())) {
                return false;
            }
        }
        return true;
    }
}
