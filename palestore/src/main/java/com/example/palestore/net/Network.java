package com.example.palestore.net;

import com.example.palestore.Unlocks;
import com.example.palestore.client.ClientUnlocks;
import java.util.ArrayList;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class Network {

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(SyncUnlocksPayload.TYPE, SyncUnlocksPayload.CODEC, Network::onSync);
    }

    /** Send the player's bought offers to their client. */
    public static void syncTo(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, new SyncUnlocksPayload(new ArrayList<>(Unlocks.get(player))));
    }

    private static void onSync(SyncUnlocksPayload payload, IPayloadContext context) {
        ClientUnlocks.set(payload.unlocked());
    }

    private Network() {}
}
