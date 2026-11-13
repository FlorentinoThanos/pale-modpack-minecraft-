package com.example.thepale.client.compat;

import com.example.thepale.client.PaleFog;
import com.example.thepale.config.ClientConfig;
import com.example.thepale.worldgen.PaleShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiFogFalloff;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiHeightFogMixMode;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiRendererMode;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeDeferredRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeFogRenderEvent;
import com.seibel.distanthorizons.api.methods.events.abstractEvents.DhApiBeforeRenderEvent;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiCancelableEventParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiFogRenderParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiMutableFogRenderParam;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/**
 * Distant Horizons support. Only loaded when DH is installed. Outside the Pale it adds the far cloud wall
 * ({@link PaleCloudWall}); inside, it pulls DH's own fog in to match the Pale camera fog so the LOD terrain disappears
 * into it, and stops DH drawing altogether once the camera fog is closer than the vanilla chunks.
 */
public final class PaleDistantHorizons {
    private static final Logger LOGGER = LogUtils.getLogger();

    @Nullable
    private static PaleCloudWall wall;
    @Nullable
    private static ClientConfig.Settings wallSettings;
    @Nullable
    private static PaleShape wallShape;
    @Nullable
    private static IDhApiLevelWrapper wallLevel;
    @Nullable
    private static IDhApiCustomRenderRegister wallRegister;
    /** Whether DH's renderer is currently switched off by us (an API override, never saved to DH's config file). */
    private static boolean dhSwitchedOff;

    private PaleDistantHorizons() {}

    public static void init() {
        try {
            DhApi.events.bind(DhApiBeforeRenderEvent.class, new BeforeRender());
            DhApi.events.bind(DhApiBeforeDeferredRenderEvent.class, new BeforeDeferredRender());
            DhApi.events.bind(DhApiBeforeFogRenderEvent.class, new BeforeFog());
            NeoForge.EVENT_BUS.addListener(PaleDistantHorizons::onClientTick);
            LOGGER.info("[thepale] Distant Horizons {} found: the fog wall reaches the LOD horizon", DhApi.getModVersion());
        } catch (LinkageError | RuntimeException e) {
            LOGGER.warn("[thepale] Unsupported Distant Horizons version; the Pale will not show in its far terrain", e);
        }
    }

    private static final class BeforeRender extends DhApiBeforeRenderEvent {
        @Override
        public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
            Minecraft mc = Minecraft.getInstance();
            ClientConfig.Settings config = ClientConfig.get();
            if (config == null || mc.level == null) {
                return;
            }
            keepWallAttached(event.value.clientLevelWrapper, config);
            if (fogCoversAllLods()) {
                event.cancelEvent();
            }
        }
    }

    /** DH draws its transparent LODs (water) in a pass of their own; that one has to go too. */
    private static final class BeforeDeferredRender extends DhApiBeforeDeferredRenderEvent {
        @Override
        public void beforeRender(DhApiCancelableEventParam<DhApiRenderParam> event) {
            if (fogCoversAllLods()) {
                event.cancelEvent();
            }
        }
    }

    /**
     * Deep in the Pale, switches DH's renderer off outright. Cancelling its render events isn't enough: its fade pass
     * (blending vanilla chunks into the LODs) runs on its own and keeps showing the last LOD image as a line on the
     * horizon. Switched back as soon as the fog thins out again, or when leaving the world.
     */
    private static void onClientTick(ClientTickEvent.Post event) {
        boolean switchOff = fogCoversAllLods();
        if (switchOff == dhSwitchedOff) {
            return;
        }
        try {
            if (switchOff) {
                DhApi.Delayed.configs.graphics().renderingMode().setValue(EDhApiRendererMode.DISABLED, "The Pale (thick fog)");
            } else {
                DhApi.Delayed.configs.graphics().renderingMode().clearValue();
            }
            dhSwitchedOff = switchOff;
        } catch (RuntimeException e) {
            LOGGER.warn("[thepale] Could not switch Distant Horizons rendering {}", switchOff ? "off" : "back on", e);
            dhSwitchedOff = switchOff; // don't retry every tick
        }
    }

    /** Whether the camera fog ends inside the vanilla chunks, so that everything DH draws would be fully fogged anyway. */
    private static boolean fogCoversAllLods() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        double radius = PaleFog.cameraRadius(mc.gameRenderer.getMainCamera());
        float vanillaDistance = mc.options.getEffectiveRenderDistance() * 16;
        // Only once DH's fog has fully changed over (PaleFog#approach), so the LODs are already fogged when they go.
        return PaleFog.approach(radius) >= 1f && PaleFog.visibility(radius, vanillaDistance) < vanillaDistance;
    }

    private static final class BeforeFog extends DhApiBeforeFogRenderEvent {
        @Override
        public void beforeRender(DhApiCancelableEventParam<EventParam> event) {
            double radius = PaleFog.cameraRadius(Minecraft.getInstance().gameRenderer.getMainCamera());
            if (!PaleFog.inFog(radius)) {
                return;
            }
            DhApiFogRenderParam original = event.value.getOriginalFogRenderParam();
            DhApiMutableFogRenderParam fog = event.value.getFogRenderParam();
            // DH's fog changes over from the player's own settings to the Pale's over the same approach as the camera fog.
            float takeover = PaleFog.approach(radius);
            // DH measures its fog in fractions of its LOD render distance.
            float lodDistance = Math.max(DhApi.Delayed.configs.graphics().chunkRenderDistance().getValue() * 16f, 1f);
            float paleEnd = PaleFog.visibility(radius, lodDistance) / lodDistance;

            fog.setFarFogStartPercent(Mth.lerp(takeover, original.getFarFogStartPercent(), 0f));
            fog.setFarFogEndPercent(Mth.lerp(takeover, original.getFarFogEndPercent(), paleEnd));
            fog.setFarFogMinThickness(Mth.lerp(takeover, original.getFarFogMinThickness(), 0f));
            fog.setFarFogMaxThickness(Mth.lerp(takeover, original.getFarFogMaxThickness(), 1f));
            fog.setFarFogDensity(Mth.lerp(takeover, original.getFarFogDensity(), 1f));
            fog.setHeightFogMinThickness(Mth.lerp(takeover, original.getHeightFogMinThickness(), 0f));
            fog.setHeightFogMaxThickness(Mth.lerp(takeover, original.getHeightFogMaxThickness(), 0f));
            if (takeover > 0.5f) {
                // Same shape as the camera fog: straight-line falloff, no extra height fog.
                fog.setFarFogFalloff(EDhApiFogFalloff.LINEAR);
                fog.setHeightFogMixingMode(EDhApiHeightFogMixMode.MAX);
            }
            // The current vanilla fog colour, which PaleFog has already blended toward the Pale grey.
            float[] paleColor = RenderSystem.getShaderFogColor();
            Color from = original.getFogColor();
            fog.setFogColor(new Color(
                    Mth.clamp(Mth.lerp(takeover, from.getRed() / 255f, paleColor[0]), 0f, 1f),
                    Mth.clamp(Mth.lerp(takeover, from.getGreen() / 255f, paleColor[1]), 0f, 1f),
                    Mth.clamp(Mth.lerp(takeover, from.getBlue() / 255f, paleColor[2]), 0f, 1f),
                    from.getAlpha() / 255f));
        }
    }

    /**
     * Puts the cloud wall into the current DH level's renderer. Built afresh for every DH level (each has its own
     * renderer) and whenever the client config changes.
     */
    private static void keepWallAttached(@Nullable IDhApiLevelWrapper level, ClientConfig.Settings config) {
        // Rebuilt for a new level, new settings, or a new outline (the server sends it after joining).
        if (level != wallLevel || !config.equals(wallSettings) || PaleShape.client() != wallShape) {
            if (wall != null && wallRegister != null && level == wallLevel) {
                wall.detach(wallRegister); // same level, new settings or outline: take the old wall down
            }
            wall = null;
            wallRegister = null;
            wallLevel = level;
            wallSettings = config;
            wallShape = PaleShape.client();
        }
        if (com.example.thepale.finale.FinaleView.paleStrength() <= 0f) {
            // The Pale has been cleansed (the finale): the wall comes down.
            if (wall != null && wallRegister != null) {
                wall.detach(wallRegister);
                wall = null;
                wallRegister = null;
            }
            return;
        }
        if (wall != null || level == null || !config.dimensions().contains(ResourceLocation.tryParse(level.getDimensionName()))) {
            return;
        }
        IDhApiCustomRenderRegister register = level.getRenderRegister();
        if (register == null) {
            return; // DH's level isn't ready yet; try again next frame.
        }
        wall = PaleCloudWall.build(config);
        wall.attach(register);
        wallRegister = register;
        LOGGER.info("[thepale] Added the Distant Horizons cloud wall to {}: {} boxes", level.getDimensionName(), wall.boxCount());
    }
}
