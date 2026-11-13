package com.example.thepale.finale;

import com.example.thepale.ThePale;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * What a client needs to draw the finale: how far the invasion has come and where the boss is out at sea (for the
 * darkened sky and the silhouette on the horizon), and whether the Pale has been cleansed and how long ago (for the
 * fade of the fog wall, the fog and the grey water).
 *
 * @param phase         {@link FinaleState.Phase} ordinal
 * @param approach      0 to 1: how much of the approach is done
 * @param silhouette    whether the silhouette shows yet
 * @param x             where the boss is out at sea
 * @param z             same
 * @param cleansed      whether the Pale has been cleansed
 * @param cleansedTicks ticks since the cleansing
 * @param fadeTicks     ticks the fade takes
 */
public record FinalePayload(
        int phase, float approach, boolean silhouette, double x, double z, boolean cleansed, long cleansedTicks, int fadeTicks)
        implements CustomPacketPayload {
    public static final Type<FinalePayload> TYPE = new Type<>(ThePale.id("finale"));
    public static final StreamCodec<FriendlyByteBuf, FinalePayload> STREAM_CODEC = StreamCodec.of(FinalePayload::write, FinalePayload::read);

    @Override
    public Type<FinalePayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, FinalePayload payload) {
        buf.writeVarInt(payload.phase);
        buf.writeFloat(payload.approach);
        buf.writeBoolean(payload.silhouette);
        buf.writeDouble(payload.x);
        buf.writeDouble(payload.z);
        buf.writeBoolean(payload.cleansed);
        buf.writeVarLong(payload.cleansedTicks);
        buf.writeVarInt(payload.fadeTicks);
    }

    private static FinalePayload read(FriendlyByteBuf buf) {
        return new FinalePayload(
                buf.readVarInt(), buf.readFloat(), buf.readBoolean(), buf.readDouble(), buf.readDouble(),
                buf.readBoolean(), buf.readVarLong(), buf.readVarInt());
    }

    public FinaleState.Phase phaseValue() {
        FinaleState.Phase[] phases = FinaleState.Phase.values();
        return phases[Math.max(0, Math.min(phase, phases.length - 1))];
    }

    static void sendTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, Invasion.describe(player.server));
    }

    static void sendToAll(MinecraftServer server) {
        PacketDistributor.sendToAllPlayers(Invasion.describe(server));
    }
}
