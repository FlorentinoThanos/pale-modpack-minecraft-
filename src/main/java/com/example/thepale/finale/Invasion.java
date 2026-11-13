package com.example.thepale.finale;

import com.example.thepale.config.CommonConfig;
import com.example.thepale.finale.boss.PatchworkAbomination;
import com.example.thepale.worldgen.PaleShape;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.logging.LogUtils;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import org.slf4j.Logger;

/**
 * The invasion: once started ({@code /thepale invasion start}, which the quest "Summon the Pale" runs), the boss
 * crosses the Pale toward the shore of the island with the most players on it, in approachDays game days. The first
 * day only the sky darkens; then its silhouette shows on the horizon (drawn by the client, see FinaleClient) and comes
 * closer; on the first night after the approach it comes ashore, if a player is there to meet it. If everybody dies or
 * leaves, it goes back into the sea and returns the next night: the finale can't be lost.
 */
public final class Invasion {
    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int DAY = 24000;
    private static final int NIGHT_START = 13000;
    private static final int NIGHT_END = 23000;
    /** Ticks between position updates sent to the clients while it approaches. */
    private static final int SYNC_INTERVAL = 100;
    /** How far off the landing point it waits. */
    private static final double OFFSHORE = 110;
    /** How much further out it stands after a retreat. */
    private static final double RETREAT_DISTANCE = 250;
    /** A player this far past the coast of an island still counts as on it. */
    private static final double ISLAND_MARGIN = 150;

    private static final int STAGE_AWAKEN = 0;
    private static final int STAGE_HORIZON = 1;
    private static final int STAGE_LAST_DAY = 2;
    private static final int STAGE_ARRIVED = 3;

    private Invasion() {}

    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        ServerLevel level = server.overworld();
        if (level == null) {
            return;
        }
        FinaleState state = FinaleState.get(server);
        if (state.phase == FinaleState.Phase.NONE || state.phase == FinaleState.Phase.DEFEATED) {
            return;
        }
        CommonConfig.Finale config = CommonConfig.get().finale();
        long dayTime = level.getDayTime();
        // Game time as the sky shows it, so a night slept through or /time add counts. A clock that stands still or
        // was set back counts as one tick.
        long passed = state.lastDayTime < 0 ? 1 : dayTime - state.lastDayTime;
        if (passed <= 0) {
            passed = 1;
        }
        state.lastDayTime = dayTime;

        switch (state.phase) {
            case APPROACH -> {
                long total = config.approachTicks();
                state.progress = Math.min(state.progress + passed, total);
                boolean silhouette = silhouetteShows(state, config);
                if (silhouette && !state.retargeted) {
                    // Last look at where the players are, before anybody can see where it is heading.
                    pickTarget(level, state, config);
                    state.retargeted = true;
                    FinalePayload.sendToAll(server);
                }
                if (state.progress >= total) {
                    state.phase = FinaleState.Phase.WAITING;
                    announce(server, state, STAGE_ARRIVED, config);
                    FinalePayload.sendToAll(server);
                } else if (total - state.progress <= DAY && total > DAY) {
                    announce(server, state, STAGE_LAST_DAY, config);
                } else if (silhouette) {
                    announce(server, state, STAGE_HORIZON, config);
                }
                if (server.getTickCount() % SYNC_INTERVAL == 0) {
                    FinalePayload.sendToAll(server);
                }
            }
            case WAITING -> {
                if (isNight(dayTime) && playerNear(level, state, config.spawnRange()) && comeAshore(level, state)) {
                    state.phase = FinaleState.Phase.BATTLE;
                    FinalePayload.sendToAll(server);
                }
            }
            case BATTLE -> {
                if (!bossPresent(level, state)) {
                    // Gone without dying: the chunk was unloaded, the server restarted or it was removed by a command.
                    retreat(server, state, false);
                }
            }
            case RETREATED -> {
                if (isNight(dayTime) && night(dayTime) > state.retreatNight) {
                    state.phase = FinaleState.Phase.WAITING;
                    broadcast(server, "message.thepale.finale.returns", FinaleSounds.ARRIVE, 0.8f, state, config);
                    FinalePayload.sendToAll(server);
                }
            }
            default -> {}
        }
        state.setDirty();
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            FinalePayload.sendTo(player);
            FinaleState state = FinaleState.get(player.server);
            // Somebody who wasn't online when it happened.
            if (state.phase != FinaleState.Phase.NONE || state.cleansed) {
                FinaleAdvancements.grant(player, FinaleAdvancements.ROOT);
            }
            if (state.cleansed) {
                FinaleAdvancements.grant(player, FinaleAdvancements.CLEANSED);
            }
        }
    }

    private static boolean isNight(long dayTime) {
        long time = Math.floorMod(dayTime, (long) DAY);
        return time >= NIGHT_START && time < NIGHT_END;
    }

    /** {@return which night this is, or the last one if it is day}: the number goes up at every dusk. */
    private static long night(long dayTime) {
        return Math.floorDiv(dayTime - NIGHT_START, (long) DAY);
    }

    private static boolean silhouetteShows(FinaleState state, CommonConfig.Finale config) {
        return state.progress >= Math.min(config.silhouetteFromDay() * DAY, config.approachTicks());
    }

    private static boolean playerNear(ServerLevel level, FinaleState state, double range) {
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator()
                    && Mth.square(player.getX() - state.landX) + Mth.square(player.getZ() - state.landZ) <= range * range) {
                return true;
            }
        }
        return false;
    }

    // ----- The boss itself -----

    /** Puts the boss on the shore. {@return false if it could not be done now} */
    private static boolean comeAshore(ServerLevel level, FinaleState state) {
        int x = Mth.floor(state.landX);
        int z = Mth.floor(state.landZ);
        if (!level.isLoaded(new BlockPos(x, level.getSeaLevel(), z))) {
            return false;
        }
        PatchworkAbomination boss = FinaleRegistry.PATCHWORK_ABOMINATION.get().create(level);
        if (boss == null) {
            return false;
        }
        Vec3 home = new Vec3(state.landX, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), state.landZ);
        // It rises out of the sea just off the landing point and comes in.
        double dx = state.offshoreX - state.landX;
        double dz = state.offshoreZ - state.landZ;
        double length = Math.max(Math.sqrt(dx * dx + dz * dz), 1);
        double leash = CommonConfig.get().finale().bossLeash();
        double fromX = state.landX + dx / length * leash;
        double fromZ = state.landZ + dz / length * leash;
        float yaw = (float) (Mth.atan2(-dz, -dx) * Mth.RAD_TO_DEG) - 90f;
        boss.moveTo(fromX, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(fromX), Mth.floor(fromZ)) - 6, fromZ, yaw, 0);
        boss.setHome(home, true);
        if (!level.addFreshEntity(boss)) {
            return false;
        }
        state.boss = boss.getUUID();
        broadcast(level.getServer(), "message.thepale.finale.ashore", FinaleSounds.ARRIVE, 1.2f, state, CommonConfig.get().finale());
        LOGGER.info("[thepale] The boss has come ashore at ({}, {})", x, z);
        return true;
    }

    private static boolean bossPresent(ServerLevel level, FinaleState state) {
        return state.boss != null && level.getEntity(state.boss) != null;
    }

    private static void removeBoss(ServerLevel level, FinaleState state) {
        if (state.boss != null) {
            net.minecraft.world.entity.Entity boss = level.getEntity(state.boss);
            if (boss != null) {
                boss.discard();
            }
            state.boss = null;
        }
    }

    /** The boss left the shore without dying. It comes back the next night. */
    public static void retreat(MinecraftServer server, FinaleState state, boolean announce) {
        if (state.phase != FinaleState.Phase.BATTLE) {
            return;
        }
        ServerLevel level = server.overworld();
        removeBoss(level, state);
        state.phase = FinaleState.Phase.RETREATED;
        long dayTime = level.getDayTime();
        // Retreating by day (after a /time set, say) counts for the night before, so it returns at the coming dusk.
        state.retreatNight = night(dayTime);
        state.setDirty();
        if (announce) {
            broadcast(server, "message.thepale.finale.retreats", FinaleSounds.HORIZON, 0.7f, state, CommonConfig.get().finale());
        }
        FinalePayload.sendToAll(server);
        LOGGER.info("[thepale] The boss retreated; it returns the next night");
    }

    /** The boss died. */
    public static void defeated(MinecraftServer server) {
        FinaleState state = FinaleState.get(server);
        if (state.phase == FinaleState.Phase.NONE) {
            return; // summoned by hand outside an invasion
        }
        state.phase = FinaleState.Phase.DEFEATED;
        state.boss = null;
        state.setDirty();
        FinalePayload.sendToAll(server);
        LOGGER.info("[thepale] The boss is dead");
    }

    // ----- Starting and stopping -----

    /** {@return a line for the command's caller, and whether the invasion was started} */
    private static Component start(MinecraftServer server) {
        FinaleState state = FinaleState.get(server);
        if (state.cleansed) {
            return Component.translatable("commands.thepale.invasion.cleansed");
        }
        if (state.phase == FinaleState.Phase.DEFEATED) {
            return Component.translatable("commands.thepale.invasion.defeated");
        }
        if (state.phase != FinaleState.Phase.NONE) {
            return Component.translatable("commands.thepale.invasion.already");
        }
        CommonConfig.Finale config = CommonConfig.get().finale();
        ServerLevel level = server.overworld();
        state.phase = FinaleState.Phase.APPROACH;
        state.progress = 0;
        state.lastDayTime = level.getDayTime();
        state.retargeted = false;
        state.announced = -1;
        state.boss = null;
        pickTarget(level, state, config);
        state.setDirty();
        announce(server, state, STAGE_AWAKEN, config);
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            FinaleAdvancements.grant(player, FinaleAdvancements.ROOT);
        }
        FinalePayload.sendToAll(server);
        LOGGER.info("[thepale] The invasion has started: landing at ({}, {}), {} game days", Math.round(state.landX), Math.round(state.landZ), config.approachDays());
        return Component.translatable("commands.thepale.invasion.started", Math.round(state.landX), Math.round(state.landZ));
    }

    private static Component cancel(MinecraftServer server) {
        FinaleState state = FinaleState.get(server);
        if (state.phase == FinaleState.Phase.NONE) {
            return Component.translatable("commands.thepale.invasion.none");
        }
        removeBoss(server.overworld(), state);
        state.phase = FinaleState.Phase.NONE;
        state.progress = 0;
        state.announced = -1;
        state.setDirty();
        FinalePayload.sendToAll(server);
        return Component.translatable("commands.thepale.invasion.cancelled");
    }

    /** Jumps to the end of the approach: the boss stands off the shore and comes ashore at night. */
    private static Component skip(MinecraftServer server) {
        FinaleState state = FinaleState.get(server);
        if (state.phase != FinaleState.Phase.APPROACH && state.phase != FinaleState.Phase.RETREATED) {
            return Component.translatable("commands.thepale.invasion.cannot_skip", state.phase.name());
        }
        CommonConfig.Finale config = CommonConfig.get().finale();
        if (state.phase == FinaleState.Phase.APPROACH) {
            if (!state.retargeted) {
                pickTarget(server.overworld(), state, config);
                state.retargeted = true;
            }
            state.progress = config.approachTicks();
            announce(server, state, STAGE_ARRIVED, config);
        }
        state.phase = FinaleState.Phase.WAITING;
        state.setDirty();
        FinalePayload.sendToAll(server);
        return Component.translatable("commands.thepale.invasion.skipped", Math.round(state.landX), Math.round(state.landZ));
    }

    private static Component status(MinecraftServer server) {
        FinaleState state = FinaleState.get(server);
        CommonConfig.Finale config = CommonConfig.get().finale();
        MutableComponent text = Component.literal("Phase: " + state.phase.name());
        if (state.phase != FinaleState.Phase.NONE) {
            FinalePayload payload = describe(server);
            text.append(String.format(java.util.Locale.ROOT,
                    "; approach %.2f of %.2f days; landing point (%d, %d); boss at sea (%d, %d)",
                    state.progress / (double) DAY, config.approachDays(),
                    Math.round(state.landX), Math.round(state.landZ), Math.round(payload.x()), Math.round(payload.z())));
        }
        text.append(state.cleansed
                ? String.format(java.util.Locale.ROOT, "; the Pale is cleansed (open sea to %d blocks past the coasts, new lands from %d)",
                        Math.round(state.seaEnd - PaleShape.server().landEnd()), Math.round(state.landFull - PaleShape.server().landEnd()))
                : "; the Pale is not cleansed");
        return text;
    }

    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        // /thepale survey is registered by IslandSurvey; Brigadier merges the two.
        dispatcher.register(Commands.literal("thepale")
                .requires(source -> source.hasPermission(2))
                .then(Commands.literal("invasion")
                        .then(Commands.literal("start").executes(context -> reply(context.getSource(), () -> start(context.getSource().getServer()))))
                        .then(Commands.literal("status").executes(context -> reply(context.getSource(), () -> status(context.getSource().getServer()))))
                        .then(Commands.literal("skip").executes(context -> reply(context.getSource(), () -> skip(context.getSource().getServer()))))
                        .then(Commands.literal("cancel").executes(context -> reply(context.getSource(), () -> cancel(context.getSource().getServer())))))
                // For trying the fight: the boss right here, outside any invasion. Killing it changes nothing in the world.
                .then(Commands.literal("boss").then(Commands.literal("spawn").executes(context -> reply(context.getSource(), () -> spawnHere(context.getSource()))))));
    }

    private static Component spawnHere(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        PatchworkAbomination boss = FinaleRegistry.PATCHWORK_ABOMINATION.get().create(level);
        if (boss == null) {
            return Component.literal("Could not create the boss");
        }
        Vec3 at = source.getPosition();
        boss.moveTo(at.x, at.y + 6, at.z, source.getRotation().y, 0);
        boss.setHome(at, false);
        level.addFreshEntity(boss);
        return Component.translatable("commands.thepale.boss.spawned");
    }

    static int reply(CommandSourceStack source, Supplier<Component> action) {
        Component line = action.get();
        source.sendSuccess(() -> line, true);
        return 1;
    }

    // ----- Where it lands -----

    /**
     * Picks the landing point: on the island with the most players, the stretch of shore nearest to them, so the
     * fight is somewhere they can walk to; and from it the spot off the shore where the boss waits and the spot far
     * out in the Pale where it starts.
     */
    private static void pickTarget(ServerLevel level, FinaleState state, CommonConfig.Finale config) {
        PaleShape shape = PaleShape.server();
        List<PaleShape.Island> islands = shape.islands();
        double edge = CommonConfig.get().paleBiomeRadius() + ISLAND_MARGIN;
        int[] counts = new int[islands.size()];
        double[] sumX = new double[islands.size()];
        double[] sumZ = new double[islands.size()];
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || shape.effectiveRadius(player.getX(), player.getZ()) > edge) {
                continue;
            }
            int index = islands.indexOf(shape.nearest(player.getX(), player.getZ()));
            counts[index]++;
            sumX[index] += player.getX();
            sumZ[index] += player.getZ();
        }
        int best = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        PaleShape.Island island = islands.get(best);
        double towardX;
        double towardZ;
        if (counts[best] > 0) {
            towardX = sumX[best] / counts[best];
            towardZ = sumZ[best] / counts[best];
        } else {
            // Nobody in the Overworld: the shore nearest to the world spawn.
            BlockPos spawn = level.getSharedSpawnPos();
            towardX = spawn.getX();
            towardZ = spawn.getZ();
        }

        double bestAngle = 0;
        double bestDistance = Double.MAX_VALUE;
        for (int degree = 0; degree < 360; degree++) {
            double angle = Math.toRadians(degree);
            double coast = island.distanceAt(shape.landEnd(), angle, shape.landEnd());
            double x = island.centerX() + Math.cos(angle) * coast;
            double z = island.centerZ() + Math.sin(angle) * coast;
            double distance = Mth.square(x - towardX) + Mth.square(z - towardZ);
            if (distance < bestDistance) {
                bestDistance = distance;
                bestAngle = angle;
            }
        }
        double outX = Math.cos(bestAngle);
        double outZ = Math.sin(bestAngle);
        // The config's coast is where the sea floor has finished sinking; the waterline is a little inland of it.
        // Walk in from the sea until the generator says there is ground above the water.
        double coast = island.distanceAt(shape.landEnd(), bestAngle, shape.landEnd());
        double shore = Math.max(0, coast - 120);
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        for (double distance = coast + 40; distance > Math.max(0, coast - 500); distance -= 4) {
            int x = Mth.floor(island.centerX() + outX * distance);
            int z = Mth.floor(island.centerZ() + outZ * distance);
            int ground = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, level.getChunkSource().randomState());
            if (ground > level.getSeaLevel()) {
                shore = distance;
                break;
            }
        }
        state.landX = island.centerX() + outX * shore;
        state.landZ = island.centerZ() + outZ * shore;
        state.offshoreX = state.landX + outX * OFFSHORE;
        state.offshoreZ = state.landZ + outZ * OFFSHORE;
        state.startX = state.landX + outX * config.approachStartDistance();
        state.startZ = state.landZ + outZ * config.approachStartDistance();
        state.setDirty();
    }

    /** {@return what the clients are told} */
    static FinalePayload describe(MinecraftServer server) {
        FinaleState state = FinaleState.get(server);
        CommonConfig.Finale config = CommonConfig.get().finale();
        float approach = (float) Mth.clamp(state.progress / (double) config.approachTicks(), 0, 1);
        double x = state.offshoreX;
        double z = state.offshoreZ;
        boolean silhouette = false;
        switch (state.phase) {
            case APPROACH -> {
                x = Mth.lerp(approach, state.startX, state.offshoreX);
                z = Mth.lerp(approach, state.startZ, state.offshoreZ);
                silhouette = silhouetteShows(state, config);
            }
            case WAITING, BATTLE -> silhouette = true;
            case RETREATED -> {
                double dx = state.offshoreX - state.landX;
                double dz = state.offshoreZ - state.landZ;
                double length = Math.max(Math.sqrt(dx * dx + dz * dz), 1);
                x += dx / length * RETREAT_DISTANCE;
                z += dz / length * RETREAT_DISTANCE;
                silhouette = true;
            }
            default -> {}
        }
        long cleansedTicks = state.cleansed ? Math.max(0, server.overworld().getGameTime() - state.cleansedAt) : 0;
        return new FinalePayload(
                state.phase.ordinal(), approach, silhouette, x, z,
                state.cleansed, cleansedTicks, (int) Math.round(config.fadeSeconds() * 20));
    }

    // ----- Telling the players -----

    private static void announce(MinecraftServer server, FinaleState state, int stage, CommonConfig.Finale config) {
        if (state.announced >= stage) {
            return;
        }
        state.announced = stage;
        switch (stage) {
            case STAGE_AWAKEN -> broadcast(server, "message.thepale.finale.awaken", FinaleSounds.AWAKEN, 1f, state, config);
            case STAGE_HORIZON -> broadcast(server, "message.thepale.finale.horizon", FinaleSounds.HORIZON, 1f, state, config);
            case STAGE_LAST_DAY -> broadcast(server, "message.thepale.finale.last_day", FinaleSounds.HORIZON, 0.8f, state, config);
            case STAGE_ARRIVED -> broadcast(server, "message.thepale.finale.arrived", FinaleSounds.ARRIVE, 1f, state, config);
            default -> {}
        }
    }

    private static void broadcast(
            MinecraftServer server, String key, Supplier<SoundEvent> sound, float pitch, FinaleState state, CommonConfig.Finale config) {
        MutableComponent line = Component.translatable(key).withStyle(ChatFormatting.DARK_PURPLE);
        boolean atShore = key.endsWith(".arrived") || key.endsWith(".returns");
        if (atShore && config.announceCoordinates()) {
            line.append(Component.translatable("message.thepale.finale.where", Math.round(state.landX), Math.round(state.landZ))
                    .withStyle(ChatFormatting.GRAY));
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            player.sendSystemMessage(line);
            if (player.level().dimension() == Level.OVERWORLD) {
                player.playNotifySound(sound.get(), SoundSource.HOSTILE, 1f, pitch);
            }
        }
    }
}
