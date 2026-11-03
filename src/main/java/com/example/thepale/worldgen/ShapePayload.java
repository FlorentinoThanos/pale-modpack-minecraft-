package com.example.thepale.worldgen;

import com.example.thepale.ThePale;
import com.mojang.logging.LogUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.slf4j.Logger;

/** Tells a client where the islands are ({@link PaleShape}), so its fog wall and fog follow the same coasts. */
public record ShapePayload(List<double[]> islands, List<Long> seeds, double landEnd) implements CustomPacketPayload {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final Type<ShapePayload> TYPE = new Type<>(ThePale.id("islands"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ShapePayload> STREAM_CODEC = StreamCodec.of(ShapePayload::write, ShapePayload::read);
    /** Numbers per island in {@link PaleShape#describe()}. */
    private static final int FIELDS = 6;

    @Override
    public Type<ShapePayload> type() {
        return TYPE;
    }

    private static void write(RegistryFriendlyByteBuf buf, ShapePayload payload) {
        buf.writeDouble(payload.landEnd());
        buf.writeVarInt(payload.islands().size());
        for (int i = 0; i < payload.islands().size(); i++) {
            for (double value : payload.islands().get(i)) {
                buf.writeDouble(value);
            }
            buf.writeLong(payload.seeds().get(i));
        }
    }

    private static ShapePayload read(RegistryFriendlyByteBuf buf) {
        double landEnd = buf.readDouble();
        int count = Math.min(buf.readVarInt(), 16);
        List<double[]> islands = new ArrayList<>();
        List<Long> seeds = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            double[] island = new double[FIELDS];
            for (int j = 0; j < FIELDS; j++) {
                island[j] = buf.readDouble();
            }
            islands.add(island);
            seeds.add(buf.readLong());
        }
        return new ShapePayload(islands, seeds, landEnd);
    }

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // Optional: a client without the mod can still join, it just has no fog wall.
        event.registrar("2").optional().playToClient(TYPE, STREAM_CODEC, (payload, context) -> {
            PaleShape shape = PaleShape.fromDescription(payload.islands(), payload.seeds(), payload.landEnd());
            PaleShape.setClient(shape);
            LOGGER.info("[thepale] World layout from the server: {}", shape.islands());
        });
    }

    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PaleShape shape = PaleShape.server();
            PacketDistributor.sendToPlayer(player, new ShapePayload(shape.describe(), shape.seeds(), shape.landEnd()));
        }
    }
}
