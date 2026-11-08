package com.example.thepale.client;

import com.example.thepale.ThePale;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.lang.reflect.Method;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.slf4j.Logger;

/**
 * The Pale fog for shader packs. Packs like Complementary draw their own fog and ignore the game's, so with one
 * active the Pale would be clear. The Pale is drawn as steam instead ({@link PaleSteam}): worked out once the level is
 * drawn, and laid over the finished frame after the shader pack and before the HUD.
 *
 * <p>If the steam can't be drawn on this machine, a plain fog is laid over the frame instead (and the wall is drawn
 * as particles): the Pale is open sea, so the distance each pixel sees is where its view ray meets the water; near
 * water stays clear, and everything at or above the horizon is fully fogged. It fades in over the same approach as
 * the camera fog.
 */
public final class PaleShaderFog {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Top of the sea: water fills up to y = 62 and its surface sits a little below the block top. */
    private static final float SEA_SURFACE_Y = 62.9f;

    /** For testing the overlay without a shader pack: -Dthepale.debug.shaderFog=true. */
    private static final boolean FORCED = Boolean.getBoolean("thepale.debug.shaderFog");

    @Nullable
    private static ShaderInstance shader;
    /** The camera matrices of this frame, if the level render handed them out; the HUD pass has its own. */
    private static final Matrix4f viewRotation = new Matrix4f();
    private static final Matrix4f projection = new Matrix4f();
    private static boolean haveMatrices;
    /** The field of view of the level render, for working the matrices out when they weren't handed out. */
    private static double fov = 70;
    private static boolean loggedDrawing;
    private static boolean loggedMissingShader;
    /** The fog colour the game would use without the Pale, which the overlay blends from as it turns grey. */
    private static float normalRed = PaleWallRenderer.RED;
    private static float normalGreen = PaleWallRenderer.GREEN;
    private static float normalBlue = PaleWallRenderer.BLUE;

    @Nullable
    private static Method irisInstance;
    @Nullable
    private static Method irisShaderPackInUse;
    private static boolean irisLooked;
    private static boolean lastShaderPackInUse;

    private PaleShaderFog() {}

    public static void onRegisterShaders(RegisterShadersEvent event) {
        PaleSteam.onResourcesReloaded();
        shader = null;
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(ThePale.MOD_ID, "pale_screen_fog"),
                            DefaultVertexFormat.POSITION),
                    loaded -> shader = loaded);
        } catch (IOException e) {
            LOGGER.error("Could not load the Pale screen fog shader; the Pale will be clear with shader packs", e);
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY || event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL) {
            viewRotation.set(event.getModelViewMatrix());
            projection.set(event.getProjectionMatrix());
            haveMatrices = true;
        }
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_LEVEL && steam()) {
            PaleSteam.render(event, normalRed, normalGreen, normalBlue);
        }
    }

    /** Registered at the lowest priority, so this is the field of view after every other mod has had its say. */
    public static void onComputeFov(ViewportEvent.ComputeFov event) {
        if (event.usedConfiguredFov()) {
            fov = event.getFOV();
        }
    }

    static void rememberNormalFogColor(float red, float green, float blue) {
        normalRed = red;
        normalGreen = green;
        normalBlue = blue;
    }

    /** Whether the Pale fog is drawn by this overlay rather than through the game's own fog (a shader pack is on). */
    public static boolean active() {
        return FORCED || shaderPackInUse();
    }

    /**
     * Whether the Pale is drawn as steam ({@link PaleSteam}): a shader pack is on and the steam works on this machine.
     * The steam is the whole Pale then: the wall, the sea of clouds and Distant Horizons' cloud wall aren't drawn.
     */
    public static boolean steam() {
        return active() && PaleSteam.usable();
    }

    public static void onRenderGui(RenderGuiEvent.Pre event) {
        boolean capturedThisFrame = haveMatrices;
        haveMatrices = false;
        if (!active()) {
            PaleSteam.discardFrame();
            return;
        }
        if (PaleSteam.usable()) {
            PaleSteam.composite();
            return;
        }
        ShaderInstance fogShader = shader;
        if (fogShader == null) {
            if (!loggedMissingShader) {
                loggedMissingShader = true;
                LOGGER.warn("[thepale] A shader pack is on but the Pale screen fog shader isn't loaded; the Pale will be clear");
            }
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Camera camera = mc.gameRenderer.getMainCamera();
        double radius = PaleFog.cameraRadius(camera);
        // Under water, lava or powder snow, or blinded, the shader pack's own thick fog is the right one.
        if (!PaleFog.inFog(radius) || camera.getFluidInCamera() != FogType.NONE || PaleFog.blinded(camera)) {
            return;
        }
        float far = PaleFog.visibility(radius, mc.options.getEffectiveRenderDistance() * 16);
        // The overlay fogs the whole sky, so it fades in over the approach; past innerRadius it is at full strength.
        float strength = PaleFog.approach(radius);
        float paleness = PaleFog.paleness(radius);
        if (strength <= 0.001f) {
            return;
        }

        if (!capturedThisFrame) {
            // Some renderers skip the level stage events; the camera has everything needed to work the matrices out.
            viewRotation.rotation(camera.rotation().conjugate(new Quaternionf()));
            projection.set(mc.gameRenderer.getProjectionMatrix(fov));
        }
        if (!loggedDrawing) {
            loggedDrawing = true;
            LOGGER.info("[thepale] Drawing the Pale fog over the shader pack (camera matrices {})",
                    capturedThisFrame ? "from the level render" : "worked out from the camera");
        }
        Matrix4f inverseViewProjection = new Matrix4f(projection).mul(viewRotation).invert();
        fogShader.safeGetUniform("PaleInvViewProj").set(inverseViewProjection);
        fogShader.safeGetUniform("PaleEyeAboveSea").set((float) camera.getPosition().y - SEA_SURFACE_Y);
        fogShader.safeGetUniform("PaleFogRange").set(far * PaleFog.NEAR_FRACTION, far);
        // The colour turns from the normal fog colour to grey a little behind the fog thickening.
        fogShader.safeGetUniform("PaleFogColor").set(
                Mth.lerp(paleness, normalRed, PaleFog.litColor().x()),
                Mth.lerp(paleness, normalGreen, PaleFog.litColor().y()),
                Mth.lerp(paleness, normalBlue, PaleFog.litColor().z()),
                strength);

        RenderSystem.setShader(() -> fogShader);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        // Straight in clip space: the vertex shader ignores the HUD's matrices.
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        buffer.addVertex(-1f, -1f, 0f);
        buffer.addVertex(1f, -1f, 0f);
        buffer.addVertex(1f, 1f, 0f);
        buffer.addVertex(-1f, 1f, 0f);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        RenderSystem.depthMask(true);
        // The HUD expects the depth test on (Gui#render turns it on before drawing its layers).
        RenderSystem.enableDepthTest();
    }

    /** Asks Iris, if it is installed, whether a shader pack is on. Looked up by reflection: Iris is optional. */
    private static boolean shaderPackInUse() {
        if (!irisLooked) {
            irisLooked = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                irisInstance = api.getMethod("getInstance");
                irisShaderPackInUse = api.getMethod("isShaderPackInUse");
                LOGGER.info("[thepale] Iris found: the Pale fog will be laid over shader packs");
            } catch (ReflectiveOperationException | LinkageError e) {
                irisInstance = null;
                if (ModList.get().isLoaded("iris") || ModList.get().isLoaded("oculus")) {
                    LOGGER.warn("[thepale] Iris is installed but its API wasn't found; the Pale fog won't show with shader packs", e);
                }
            }
        }
        if (irisInstance == null || irisShaderPackInUse == null) {
            return false;
        }
        try {
            boolean inUse = (boolean) irisShaderPackInUse.invoke(irisInstance.invoke(null));
            if (inUse != lastShaderPackInUse) {
                lastShaderPackInUse = inUse;
                LOGGER.info("[thepale] Shader pack {}: the Pale is drawn {}", inUse ? "on" : "off",
                        inUse ? (PaleSteam.usable() ? "as steam over the finished frame" : "as plain fog over the finished frame and the wall as particles")
                                : "with the game's own fog");
            }
            return inUse;
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
