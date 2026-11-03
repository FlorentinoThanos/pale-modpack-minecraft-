package com.example.thepale.client;

import com.example.thepale.config.ClientConfig;
import com.example.thepale.worldgen.PaleAltitude;
import com.example.thepale.worldgen.PaleShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

/**
 * Camera fog inside the Pale, styled on the Basalt Deltas: a haze that starts a little way out. It eases in over the
 * last 120 blocks before the inner radius, where you can see about fogEntryVisibility blocks, and keeps getting
 * thicker to fogFullVisibility at the outer radius (both in the client config). Shader packs ignore this fog;
 * {@link PaleShaderFog} covers that case.
 */
public final class PaleFog {
    /** Blocks before innerRadius over which the fog and its colour ease in, so there's no line where the Pale starts. */
    private static final double APPROACH = 120;
    /** Blocks past innerRadius by which the fog and sky have turned fully grey. */
    private static final double GREY_BY = 90;
    /** Where the fog starts, as a fraction of where it ends: the Nether's thick-fog proportions. */
    public static final float NEAR_FRACTION = 0.1f;
    /** No camera radius: the Pale isn't in this dimension, or the config isn't loaded yet. */
    public static final double NOWHERE = -1;

    private static final Vector3f litColor = new Vector3f(PaleWallRenderer.RED, PaleWallRenderer.GREEN, PaleWallRenderer.BLUE);

    public static void onRenderFog(ViewportEvent.RenderFog event) {
        if (PaleShaderFog.active()) {
            return; // the shader pack ignores the game's fog; PaleShaderFog draws it instead
        }
        double radius = cameraRadius(event.getCamera());
        if (!inFog(radius)) {
            return;
        }
        float far = visibility(radius, event.getFarPlaneDistance());
        // Leave vanilla alone where its own fog is already thicker (water, lava, blindness, powder snow).
        if (far >= event.getFarPlaneDistance()) {
            return;
        }
        // Vanilla's fog only starts close to where it ends; ease the start in toward the Nether-like proportions
        // too, or the haze would jump closer the moment the approach begins.
        float vanillaNearFraction = event.getNearPlaneDistance() / Math.max(event.getFarPlaneDistance(), 1f);
        event.setNearPlaneDistance(far * Mth.lerp(approach(radius), vanillaNearFraction, NEAR_FRACTION));
        event.setFarPlaneDistance(far);
        // NeoForge only applies the modified values if the event is cancelled.
        event.setCanceled(true);
    }

    public static void onComputeFogColor(ViewportEvent.ComputeFogColor event) {
        // Fired once at the start of every frame, so the wall, the sky overlay and the shader-pack fog use this too.
        updateLitColor(event.getCamera(), (float) event.getPartialTick());
        PaleShaderFog.rememberNormalFogColor(event.getRed(), event.getGreen(), event.getBlue());
        if (PaleShaderFog.active()) {
            return; // the shader pack ignores the game's fog; PaleShaderFog draws it instead
        }
        double radius = cameraRadius(event.getCamera());
        // Under water, lava or powder snow, or blinded, vanilla's own colour is the right one.
        if (!inFog(radius) || event.getCamera().getFluidInCamera() != FogType.NONE || blinded(event.getCamera())) {
            return;
        }
        float blend = paleness(radius);
        event.setRed(Mth.lerp(blend, event.getRed(), litColor.x()));
        event.setGreen(Mth.lerp(blend, event.getGreen(), litColor.y()));
        event.setBlue(Mth.lerp(blend, event.getBlue(), litColor.z()));
    }

    /**
     * {@return the Pale grey as lit this frame}: darkened at night and in rain or thunder exactly as vanilla darkens
     * the Overworld fog, and brightened by night vision. Don't modify it.
     */
    public static Vector3f litColor() {
        return litColor;
    }

    private static void updateLitColor(Camera camera, float partialTick) {
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null) {
            litColor.set(PaleWallRenderer.RED, PaleWallRenderer.GREEN, PaleWallRenderer.BLUE);
            return;
        }
        // FogRenderer#setupColor and DimensionSpecialEffects.OverworldEffects#getBrightnessDependentFogColor.
        float daylight = Mth.clamp(Mth.cos(level.getTimeOfDay(partialTick) * Mth.TWO_PI) * 2f + 0.5f, 0f, 1f);
        float red = PaleWallRenderer.RED * (daylight * 0.94f + 0.06f);
        float green = PaleWallRenderer.GREEN * (daylight * 0.94f + 0.06f);
        float blue = PaleWallRenderer.BLUE * (daylight * 0.91f + 0.09f);
        float rain = level.getRainLevel(partialTick);
        red *= 1f - rain * 0.5f;
        green *= 1f - rain * 0.5f;
        blue *= 1f - rain * 0.4f;
        float thunder = 1f - level.getThunderLevel(partialTick) * 0.5f;
        red *= thunder;
        green *= thunder;
        blue *= thunder;
        if (camera.getEntity() instanceof LivingEntity living
                && living.hasEffect(MobEffects.NIGHT_VISION)
                && !living.hasEffect(MobEffects.DARKNESS)) {
            float vision = GameRenderer.getNightVisionScale(living, partialTick);
            float boost = Math.min(1f / red, Math.min(1f / green, 1f / blue));
            red = Mth.lerp(vision, red, red * boost);
            green = Mth.lerp(vision, green, green * boost);
            blue = Mth.lerp(vision, blue, blue * boost);
        }
        litColor.set(red, green, blue);
    }

    static boolean blinded(Camera camera) {
        return camera.getEntity() instanceof LivingEntity living
                && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    /**
     * Vanilla draws the sun, moon, stars and sunrise glow without fog, so they would shine through the whiteout. Paint
     * over the sky in the fog colour, as opaque as the fog has turned grey.
     */
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || PaleShaderFog.active()) {
            return;
        }
        double radius = cameraRadius(event.getCamera());
        if (!inFog(radius) || blinded(event.getCamera())) {
            return;
        }
        float alpha = paleness(radius);

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.identity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);

        // In view space: one block in front of the camera and far wider than any field of view.
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        buffer.addVertex(-100f, -100f, -1f).setColor(litColor.x(), litColor.y(), litColor.z(), alpha);
        buffer.addVertex(100f, -100f, -1f).setColor(litColor.x(), litColor.y(), litColor.z(), alpha);
        buffer.addVertex(100f, 100f, -1f).setColor(litColor.x(), litColor.y(), litColor.z(), alpha);
        buffer.addVertex(-100f, 100f, -1f).setColor(litColor.x(), litColor.y(), litColor.z(), alpha);
        BufferUploader.drawWithShader(buffer.buildOrThrow());

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
    }

    /**
     * {@return how far out the camera is, in config radii (how far past the nearest island's coast), pulled back in
     * high above the Pale where the fog clears; or {@link #NOWHERE} if the Pale isn't in this dimension}
     */
    public static double cameraRadius(Camera camera) {
        ClientConfig.Settings config = ClientConfig.get();
        ClientLevel level = Minecraft.getInstance().level;
        if (config == null || level == null || !config.appliesTo(level)) {
            return NOWHERE;
        }
        Vec3 pos = camera.getPosition();
        double radius = PaleShape.client().effectiveRadius(pos.x, pos.z);
        // High above the Pale the fog clears: as if back at the start of the approach, by the time the air is clean.
        double clear = config.innerRadius() - APPROACH;
        return radius > clear ? clear + (radius - clear) * PaleAltitude.strength(pos.y) : radius;
    }

    /** Whether the Pale fog has started at this camera radius (it starts {@link #APPROACH} blocks before innerRadius). */
    public static boolean inFog(double radius) {
        ClientConfig.Settings config = ClientConfig.get();
        return config != null && radius != NOWHERE && radius > config.innerRadius() - APPROACH;
    }

    /**
     * {@return how far the camera fog reaches, in blocks, at this camera radius}: easing from {@code normalVisibility}
     * (what you'd see without the Pale) to fogEntryVisibility over the approach to innerRadius, then thinning by the
     * same factor every block out to fogFullVisibility at the outer radius.
     */
    public static float visibility(double radius, float normalVisibility) {
        ClientConfig.Settings config = ClientConfig.get();
        if (config == null) {
            return normalVisibility;
        }
        float entry = (float) config.fogEntryVisibility();
        float full = (float) config.fogFullVisibility();
        if (radius < config.innerRadius()) {
            float from = Math.max(normalVisibility, entry);
            return from * (float) Math.pow(entry / from, approach(radius));
        }
        double progress = Math.min(1.0, (radius - config.innerRadius()) / (config.outerRadius() - config.innerRadius()));
        return entry * (float) Math.pow(full / entry, progress);
    }

    /** {@return 0 to 1 over the approach to innerRadius, eased}: how far the Pale has taken over from normal settings. */
    public static float approach(double radius) {
        ClientConfig.Settings config = ClientConfig.get();
        if (config == null || radius == NOWHERE) {
            return 0;
        }
        return smoothstep((float) Mth.clamp((radius - (config.innerRadius() - APPROACH)) / APPROACH, 0, 1));
    }

    /** {@return how far the Pale grey has replaced the normal fog and sky colour at this camera radius, 0 to 1} */
    public static float paleness(double radius) {
        ClientConfig.Settings config = ClientConfig.get();
        if (config == null || radius == NOWHERE) {
            return 0;
        }
        return smoothstep((float) Mth.clamp((radius - (config.innerRadius() - APPROACH)) / (APPROACH + GREY_BY), 0, 1));
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }
}
