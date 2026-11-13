package com.example.thepale.finale.boss;

import com.example.thepale.ThePale;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/** Tells the clients that a tentacle starts a strike now: which one, where it will land and how long it winds up. */
public record SlamPayload(int entity, int limb, double x, double y, double z, int windup) implements CustomPacketPayload {
    public static final Type<SlamPayload> TYPE = new Type<>(ThePale.id("boss_slam"));
    public static final StreamCodec<FriendlyByteBuf, SlamPayload> STREAM_CODEC = StreamCodec.of(SlamPayload::write, SlamPayload::read);

    @Override
    public Type<SlamPayload> type() {
        return TYPE;
    }

    private static void write(FriendlyByteBuf buf, SlamPayload payload) {
        buf.writeVarInt(payload.entity);
        buf.writeByte(payload.limb);
        buf.writeDouble(payload.x);
        buf.writeDouble(payload.y);
        buf.writeDouble(payload.z);
        buf.writeVarInt(payload.windup);
    }

    private static SlamPayload read(FriendlyByteBuf buf) {
        return new SlamPayload(buf.readVarInt(), buf.readByte(), buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readVarInt());
    }

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        event.registrar("1").optional().playToClient(TYPE, STREAM_CODEC, (payload, context) -> {
            if (context.player().level().getEntity(payload.entity()) instanceof PatchworkAbomination boss
                    && payload.limb() >= 0 && payload.limb() < Limbs.TENTACLES) {
                boss.startSlam(payload.limb(), new Vec3(payload.x(), payload.y(), payload.z()), payload.windup());
            }
        });
    }
}
