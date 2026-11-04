package com.example.thepale;

import com.example.thepale.config.CommonConfig;
import com.mojang.logging.LogUtils;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * Changes other mods' effects. For now: the movement speed bonus of Born in Chaos' five Rampage effects
 * ({@code rampageSpeedBonus} in the common config). Rewrites the effect's own attribute template, so it applies to
 * every source of Rampage (Great Reaper Axe, Dark Metal armour, the potion). An effect already running keeps the
 * old bonus until it ends. Does nothing when Born in Chaos is not installed.
 */
public final class EffectTweaks {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BIC = "born_in_chaos_v1";
    /** Rampage effects from Light to Rampant, in the order of the config list. */
    private static final List<String> RAMPAGE = List.of(
            "light_rampage", "medium_rampage", "strong_rampage", "furious_rampage", "rampant_rampage");

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
}
