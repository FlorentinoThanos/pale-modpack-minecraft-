package com.example.thepale;

import com.example.thepale.config.CommonConfig;
import com.example.thepale.survey.IslandSurvey;
import com.example.thepale.worldgen.SeedSearch;
import com.example.thepale.worldgen.ShapePayload;
import com.example.thepale.worldgen.PaleBiomes;
import com.example.thepale.worldgen.PaleWorldgen;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/**
 * The Pale, common side: world shape, the Pale biome and the poison. Runs on both the dedicated server and the
 * client (whose integrated server generates the world). Visuals live in {@link ThePaleClient}.
 */
@Mod(ThePale.MOD_ID)
public final class ThePale {
    public static final String MOD_ID = "thepale";

    public ThePale(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.COMMON, CommonConfig.SPEC);
        modBus.addListener(CommonConfig::onLoad);
        modBus.addListener(CommonConfig::onReload);
        PaleWorldgen.register(modBus);

        NeoForge.EVENT_BUS.addListener(PaleBiomes::onServerAboutToStart);
        NeoForge.EVENT_BUS.addListener(PalePoison::onPlayerTick);
        NeoForge.EVENT_BUS.addListener(IslandMobs::onPotentialSpawns);
        NeoForge.EVENT_BUS.addListener(IslandMobs::onFinalizeSpawn);
        NeoForge.EVENT_BUS.addListener(IslandMobs::onEntityJoinLevel);
        NeoForge.EVENT_BUS.addListener(IslandMobs::onServerStarted);
        NeoForge.EVENT_BUS.addListener(IslandMobs::onRightClickBlock);
        NeoForge.EVENT_BUS.addListener(IslandSurvey::onRegisterCommands);
        NeoForge.EVENT_BUS.addListener(SeedSearch::onPlayerLoggedIn);
        NeoForge.EVENT_BUS.addListener(ShapePayload::onPlayerLoggedIn);
        modBus.addListener(ShapePayload::onRegisterPayloads);
    }

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }
}
