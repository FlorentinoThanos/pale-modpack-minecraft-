package com.example.thepale.mixinsupport;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Makes mixins visible in the log: one line when thepale.mixins.json is picked up and one per mixin applied. If the
 * "config loaded" line is missing, the config isn't registered ([[mixins]] in neoforge.mods.toml); if a mixin's line
 * is missing, it never reached its target. Lives outside the mixin package, which Mixin forbids referencing.
 */
public final class ThePaleMixinPlugin implements IMixinConfigPlugin {
    private static final Logger LOGGER = LoggerFactory.getLogger("thepale");

    @Override
    public void onLoad(String mixinPackage) {
        LOGGER.info("[thepale] Mixin config loaded ({})", mixinPackage);
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {}

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {}

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        LOGGER.info("[thepale] Applied mixin {} to {}", mixinInfo.getName(), targetClassName);
    }
}
