package com.example.thepale.survey;

import com.example.thepale.config.CommonConfig;
import com.example.thepale.worldgen.PaleBiomes;
import com.example.thepale.worldgen.PaleShape;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

/**
 * {@code /thepale survey}: what's on the island. Works it out from the world generator, so nothing has to be generated
 * or explored, and writes a biome map next to the world (thepale-survey.png).
 */
public final class IslandSurvey {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Blocks between sampled columns. */
    private static final int STEP = 16;

    private IslandSurvey() {}

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("thepale")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("survey").executes(context -> {
                    CommandSourceStack source = context.getSource();
                    ServerLevel overworld = source.getServer().overworld();
                    source.sendSuccess(() -> Component.literal("Surveying the island..."), false);
                    Util.backgroundExecutor().execute(() -> {
                        List<String> lines;
                        try {
                            lines = survey(overworld);
                        } catch (RuntimeException e) {
                            LOGGER.error("[thepale] Island survey failed", e);
                            lines = List.of("Survey failed: " + e);
                        }
                        List<String> report = lines;
                        source.getServer().execute(() -> report.forEach(line -> source.sendSuccess(() -> Component.literal(line), false)));
                    });
                    return 1;
                })));
    }

    /** One sampled column of the island. */
    private record Column(int x, int z, ResourceLocation biome) {}

    static List<String> survey(ServerLevel level) {
        long started = System.currentTimeMillis();
        double radius = CommonConfig.get().paleBiomeRadius();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        BiomeSource biomes = generator.getBiomeSource();
        int seaLevel = generator.getSeaLevel();
        PaleShape shape = PaleShape.server();
        // Every island, in one rectangle of columns STEP blocks apart.
        int[] bounds = {Integer.MAX_VALUE, Integer.MAX_VALUE, Integer.MIN_VALUE, Integer.MIN_VALUE};
        for (int[] box : IslandCheck.islandBoxes(radius)) {
            bounds[0] = Math.min(bounds[0], Math.floorDiv(box[0] * 16, STEP));
            bounds[1] = Math.min(bounds[1], Math.floorDiv(box[1] * 16, STEP));
            bounds[2] = Math.max(bounds[2], Math.floorDiv(box[2] * 16 + 15, STEP));
            bounds[3] = Math.max(bounds[3], Math.floorDiv(box[3] * 16 + 15, STEP));
        }

        List<Column> columns = IntStream.rangeClosed(bounds[0], bounds[2]).parallel().boxed().flatMap(i -> {
            List<Column> row = new ArrayList<>();
            for (int j = bounds[1]; j <= bounds[3]; j++) {
                int x = i * STEP;
                int z = j * STEP;
                if (shape.effectiveRadius(x, z) >= radius) {
                    continue;
                }
                int ground = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random);
                int y = Math.max(ground, seaLevel);
                Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z), random.sampler());
                row.add(new Column(x, z, biome.unwrapKey().map(key -> key.location()).orElse(ResourceLocation.withDefaultNamespace("unknown"))));
            }
            return row.stream();
        }).toList();

        Map<ResourceLocation, Integer> counts = new HashMap<>();
        columns.forEach(column -> counts.merge(column.biome(), 1, Integer::sum));
        List<Map.Entry<ResourceLocation, Integer>> sorted = new ArrayList<>(counts.entrySet());
        sorted.sort(Map.Entry.<ResourceLocation, Integer>comparingByValue(Comparator.reverseOrder()));

        Path folder = level.getServer().getWorldPath(LevelResource.ROOT);
        Path map = folder.resolve("thepale-survey.png");
        writeMap(map, columns, bounds);

        // Biomes the world could have but the island doesn't (cave biomes only show where they reach the surface).
        List<String> missingBiomes = biomes.possibleBiomes().stream()
                .map(biome -> biome.unwrapKey().map(key -> key.location()).orElse(null))
                .filter(id -> id != null && !counts.containsKey(id) && !id.equals(PaleBiomes.THE_PALE.location()))
                .map(ResourceLocation::toString)
                .sorted()
                .toList();

        IslandCheck.World world = new IslandCheck.World(
                level.registryAccess(), generator, random, level.getChunkSource().getGeneratorState(),
                level.getStructureManager(), level, level.getSeed());
        Map<ResourceLocation, List<BlockPos>> structures = IslandCheck.structures(world, radius);
        List<ResourceLocation> missingStructures = IslandCheck.missing(IslandCheck.possibleStructures(world), structures);

        List<String> report = new ArrayList<>();
        report.add(String.format("Island survey (radius %.0f, seed %d): %d biomes, %d kinds of structure", radius, level.getSeed(), counts.size(), structures.size()));
        report.add("Islands: " + shape.islands());
        if (shape.islands().size() > 1) {
            // Each island's own biomes, biggest first.
            for (PaleShape.Island island : shape.islands()) {
                Map<ResourceLocation, Integer> own = new HashMap<>();
                int total = 0;
                for (Column column : columns) {
                    if (shape.nearest(column.x(), column.z()) == island) {
                        own.merge(column.biome(), 1, Integer::sum);
                        total++;
                    }
                }
                List<Map.Entry<ResourceLocation, Integer>> ranked = new ArrayList<>(own.entrySet());
                ranked.sort(Map.Entry.<ResourceLocation, Integer>comparingByValue(Comparator.reverseOrder()));
                StringBuilder line = new StringBuilder("  " + island.kind().name().toLowerCase(java.util.Locale.ROOT) + " island: ");
                for (Map.Entry<ResourceLocation, Integer> entry : ranked) {
                    line.append(String.format("%s %.0f%%, ", entry.getKey().getPath(), 100.0 * entry.getValue() / Math.max(total, 1)));
                }
                report.add(line.substring(0, line.length() - 2));
            }
        }
        StringBuilder biomeLine = new StringBuilder("Biomes: ");
        for (Map.Entry<ResourceLocation, Integer> entry : sorted) {
            biomeLine.append(String.format("%s %.1f%%, ", entry.getKey(), 100.0 * entry.getValue() / columns.size()));
        }
        report.add(biomeLine.substring(0, biomeLine.length() - 2));
        report.add("Biomes not on the island: " + (missingBiomes.isEmpty() ? "none" : String.join(", ", missingBiomes)));
        report.add("Structures:");
        IslandCheck.describe(structures).forEach(line -> report.add("  " + line));
        report.add("Structures not on the island: " + (missingStructures.isEmpty() ? "none"
                : String.join(", ", missingStructures.stream().map(ResourceLocation::toString).toList())));
        Path text = folder.resolve("thepale-survey.txt");
        try {
            Files.write(text, report);
        } catch (IOException e) {
            LOGGER.warn("[thepale] Could not write the survey report to {}", text, e);
        }
        report.forEach(line -> LOGGER.info("[thepale] {}", line));

        List<String> lines = new ArrayList<>();
        lines.add(report.get(0));
        lines.add(String.format("%d biomes and %d kinds of structure the world has are not on the island.", missingBiomes.size(), missingStructures.size()));
        if (!missingStructures.isEmpty()) {
            lines.add("Missing structures: " + String.join(", ", missingStructures.stream().map(ResourceLocation::toString).toList()));
        }
        lines.add("Full report and biome map in " + folder.toAbsolutePath().normalize()
                + String.format(" (thepale-survey.txt, .png; %.1f s)", (System.currentTimeMillis() - started) / 1000.0));
        return lines;
    }

    /** One pixel per sampled column, north up; land and water biomes in their usual colours. */
    private static void writeMap(Path path, List<Column> columns, int[] bounds) {
        BufferedImage image = new BufferedImage(bounds[2] - bounds[0] + 1, bounds[3] - bounds[1] + 1, BufferedImage.TYPE_INT_RGB);
        for (Column column : columns) {
            image.setRGB(column.x() / STEP - bounds[0], column.z() / STEP - bounds[1], colour(column.biome()));
        }
        try {
            ImageIO.write(image, "png", path.toFile());
        } catch (IOException e) {
            LOGGER.warn("[thepale] Could not write the survey map to {}", path, e);
        }
    }

    private static int colour(ResourceLocation biome) {
        String name = biome.getPath();
        if (name.contains("frozen_ocean") || name.contains("frozen_river")) return 0x8FB8E8;
        if (name.contains("ocean") || name.contains("sea")) return name.contains("warm") || name.contains("lukewarm") ? 0x2E7FC0 : name.contains("cold") ? 0x2A4F9E : 0x2B5FB0;
        if (name.contains("river")) return 0x4A8FE0;
        if (name.contains("beach") || name.contains("shore")) return 0xE8DCA0;
        if (name.contains("ice") || name.contains("frozen") || name.contains("snow") || name.contains("glaci") || name.contains("alpine") || name.contains("wintry") || name.contains("winter")) return 0xF2F6FA;
        if (name.contains("badlands") || name.contains("mesa") || name.contains("canyon")) return 0xC8642A;
        if (name.contains("desert") || name.contains("dune") || name.contains("sand")) return 0xE8D070;
        if (name.contains("savanna") || name.contains("shrubland") || name.contains("steppe")) return 0xB8B050;
        if (name.contains("jungle") || name.contains("bamboo") || name.contains("tropical") || name.contains("rainforest")) return 0x1E9E28;
        if (name.contains("mangrove") || name.contains("swamp") || name.contains("bayou") || name.contains("marsh")) return 0x4E6A3A;
        if (name.contains("dark_forest") || name.contains("moonlight") || name.contains("shield")) return 0x22421E;
        if (name.contains("taiga") || name.contains("spruce") || name.contains("grove")) return 0x3E7A68;
        if (name.contains("cherry") || name.contains("sakura")) return 0xF0A8C8;
        if (name.contains("mushroom")) return 0x9A6EA0;
        if (name.contains("peak") || name.contains("slope") || name.contains("stony") || name.contains("mountain") || name.contains("cliff") || name.contains("highland")) return 0x8C8C8C;
        if (name.contains("forest") || name.contains("birch") || name.contains("wood")) return 0x3E8A34;
        if (name.contains("plains") || name.contains("meadow") || name.contains("field") || name.contains("prairie")) return 0x8DB360;
        if (name.contains("pale")) return 0xBEBEC3;
        return 0x606060 | (name.hashCode() & 0x9F9F9F);
    }
}
