package com.example.palestore.client;

import com.example.palestore.PaleStore;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

/** Client-only entry point. This class is never loaded on a dedicated server. */
@Mod(value = PaleStore.MOD_ID, dist = Dist.CLIENT)
public class PaleStoreClient {
    public PaleStoreClient(IEventBus modBus) {
        NeoForge.EVENT_BUS.addListener(XpHud::onRenderLayer);
    }
}
