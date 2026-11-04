package com.example.palestore.net;

import com.example.palestore.PaleStore;
import io.netty.buffer.ByteBuf;
import java.util.List;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Server -> client: the full list of offers this player has bought. */
public record SyncUnlocksPayload(List<ResourceLocation> unlocked) implements CustomPacketPayload {
    public static final Type<SyncUnlocksPayload> TYPE = new Type<>(PaleStore.id("sync_unlocks"));

    private static final StreamCodec<ByteBuf, List<ResourceLocation>> LIST =
            ResourceLocation.STREAM_CODEC.apply(ByteBufCodecs.list());

    public static final StreamCodec<ByteBuf, SyncUnlocksPayload> CODEC =
            LIST.map(SyncUnlocksPayload::new, SyncUnlocksPayload::unlocked);

    @Override
    public Type<SyncUnlocksPayload> type() {
        return TYPE;
    }
}
