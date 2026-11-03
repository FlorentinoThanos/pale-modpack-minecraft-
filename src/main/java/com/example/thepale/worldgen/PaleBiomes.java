package com.example.thepale.worldgen;

import com.example.thepale.ThePale;
import com.example.thepale.config.CommonConfig;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.dimension.LevelStem;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import org.slf4j.Logger;

/** The Pale biome (data/thepale/worldgen/biome/the_pale.json) and where it takes over the Overworld. */
public final class PaleBiomes {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceKey<Biome> THE_PALE = ResourceKey.create(Registries.BIOME, ThePale.id("the_pale"));

    private PaleBiomes() {}

    public static boolean isInPale(int blockX, int blockZ) {
        return PaleShape.server().effectiveRadius(blockX, blockZ) >= CommonConfig.get().paleBiomeRadius();
    }

    /**
     * Switches the Pale on for the Overworld's biome source only (the Nether's is a MultiNoiseBiomeSource too). Runs
     * before the levels are created, so structure placement and feature ordering already see the biome.
     */
    public static void onServerAboutToStart(ServerAboutToStartEvent event) {
        MinecraftServer server = event.getServer();
        LevelStem overworld = server.registryAccess().registryOrThrow(Registries.LEVEL_STEM).get(LevelStem.OVERWORLD);
        if (overworld == null) {
            LOGGER.warn("[thepale] No Overworld in this world; the Pale biome is off");
            return;
        }
        BiomeSource source = overworld.generator().getBiomeSource();
        if (source instanceof PaleBiomeSource paleSource) {
            Holder<Biome> pale = server.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(THE_PALE);
            paleSource.thepale$setPaleBiome(pale);
            LOGGER.info("[thepale] The Pale biome takes over the Overworld from {} blocks", CommonConfig.get().paleBiomeRadius());
            // Biome modifiers have run by now. Mods that add spawns to every biome (neoforge:any) are undone by
            // data/thepale/neoforge/biome_modifier/the_pale_no_spawns.json; say so if something got through anyway.
            MobSpawnSettings mobs = pale.value().getMobSettings();
            int spawns = 0;
            for (MobCategory category : MobCategory.values()) {
                spawns += mobs.getMobs(category).unwrap().size();
            }
            if (spawns > 0) {
                LOGGER.warn("[thepale] The Pale biome has {} mob spawn entries after biome modifiers; it should have none", spawns);
            } else {
                LOGGER.info("[thepale] The Pale biome has no mob spawns");
            }
            PaleShape.onServerAboutToStart(server);
            IslandFeatures.onServerAboutToStart(server);
            IslandStructures.onServerAboutToStart(server);
            SeedSearch.onServerAboutToStart(server, overworld);
        } else if (source instanceof MultiNoiseBiomeSource) {
            LOGGER.error("[thepale] MultiNoiseBiomeSourceMixin was not applied, so the Pale biome will not generate");
        } else {
            LOGGER.warn("[thepale] The Overworld uses {}, not a multi-noise biome source; the Pale biome is off", source.getClass().getName());
        }
    }
}
