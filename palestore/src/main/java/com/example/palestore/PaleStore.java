package com.example.palestore;

import com.example.palestore.enchant.PaleEnchants;
import com.example.palestore.net.Network;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(PaleStore.MOD_ID)
public class PaleStore {
    public static final String MOD_ID = "palestore";

    public static ResourceLocation id(String path) {
        return ResourceLocation.fromNamespaceAndPath(MOD_ID, path);
    }

    public PaleStore(IEventBus modBus) {
        modBus.addListener(Network::register);
        PaleEnchants.register(modBus);

        NeoForge.EVENT_BUS.addListener(ServerLogic::onLogin);
        NeoForge.EVENT_BUS.addListener(ServerLogic::onRespawn);
        NeoForge.EVENT_BUS.addListener(ServerLogic::onAdvancementEarned);
    }
}
