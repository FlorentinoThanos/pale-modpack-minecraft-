package com.example.thepale;

import com.example.thepale.client.PaleCloudSea;
import com.example.thepale.client.PaleFog;
import com.example.thepale.client.PaleParticles;
import com.example.thepale.client.PaleShaderFog;
import com.example.thepale.client.PaleWallRenderer;
import com.example.thepale.client.compat.PaleDistantHorizons;
import com.example.thepale.config.ClientConfig;
import com.example.thepale.finale.FinaleView;
import com.example.thepale.finale.client.FinaleClient;
import com.example.thepale.worldgen.PaleShape;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.common.NeoForge;

/** The Pale, client side: the fog wall, the camera fog and the ash. Purely visual. */
@Mod(value = ThePale.MOD_ID, dist = Dist.CLIENT)
public final class ThePaleClient {
    public ThePaleClient(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, ClientConfig.SPEC);
        // One screen for both files: NeoForge lists every config the mod registered.
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);

        modBus.addListener(ClientConfig::onLoad);
        modBus.addListener(ClientConfig::onReload);
        modBus.addListener(PaleWallRenderer::onRegisterShaders);
        modBus.addListener(PaleShaderFog::onRegisterShaders);

        NeoForge.EVENT_BUS.addListener(PaleWallRenderer::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(PaleCloudSea::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(PaleFog::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(PaleFog::onRenderFog);
        NeoForge.EVENT_BUS.addListener(PaleFog::onComputeFogColor);
        NeoForge.EVENT_BUS.addListener(PaleParticles::onClientTick);
        NeoForge.EVENT_BUS.addListener(PaleShaderFog::onRenderLevelStage);
        NeoForge.EVENT_BUS.addListener(PaleShaderFog::onRenderGui);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, PaleShaderFog::onComputeFov);
        // A server without The Pale sends no outline; don't carry the last world's over.
        NeoForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            PaleShape.setClient(PaleShape.circle());
            FinaleView.reset();
        });
        // The invasion: registered after the Pale's own handlers, so it darkens their colours and draws over the wall.
        NeoForge.EVENT_BUS.addListener(FinaleClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(FinaleClient::onComputeFogColor);
        NeoForge.EVENT_BUS.addListener(FinaleClient::onRenderLevelStage);

        // Optional: the DH classes are only loaded when DH is there.
        if (ModList.get().isLoaded("distanthorizons")) {
            modBus.addListener((FMLClientSetupEvent event) -> PaleDistantHorizons.init());
        }
    }
}
