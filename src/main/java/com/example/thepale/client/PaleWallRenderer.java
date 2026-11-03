package com.example.thepale.client;

import com.example.thepale.ThePale;
import com.example.thepale.config.ClientConfig;
import com.example.thepale.worldgen.PaleShape;
import com.mojang.blaze3d.shaders.AbstractUniform;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RegisterShadersEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4fStack;
import org.slf4j.Logger;

/**
 * Draws the Pale fog wall: {@link ClientConfig.Settings#layerCount()} concentric translucent cylinders, using the
 * {@code thepale:pale_wall} core shader, which ignores terrain fog and supplies the drifting noise. With a shader pack
 * on, the wall is drawn through the game's particle shader instead, which the pack knows how to draw.
 */
public final class PaleWallRenderer {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The Pale grey, shared with the camera fog so the wall and the fog read as the same stuff. */
    public static final float RED = 0.745f;
    public static final float GREEN = 0.745f;
    public static final float BLUE = 0.765f;

    private static final float BOTTOM_Y = 40f;
    private static final float TOP_Y = 320f;
    private static final float INNER_ALPHA = 0.15f;
    private static final float OUTER_ALPHA = 0.6f;

    /**
     * The wall is drawn out to nearly the far clip plane (GameRenderer#getDepthFar: four times the render distance),
     * at most this far. Past it Distant Horizons draws it (PaleCloudWall).
     */
    private static final double MAX_FADE_END = 1024;
    /** Where the wall starts to thin with distance, as a share of how far it reaches. */
    private static final double FADE_START_SHARE = 0.57;
    /** Arc length of one wall segment. The cylinders are faceted at this resolution. */
    private static final double SEGMENT_LENGTH = 16;

    /** Noise drift, in noise cells per second (cell sizes are in pale_wall.fsh: 48 and 16 blocks, so ~0.5 blocks/s). */
    private static final Vec3 BROAD_DRIFT = new Vec3(0.010, 0.004, 0.007);
    private static final Vec3 FINE_DRIFT = new Vec3(-0.030, 0.012, 0.022);
    /** The noise repeats every this many cells (NOISE_PERIOD in pale_wall.fsh), so drift can wrap without a jump. */
    private static final double NOISE_PERIOD = 256;

    // The shader-pack wall (drawForShaderPack), with the same fades as pale_wall.fsh.
    /** Heights (as fractions of the wall) of its rows: closer together low down, where the sea fade is. */
    private static final float[] ROWS = {0f, 0.03f, 0.082f, 0.14f, 0.2f, 0.28f, 0.4f, 0.55f, 0.7f, 0.85f, 1f};
    /** The cloud texture repeats every this many blocks. */
    private static final double CLOUD_TILE = 384;
    /** Texture repeats per second: about half a block a second, like the shader's drift. */
    private static final double CLOUD_DRIFT_U = 0.5 / CLOUD_TILE;
    private static final double CLOUD_DRIFT_V = 0.15 / CLOUD_TILE;
    /** The shader thickens the clouds up to almost twice the layer opacity; the texture can only thin them. */
    private static final float DENSITY_BOOST = 1.5f;
    private static final double SEA_LEVEL = 63;
    private static final double WATER_FADE_HEIGHT = 40;
    private static final float WATER_MIN_ALPHA = 0.2f;
    private static final double NEAR_FADE_START = 6;
    private static final double NEAR_FADE_END = 160;
    private static final double INSIDE_NEAR_FADE_END = 20;
    private static final double GRAZING_START = 0.02;
    private static final double GRAZING_END = 0.7;
    /** How far out, as a fraction of the render distance, the shader-pack wall is drawn at most (clamped to the next two). */
    private static final double PULL_IN_FRACTION = 0.6;
    private static final double PULL_IN_MIN = 48;
    private static final double PULL_IN_MAX = 256;
    /** Per-column scratch space for one layer's arc, grown as needed (render thread only). */
    private static double[] columnX = new double[0];
    private static double[] columnZ = new double[0];
    private static float[] columnFade = new float[0];

    @Nullable
    private static ShaderInstance shader;

    public static void onRegisterShaders(RegisterShadersEvent event) {
        // The old instance is closed by this reload, so never keep it around if the new one fails.
        shader = null;
        try {
            event.registerShader(
                    new ShaderInstance(
                            event.getResourceProvider(),
                            ResourceLocation.fromNamespaceAndPath(ThePale.MOD_ID, "pale_wall"),
                            DefaultVertexFormat.POSITION_TEX_COLOR),
                    loaded -> shader = loaded);
        } catch (IOException e) {
            LOGGER.error("Could not load the Pale wall shader; the wall will not be drawn", e);
        }
    }

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        ShaderInstance wallShader = shader;
        ClientConfig.Settings config = ClientConfig.get();
        Minecraft mc = Minecraft.getInstance();
        boolean shaderPack = PaleShaderFog.active();
        if ((wallShader == null && !shaderPack) || config == null || mc.level == null || !config.appliesTo(mc.level)) {
            return;
        }

        Vec3 camera = event.getCamera().getPosition();
        // A wall around every island, following its coast. Vertices are emitted relative to the camera.
        PaleShape shape = PaleShape.client();
        // How far into the wall the camera is, in config radii: 0 to 1 over the 60 blocks past innerRadius.
        float inside = smoothstep((float) Mth.clamp((shape.effectiveRadius(camera.x, camera.z) - config.innerRadius()) / 60.0, 0, 1));

        double fadeEnd = fadeEnd();
        double fadeStart = fadeEnd * FADE_START_SHARE;

        if (shaderPack) {
            drawForShaderPack(event, config, shape, camera, inside, fadeStart, fadeEnd);
            return;
        }

        float bottom = (float) (BOTTOM_Y - camera.y);
        float top = (float) (TOP_Y - camera.y);
        int layers = config.layerCount();
        // The wall's own shader has no lighting, so give it the grey as lit right now (dark at night).
        float red = PaleFog.litColor().x();
        float green = PaleFog.litColor().y();
        float blue = PaleFog.litColor().z();

        wallShader.safeGetUniform("PaleCameraOffset").set((float) camera.x, (float) camera.y, (float) camera.z);
        wallShader.safeGetUniform("PaleFadeRange").set((float) fadeStart, (float) fadeEnd);
        wallShader.safeGetUniform("PaleInside").set(inside);
        double seconds = Util.getMillis() / 1000.0;
        setDrift(wallShader.safeGetUniform("PaleDriftBroad"), BROAD_DRIFT, seconds);
        setDrift(wallShader.safeGetUniform("PaleDriftFine"), FINE_DRIFT, seconds);

        // Every layer has the same colour, so alpha blending gives the same result in any order and the layers
        // need no sorting. No depth writes, so they don't hide each other or anything translucent drawn later.
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        // This stage hands out an identity pose stack; the camera rotation is only in getModelViewMatrix().
        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();

        for (PaleShape.Island island : shape.islands()) {
            double camX = camera.x - island.centerX();
            double camZ = camera.z - island.centerZ();
            double camDistance = Math.sqrt(camX * camX + camZ * camZ);
            double camAngle = Math.atan2(camZ, camX);
            BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int layer = 0; layer < layers; layer++) {
                double radius = config.layerRadius(layer);
                // Only the arc within fade range of the camera; the rest would be invisible anyway.
                double halfArc = visibleHalfArc(island.nearest(radius, shape.landEnd()), island.reach(radius, shape.landEnd()), camDistance, fadeEnd);
                if (halfArc <= 0) {
                    continue;
                }
                int segmentsAround = Math.max(64, Mth.ceil(2 * Math.PI * island.reach(radius, shape.landEnd()) / SEGMENT_LENGTH));
                double step = 2 * Math.PI / segmentsAround;
                // Snap to a fixed angular grid so the facets don't slide around as the camera moves.
                int first = Mth.floor((camAngle - halfArc) / step);
                int last = Math.min(Mth.ceil((camAngle + halfArc) / step), first + segmentsAround);
                float alpha = Mth.lerp(layers == 1 ? 1f : (float) layer / (layers - 1), INNER_ALPHA, OUTER_ALPHA);

                // UV carries (height fraction, layer index) for the shader rather than texture coordinates.
                double r0 = island.distanceAt(radius, first * step, shape.landEnd());
                float x0 = (float) (Math.cos(first * step) * r0 - camX);
                float z0 = (float) (Math.sin(first * step) * r0 - camZ);
                for (int segment = first; segment < last; segment++) {
                    double angle = (segment + 1) * step;
                    double r1 = island.distanceAt(radius, angle, shape.landEnd());
                    float x1 = (float) (Math.cos(angle) * r1 - camX);
                    float z1 = (float) (Math.sin(angle) * r1 - camZ);
                    buffer.addVertex(x0, bottom, z0).setUv(0f, layer).setColor(red, green, blue, alpha);
                    buffer.addVertex(x0, top, z0).setUv(1f, layer).setColor(red, green, blue, alpha);
                    buffer.addVertex(x1, top, z1).setUv(1f, layer).setColor(red, green, blue, alpha);
                    buffer.addVertex(x1, bottom, z1).setUv(0f, layer).setColor(red, green, blue, alpha);
                    x0 = x1;
                    z0 = z1;
                }
            }
            MeshData mesh = buffer.build();
            if (mesh != null) {
                // The shader works out which way the wall faces from the island's centre.
                wallShader.safeGetUniform("PaleWallCenter").set((float) island.centerX(), (float) island.centerZ());
                RenderSystem.setShader(() -> wallShader);
                BufferUploader.drawWithShader(mesh);
            }
        }

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }

    /**
     * The wall for shader packs. They only draw the game's own shaders properly, so the wall goes through the particle
     * shader (the pack lights and fogs it like its particles) with {@link PaleNoiseTexture} for the clouds, and the
     * fades that pale_wall.fsh does per pixel are worked out per vertex here, on a wall split into rows.
     */
    private static void drawForShaderPack(
            RenderLevelStageEvent event, ClientConfig.Settings config, PaleShape shape, Vec3 camera, float inside,
            double fadeStart, double fadeEnd) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation texture = PaleNoiseTexture.get();
        double seconds = Util.getMillis() / 1000.0;
        // Outside the Pale the sheets stay faded near the player (hides where they cut the water); inside, the camera
        // fog is grey already, so the clouds come right up to the player.
        double nearEnd = Mth.lerp(inside, NEAR_FADE_END, INSIDE_NEAR_FADE_END);
        int light = LightTexture.pack(0, 15);
        // Shader packs fog everything out toward the render distance (Complementary's border fog), which would hide
        // the wall from outside. Each vertex is pulled in along its view ray to well inside that: it covers the same
        // part of the screen, just at a nearer depth.
        double pullTo = Mth.clamp(mc.options.getEffectiveRenderDistance() * 16 * PULL_IN_FRACTION, PULL_IN_MIN, PULL_IN_MAX);

        int layers = config.layerCount();
        BufferBuilder buffer = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        for (PaleShape.Island island : shape.islands()) {
            double camDistance = Math.sqrt(Mth.square(camera.x - island.centerX()) + Mth.square(camera.z - island.centerZ()));
            double camAngle = Math.atan2(camera.z - island.centerZ(), camera.x - island.centerX());
            for (int layer = 0; layer < layers; layer++) {
                double radius = config.layerRadius(layer);
                double halfArc = visibleHalfArc(island.nearest(radius, shape.landEnd()), island.reach(radius, shape.landEnd()), camDistance, fadeEnd);
                if (halfArc <= 0) {
                    continue;
                }
                int segmentsAround = Math.max(64, Mth.ceil(2 * Math.PI * island.reach(radius, shape.landEnd()) / SEGMENT_LENGTH));
                double step = 2 * Math.PI / segmentsAround;
                int first = Mth.floor((camAngle - halfArc) / step);
                int last = Math.min(Mth.ceil((camAngle + halfArc) / step), first + segmentsAround);
                float layerAlpha = Math.min(1f, DENSITY_BOOST * Mth.lerp(layers == 1 ? 1f : (float) layer / (layers - 1), INNER_ALPHA, OUTER_ALPHA));
                // A whole number of texture repeats around the circle, so the clouds line up where the angle wraps.
                double tilesAround = Math.max(1, Math.round(2 * Math.PI * radius / CLOUD_TILE));
                // Each layer drifts its own way, so the layers slide past each other like the shader's do.
                double direction = layer % 2 == 0 ? 1 : -0.7;
                float uOffset = (float) Mth.frac(layer * 0.618 + seconds * CLOUD_DRIFT_U * direction);
                float vOffset = (float) Mth.frac(layer * 0.382 + seconds * CLOUD_DRIFT_V);

                if (columnX.length < last - first + 1) {
                    columnX = new double[last - first + 1];
                    columnZ = new double[last - first + 1];
                    columnFade = new float[last - first + 1];
                }
                for (int segment = first; segment <= last; segment++) {
                    double angle = segment * step;
                    double shaped = island.distanceAt(radius, angle, shape.landEnd());
                    columnX[segment - first] = island.centerX() + Math.cos(angle) * shaped - camera.x;
                    columnZ[segment - first] = island.centerZ() + Math.sin(angle) * shaped - camera.z;
                    columnFade[segment - first] = fadeAt(angle, columnX[segment - first], columnZ[segment - first], fadeStart, fadeEnd);
                }
                for (int segment = first; segment < last; segment++) {
                    int i = segment - first;
                    if (columnFade[i] <= 0 && columnFade[i + 1] <= 0) {
                        continue;
                    }
                    float u0 = (float) (segment / (double) segmentsAround * tilesAround) + uOffset;
                    float u1 = (float) ((segment + 1) / (double) segmentsAround * tilesAround) + uOffset;
                    for (int row = 0; row < ROWS.length - 1; row++) {
                        float a00 = vertexAlpha(layerAlpha, columnFade[i], ROWS[row], columnX[i], columnZ[i], camera.y, nearEnd);
                        float a01 = vertexAlpha(layerAlpha, columnFade[i], ROWS[row + 1], columnX[i], columnZ[i], camera.y, nearEnd);
                        float a11 = vertexAlpha(layerAlpha, columnFade[i + 1], ROWS[row + 1], columnX[i + 1], columnZ[i + 1], camera.y, nearEnd);
                        float a10 = vertexAlpha(layerAlpha, columnFade[i + 1], ROWS[row], columnX[i + 1], columnZ[i + 1], camera.y, nearEnd);
                        if (a00 + a01 + a11 + a10 < 0.004f) {
                            continue;
                        }
                        float y0 = Mth.lerp(ROWS[row], BOTTOM_Y, TOP_Y);
                        float y1 = Mth.lerp(ROWS[row + 1], BOTTOM_Y, TOP_Y);
                        float v0 = (float) (y0 / CLOUD_TILE) + vOffset;
                        float v1 = (float) (y1 / CLOUD_TILE) + vOffset;
                        double ry0 = y0 - camera.y;
                        double ry1 = y1 - camera.y;
                        addPulledIn(buffer, columnX[i], ry0, columnZ[i], pullTo, u0, v0, a00, light);
                        addPulledIn(buffer, columnX[i], ry1, columnZ[i], pullTo, u0, v1, a01, light);
                        addPulledIn(buffer, columnX[i + 1], ry1, columnZ[i + 1], pullTo, u1, v1, a11, light);
                        addPulledIn(buffer, columnX[i + 1], ry0, columnZ[i + 1], pullTo, u1, v0, a10, light);
                    }
                }
            }
        }
        MeshData mesh = buffer.build();
        if (mesh == null) {
            return;
        }

        RenderSystem.setShader(GameRenderer::getParticleShader);
        RenderSystem.setShaderTexture(0, texture);
        mc.gameRenderer.lightTexture().turnOnLightLayer();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Matrix4fStack modelView = RenderSystem.getModelViewStack();
        modelView.pushMatrix();
        modelView.set(event.getModelViewMatrix());
        RenderSystem.applyModelViewMatrix();

        BufferUploader.drawWithShader(mesh);

        modelView.popMatrix();
        RenderSystem.applyModelViewMatrix();
        mc.gameRenderer.lightTexture().turnOffLightLayer();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    /** Adds a camera-relative vertex, moved toward the camera along its view ray if it's further than {@code pullTo}. */
    private static void addPulledIn(
            BufferBuilder buffer, double x, double y, double z, double pullTo, float u, float v, float alpha, int light) {
        double distance = Math.sqrt(x * x + y * y + z * z);
        double scale = distance > pullTo ? pullTo / distance : 1;
        buffer.addVertex((float) (x * scale), (float) (y * scale), (float) (z * scale))
                .setUv(u, v)
                .setColor(RED, GREEN, BLUE, alpha)
                .setLight(light);
    }

    /** The fades that only depend on where a wall column is: distance from the camera, and how side-on it's seen. */
    private static float fadeAt(double angle, double x, double z, double fadeStart, double fadeEnd) {
        double distance = Math.sqrt(x * x + z * z);
        float distanceFade = 1f - smoothstep((float) Mth.clamp((distance - fadeStart * 0.4) / (fadeEnd - fadeStart * 0.4), 0, 1));
        // Looking along a sheet from close by shows as a hard vertical edge; fade those parts out (as pale_wall.fsh).
        double facing = distance < 1e-3 ? 1 : Math.abs(Math.cos(angle) * x + Math.sin(angle) * z) / distance;
        float grazingFade = smoothstep((float) Mth.clamp((facing - GRAZING_START) / (GRAZING_END - GRAZING_START), 0, 1));
        return distanceFade * grazingFade;
    }

    private static float vertexAlpha(float layerAlpha, float columnFade, float height, double x, double z, double cameraY, double nearEnd) {
        if (columnFade <= 0) {
            return 0;
        }
        // Thin toward the top and feathered at the very bottom.
        float vertical = (1f - smoothstep(Mth.clamp((height - 0.25f) / 0.75f, 0, 1))) * smoothstep(Mth.clamp(height / 0.03f, 0, 1));
        double y = Mth.lerp(height, BOTTOM_Y, TOP_Y);
        if (y < SEA_LEVEL && cameraY > SEA_LEVEL) {
            return 0; // under the sea, which hides it; pulled in (addPulledIn) it would show in front of the water instead
        }
        // Thin right above the sea, where each sheet would cut the water in a straight line.
        float waterFade = Mth.lerp(smoothstep((float) Mth.clamp((y - SEA_LEVEL) / WATER_FADE_HEIGHT, 0, 1)), WATER_MIN_ALPHA, 1f);
        double dy = y - cameraY;
        double distance = Math.sqrt(x * x + dy * dy + z * z);
        double nearStart = Math.min(NEAR_FADE_START, nearEnd - 1);
        float nearFade = smoothstep((float) Mth.clamp((distance - nearStart) / (nearEnd - nearStart), 0, 1));
        return layerAlpha * columnFade * vertical * waterFade * nearFade;
    }

    private static float smoothstep(float t) {
        return t * t * (3f - 2f * t);
    }

    /** {@return how far from the camera the wall is drawn: almost to the far clip plane, and never past it} */
    public static double fadeEnd() {
        return Math.min(MAX_FADE_END, Minecraft.getInstance().gameRenderer.getDepthFar() * 0.9);
    }

    /**
     * Half-angle (around the island's centre) of the arc of a layer that can lie within {@code range} of a point
     * {@code distance} from the centre, for a layer that stays between {@code inner} and {@code outer} from the centre
     * (it follows the coast): the widest arc of any circle in that band, so generous, never short.
     */
    private static double visibleHalfArc(double inner, double outer, double distance, double range) {
        // The arc of a circle within range is widest where the circle is tangent to the range's edge, or at the band's ends.
        double tangent = Mth.clamp(Math.sqrt(Math.max(0, distance * distance - range * range)), inner, outer);
        return Math.max(visibleHalfArc(tangent, distance, range), Math.max(visibleHalfArc(inner, distance, range), visibleHalfArc(outer, distance, range)));
    }

    /**
     * Half-angle (around the wall centre) of the arc of a circle of {@code radius} that lies within {@code range}
     * of a point {@code distance} from the centre: 0 if none of it does, pi if all of it does.
     */
    private static double visibleHalfArc(double radius, double distance, double range) {
        if (Math.abs(radius - distance) >= range) {
            return 0;
        }
        if (radius + distance <= range) {
            return Math.PI;
        }
        double cos = (radius * radius + distance * distance - range * range) / (2 * radius * distance);
        return Math.acos(Mth.clamp(cos, -1.0, 1.0));
    }

    private static void setDrift(AbstractUniform uniform, Vec3 cellsPerSecond, double seconds) {
        uniform.set(
                (float) Mth.positiveModulo(cellsPerSecond.x * seconds, NOISE_PERIOD),
                (float) Mth.positiveModulo(cellsPerSecond.y * seconds, NOISE_PERIOD),
                (float) Mth.positiveModulo(cellsPerSecond.z * seconds, NOISE_PERIOD));
    }
}
