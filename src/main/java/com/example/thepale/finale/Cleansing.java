package com.example.thepale.finale;

import com.example.thepale.config.CommonConfig;
import com.example.thepale.finale.boss.PatchworkAbomination;
import com.example.thepale.worldgen.PaleBiomes;
import com.example.thepale.worldgen.PaleCleanse;
import com.example.thepale.worldgen.PaleShape;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeResolver;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * The cleansing of the Pale: what using the Heart of the Pale does.
 *
 * <ul>
 *   <li>The poison stops (PalePoison) and clients fade the fog wall, the fog, the grey water and the sea of clouds
 *       ({@link FinaleView#paleStrength}).
 *   <li>Chunks that exist keep their terrain; their Pale biome is turned into the ocean the seed has there, as they
 *       load ({@link #onChunkLoad}) and a few loaded chunks a tick, and sent to the players again. Nothing marks a
 *       chunk as done: a chunk still holding the Pale biome is simply one that hasn't been turned yet.
 *   <li>New chunks: out to a margin past the farthest chunk that existed, open sea as before; beyond it the seed's own
 *       terrain comes back ({@link PaleCleanse}). World generation reads that state live, so no restart is needed.
 * </ul>
 */
public final class Cleansing {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SEA_LEVEL_QUART = QuartPos.fromBlock(63);

    /** Loaded chunks waiting to have their biome turned; server thread only. */
    private static final LongArrayFIFOQueue QUEUE = new LongArrayFIFOQueue();
    private static final LongSet QUEUED = new LongOpenHashSet();

    private Cleansing() {}

    /** {@return whether the heart was used up} */
    static boolean useHeart(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.level().dimension() != Level.OVERWORLD) {
            player.displayClientMessage(Component.translatable("message.thepale.finale.heart_silent"), true);
            return false;
        }
        MinecraftServer server = serverPlayer.server;
        if (FinaleState.get(server).cleansed) {
            player.displayClientMessage(Component.translatable("message.thepale.finale.heart_spent"), true);
            return false;
        }
        cleanse(server);
        return true;
    }

    /** Cleanses the Pale, for good. {@return what happened, for the command} */
    static Component cleanse(MinecraftServer server) {
        FinaleState state = FinaleState.get(server);
        if (state.cleansed) {
            return Component.translatable("commands.thepale.cleanse.already");
        }
        ServerLevel level = server.overworld();
        CommonConfig.Finale config = CommonConfig.get().finale();

        // An invasion still under way ends here (the heart was given out by hand, or the command was used).
        if (state.boss != null && level.getEntity(state.boss) instanceof PatchworkAbomination boss) {
            boss.discard();
        }
        state.boss = null;
        if (state.phase != FinaleState.Phase.NONE) {
            state.phase = FinaleState.Phase.DEFEATED;
        }

        // Everything generated so far has to be on disk to be counted.
        level.save(null, true, false);
        PaleShape shape = PaleShape.server();
        List<ChunkPos> existing = existingChunks(server);
        double explored = shape.landEnd();
        for (ChunkPos chunk : existing) {
            explored = Math.max(explored, shape.effectiveRadius(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ()) + 12);
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.level() == level) {
                int reach = (server.getPlayerList().getViewDistance() + 3) * 16;
                explored = Math.max(explored, shape.effectiveRadius(player.getX(), player.getZ()) + reach);
            }
        }
        double seaEnd = explored + config.preserveMargin();
        if (config.maxSeaDistance() > 0) {
            seaEnd = Math.min(seaEnd, shape.landEnd() + config.maxSeaDistance());
        }
        state.seaEnd = seaEnd;
        state.landFull = seaEnd + config.transitionWidth();
        state.cleansed = true;
        state.cleansedAt = level.getGameTime();
        state.setDirty();
        PaleCleanse.set(true, state.seaEnd, state.landFull);
        LOGGER.info("[thepale] The Pale is cleansed. {} chunks exist, out to {} blocks past the coasts; open sea to {}, the seed's own lands from {}",
                existing.size(), Math.round(explored - shape.landEnd()), Math.round(state.seaEnd - shape.landEnd()),
                Math.round(state.landFull - shape.landEnd()));

        for (ChunkPos chunk : existing) {
            if (level.getChunkSource().getChunkNow(chunk.x, chunk.z) != null) {
                enqueue(chunk.toLong());
            }
        }

        FinalePayload.sendToAll(server);
        Component message = Component.translatable("message.thepale.finale.cleansed").withStyle(ChatFormatting.AQUA);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(message);
            player.playNotifySound(FinaleSounds.CLEANSE.get(), SoundSource.AMBIENT, 1f, 1f);
            FinaleAdvancements.grant(player, FinaleAdvancements.ROOT);
            FinaleAdvancements.grant(player, FinaleAdvancements.CLEANSED);
        }
        return Component.translatable("commands.thepale.cleanse.done",
                Math.round(state.seaEnd - shape.landEnd()), Math.round(state.landFull - shape.landEnd()));
    }

    /**
     * For testing only: the fog, the poison and the Pale biome in new chunks come back. Chunks whose biome was already
     * turned into ocean stay ocean, and terrain generated as new lands stays.
     */
    static Component undo(MinecraftServer server) {
        FinaleState state = FinaleState.get(server);
        if (!state.cleansed) {
            return Component.translatable("commands.thepale.cleanse.not_cleansed");
        }
        state.cleansed = false;
        state.setDirty();
        PaleCleanse.set(false, 0, 0);
        QUEUE.clear();
        QUEUED.clear();
        FinalePayload.sendToAll(server);
        return Component.translatable("commands.thepale.cleanse.undone");
    }

    static Component status(MinecraftServer server) {
        FinaleState state = FinaleState.get(server);
        if (!state.cleansed) {
            return Component.translatable("commands.thepale.cleanse.not_cleansed");
        }
        double landEnd = PaleShape.server().landEnd();
        return Component.literal(String.format(Locale.ROOT,
                "Cleansed %d ticks ago. Open sea to %d blocks past the coasts, the seed's own lands from %d. Loaded chunks waiting for their biome: %d",
                server.overworld().getGameTime() - state.cleansedAt, Math.round(state.seaEnd - landEnd), Math.round(state.landFull - landEnd), QUEUE.size()));
    }

    // ----- Chunks that already exist -----

    /** Every chunk in the Overworld's region files, generated fully or partly (read from the files' headers). */
    private static List<ChunkPos> existingChunks(MinecraftServer server) {
        List<ChunkPos> chunks = new ArrayList<>();
        Path regions = DimensionType.getStorageFolder(Level.OVERWORLD, server.getWorldPath(LevelResource.ROOT)).resolve("region");
        if (!Files.isDirectory(regions)) {
            return chunks;
        }
        ByteBuffer header = ByteBuffer.allocate(4096);
        try (DirectoryStream<Path> files = Files.newDirectoryStream(regions, "r.*.*.mca")) {
            for (Path file : files) {
                String[] name = file.getFileName().toString().split("\\.");
                int regionX;
                int regionZ;
                try {
                    regionX = Integer.parseInt(name[1]);
                    regionZ = Integer.parseInt(name[2]);
                } catch (NumberFormatException e) {
                    continue;
                }
                try (RandomAccessFile region = new RandomAccessFile(file.toFile(), "r")) {
                    if (region.length() < 4096) {
                        continue;
                    }
                    region.readFully(header.array());
                    for (int i = 0; i < 1024; i++) {
                        if (header.getInt(i * 4) != 0) {
                            chunks.add(new ChunkPos(regionX * 32 + (i & 31), regionZ * 32 + (i >> 5)));
                        }
                    }
                } catch (IOException e) {
                    LOGGER.warn("[thepale] Could not read {}; its chunks are not counted as explored", file, e);
                }
            }
        } catch (IOException e) {
            LOGGER.error("[thepale] Could not list {}", regions, e);
        }
        return chunks;
    }

    private static void enqueue(long chunk) {
        if (QUEUED.add(chunk)) {
            QUEUE.enqueue(chunk);
        }
    }

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (PaleCleanse.cleansed()
                && event.getLevel() instanceof ServerLevel level
                && level.dimension() == Level.OVERWORLD
                && event.getChunk() instanceof LevelChunk chunk
                && holdsPale(chunk)) {
            enqueue(chunk.getPos().toLong());
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        if (QUEUE.isEmpty()) {
            return;
        }
        ServerLevel level = event.getServer().overworld();
        if (!PaleCleanse.cleansed()) {
            QUEUE.clear();
            QUEUED.clear();
            return;
        }
        List<ChunkAccess> changed = new ArrayList<>();
        int budget = CommonConfig.get().finale().chunksPerTick();
        // Chunks without the Pale biome cost next to nothing; look at many, turn few.
        for (int looked = 0; looked < 256 && changed.size() < budget && !QUEUE.isEmpty(); looked++) {
            long key = QUEUE.dequeueLong();
            QUEUED.remove(key);
            // Unloaded in the meantime: it queues again when it next loads.
            LevelChunk chunk = level.getChunkSource().getChunkNow(ChunkPos.getX(key), ChunkPos.getZ(key));
            if (chunk != null && holdsPale(chunk)) {
                turn(level, chunk);
                changed.add(chunk);
            }
        }
        if (!changed.isEmpty()) {
            level.getChunkSource().chunkMap.resendBiomesForChunks(changed);
        }
    }

    private static boolean holdsPale(ChunkAccess chunk) {
        for (LevelChunkSection section : chunk.getSections()) {
            if (section.getBiomes().maybeHas(biome -> biome.is(PaleBiomes.THE_PALE))) {
                return true;
            }
        }
        return false;
    }

    /** The Pale biome out, the ocean the seed has at this column in: what /fillbiome does, a chunk at a time. */
    private static void turn(ServerLevel level, LevelChunk chunk) {
        BiomeSource source = level.getChunkSource().getGenerator().getBiomeSource();
        Climate.Sampler sampler = level.getChunkSource().randomState().sampler();
        Holder<Biome> ocean = level.registryAccess().registryOrThrow(Registries.BIOME).getHolderOrThrow(Biomes.OCEAN);
        int firstX = QuartPos.fromBlock(chunk.getPos().getMinBlockX());
        int firstZ = QuartPos.fromBlock(chunk.getPos().getMinBlockZ());
        @SuppressWarnings("unchecked")
        Holder<Biome>[] columns = new Holder[16];
        BiomeResolver resolver = (x, y, z, climate) -> {
            Holder<Biome> now = chunk.getNoiseBiome(x, y, z);
            if (!now.is(PaleBiomes.THE_PALE)) {
                return now;
            }
            int index = (x - firstX & 3) + (z - firstZ & 3) * 4;
            Holder<Biome> replacement = columns[index];
            if (replacement == null) {
                // The same biome from the sea floor to the sky, as the Pale was: what the seed has at the surface.
                replacement = source.getNoiseBiome(x, SEA_LEVEL_QUART, z, sampler);
                if (replacement.is(PaleBiomes.THE_PALE)) {
                    replacement = ocean;
                }
                columns[index] = replacement;
            }
            return replacement;
        };
        chunk.fillBiomesFromNoise(resolver, sampler);
        chunk.setUnsaved(true);
    }

    /** Once the world has loaded, its saved data is the truth (it was read from the file before the levels existed). */
    public static void onServerStarted(ServerStartedEvent event) {
        FinaleState state = FinaleState.get(event.getServer());
        PaleCleanse.set(state.cleansed, state.seaEnd, state.landFull);
        QUEUE.clear();
        QUEUED.clear();
    }
}
