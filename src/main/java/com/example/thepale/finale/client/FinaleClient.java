package com.example.thepale.finale.client;

import com.example.thepale.ThePale;
import com.example.thepale.client.PaleShaderFog;
import com.example.thepale.config.ClientConfig;
import com.example.thepale.finale.FinalePayload;
import com.example.thepale.finale.FinaleState;
import com.example.thepale.finale.FinaleView;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

/**
 * The invasion as the player sees it. While the boss approaches, the sky and the fog darken toward purple (more every
 * day), and from the second day its silhouette stands on the horizon: no entity is drawn that far out (and Distant
 * Horizons draws none at all), so it is a picture standing upright out at sea, drawn nearer than it is but as large as
 * it would look, and growing as the boss comes closer.
 */
public final class FinaleClient {
    private static final ResourceLocation SILHOUETTE = ThePale.id("textures/environment/abomination_silhouette.png");
    /** What the sky and the fog darken toward. */
    private static final float DREAD_RED = 0.11f;
    private static final float DREAD_GREEN = 0.06f;
    private static final float DREAD_BLUE = 0.17f;
    /** How tall the silhouette looks: as something this many blocks tall would at its real distance... */
    private static final double APPARENT_HEIGHT = 70;
    /** ...but never smaller or larger than this, in degrees. */
    private static final double MIN_ANGLE = 4;
    private static final double MAX_ANGLE = 30;
    /** Width of the picture as a share of its height. */
    private static final float ASPECT = 1.5f;
    /** Nearer than this the real boss is in view and the picture is gone. */
    private static final double HIDE_WITHIN = 130;
    private static final double SHOW_FROM = 210;
    private static final float SEA_LEVEL = 63;

    /** How dark things are right now, 0 to 1; eased toward what the server last said. */
    private static float dread;
    private static float lastDread;
    /** Where the silhouette is drawn; eased, so it doesn't jump when the server sends a new position. */
    private static double shownX;
    private static double shownZ;
    private static boolean placed;

    private FinaleClient() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        lastDread = dread;
        if (mc.level == null) {
            dread = 0;
            lastDread = 0;
            placed = false;
            return;
        }
        FinalePayload view = FinaleView.last();
        dread += (targetDread(view) - dread) * 0.02f;
        if (!placed) {
            shownX = view.x();
            shownZ = view.z();
            placed = view.silhouette();
        } else {
            shownX += (view.x() - shownX) * 0.05;
            shownZ += (view.z() - shownZ) * 0.05;
            placed = view.silhouette();
        }
    }

    private static float targetDread(FinalePayload view) {
        float target = switch (view.phaseValue()) {
            case APPROACH -> 0.25f + 0.45f * view.approach();
            case WAITING, BATTLE -> 0.8f;
            case RETREATED -> 0.6f;
            default -> 0f;
        };
        return target * FinaleView.paleStrength();
    }

    /** {@return how dark the invasion makes things this frame, 0 to 1; 0 outside the Pale's dimensions} */
    public static float dread(float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        ClientConfig.Settings config = ClientConfig.get();
        if (mc.level == null || config == null || !config.appliesTo(mc.level)) {
            return 0;
        }
        return Mth.lerp(partialTick, lastDread, dread);
    }

    /** Darkens the Pale's own grey (the wall, the camera fog, the steam) toward purple. */
    public static void tint(Vector3f color, float partialTick) {
        float amount = dread(partialTick) * 0.6f;
        if (amount > 0) {
            // Relative to how lit the grey is, so a dark night stays dark.
            float light = Math.max(color.x(), Math.max(color.y(), color.z()));
            color.set(
                    Mth.lerp(amount, color.x(), DREAD_RED * 2.2f * light),
                    Mth.lerp(amount, color.y(), DREAD_GREEN * 2.2f * light),
                    Mth.lerp(amount, color.z(), DREAD_BLUE * 2.2f * light));
        }
    }

    /** The game's own fog (and with it the horizon) darkens too. Registered after PaleFog's handler. */
    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        float amount = dread((float) event.getPartialTick()) * 0.55f;
        if (amount <= 0) {
            return;
        }
        float light = Math.max(event.getRed(), Math.max(event.getGreen(), event.getBlue()));
        event.setRed(Mth.lerp(amount, event.getRed(), DREAD_RED * 2.2f * light));
        event.setGreen(Mth.lerp(amount, event.getGreen(), DREAD_GREEN * 2.2f * light));
        event.setBlue(Mth.lerp(amount, event.getBlue(), DREAD_BLUE * 2.2f * light));
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
            darkenSky(event);
        } else if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            drawSilhouette(event);
        }
    }

    /** The sky isn't fogged, so paint over it, as PaleFog does for its whiteout. Shader packs draw their own sky. */
    private static void darkenSky(RenderLevelStageEvent event) {
        float alpha = dread(event.getPartialTick().getGameTimeDeltaPartialTick(false)) * 0.6f;
        if (alpha <= 0.003f || PaleShaderFog.active()) {
            return;
        }
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex(-100f, -100f, -1f).setColor(DREAD_RED, DREAD_GREEN, DREAD_BLUE, alpha);
        buffer.addVertex(100f, -100f, -1f).setColor(DREAD_RED, DREAD_GREEN, DREAD_BLUE, alpha);
        buffer.addVertex(100f, 100f, -1f).setColor(DREAD_RED, DREAD_GREEN, DREAD_BLUE, alpha);
        buffer.addVertex(-100f, 100f, -1f).setColor(DREAD_RED, DREAD_GREEN, DREAD_BLUE, alpha);
        BufferUploader.drawWithShader(buffer.buildOrThrow());
        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    private static void drawSilhouette(RenderLevelStageEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientConfig.Settings config = ClientConfig.get();
        FinalePayload view = FinaleView.last();
        if (!placed || !view.silhouette() || mc.level == null || config == null || !config.appliesTo(mc.level)) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        double dx = shownX - camera.x;
        double dz = shownZ - camera.z;
        double distance = Math.sqrt(dx * dx + dz * dz);
        float near = (float) Mth.clamp((distance - HIDE_WITHIN) / (SHOW_FROM - HIDE_WITHIN), 0, 1);
        float strength = view.phaseValue() == FinaleState.Phase.APPROACH ? 0.4f + 0.5f * view.approach() : 0.9f;
        float alpha = strength * near * near * (3f - 2f * near) * FinaleView.paleStrength();
        if (alpha <= 0.01f) {
            return;
        }
        // Drawn inside the far plane (four times the render distance) however far it really is, at the size it
        // would look. Terrain in front of that still hides it.
        double drawAt = Math.min(distance, mc.options.getEffectiveRenderDistance() * 16 * 2.5);
        double angle = Mth.clamp(Math.toDegrees(Math.atan(APPARENT_HEIGHT / distance)), MIN_ANGLE, MAX_ANGLE);
        float height = (float) (drawAt * Math.tan(Math.toRadians(angle)));
        float halfWidth = height * ASPECT / 2;
        double dirX = dx / distance;
        double dirZ = dz / distance;
        // Standing in the sea: the waterline at its real distance, seen from here, brought in to where it is drawn.
        float bottom = (float) ((SEA_LEVEL - camera.y) * drawAt / distance) - height * 0.06f;
        bottom += (float) Math.sin(Util.getMillis() / 2600.0) * height * 0.012f;
        float top = bottom + height;
        float centreX = (float) (dirX * drawAt);
        float centreZ = (float) (dirZ * drawAt);
        float rightX = (float) (-dirZ * halfWidth);
        float rightZ = (float) (dirX * halfWidth);

        boolean shaderPack = PaleShaderFog.active();
        // A shader pack only draws the game's own shaders properly; the particle shader is one it knows.
        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, shaderPack ? DefaultVertexFormat.PARTICLE : DefaultVertexFormat.POSITION_TEX_COLOR);
        int light = LightTexture.pack(15, 15);
        vertex(buffer, centreX - rightX, bottom, centreZ - rightZ, 0, 1, alpha, shaderPack, light);
        vertex(buffer, centreX - rightX, top, centreZ - rightZ, 0, 0, alpha, shaderPack, light);
        vertex(buffer, centreX + rightX, top, centreZ + rightZ, 1, 0, alpha, shaderPack, light);
        vertex(buffer, centreX + rightX, bottom, centreZ + rightZ, 1, 1, alpha, shaderPack, light);

        RenderSystem.setShader(shaderPack ? GameRenderer::getParticleShader : GameRenderer::getPositionTexColorShader);
        RenderSystem.setShaderTexture(0, SILHOUETTE);
        if (shaderPack) {
            mc.gameRenderer.lightTexture().turnOnLightLayer();
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();

        BufferUploader.drawWithShader(buffer.buildOrThrow());

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        if (shaderPack) {
            mc.gameRenderer.lightTexture().turnOffLightLayer();
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder buffer, float x, float y, float z, float u, float v, float alpha, boolean particle, int light) {
        var vertex = buffer.addVertex(x, y, z).setUv(u, v).setColor(1f, 1f, 1f, alpha);
        if (particle) {
            vertex.setLight(light);
        }
    }
}
