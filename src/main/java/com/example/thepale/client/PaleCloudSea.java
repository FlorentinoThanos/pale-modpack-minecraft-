package com.example.thepale.client;

import com.example.thepale.config.ClientConfig;
import com.example.thepale.worldgen.PaleShape;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4fStack;
import org.joml.Vector3f;

/**
 * The sea of clouds over the Pale: a deck of drifting cloud sheets above the fog, with the islands as holes in it.
 * Seen from above (on elytra, where the air is clear) it hides the Pale below; from inside or under it the Pale's own
 * fog covers the view, so the deck fades out with that fog. Drawn without terrain fog, out to near the far clip plane.
 */
public final class PaleCloudSea {
    /** Heights of the sheets: from just above the fog wall's top (320) to just under where the air clears. */
    private static final float[] SHEETS = {335f, 360f, 385f, 410f, 435f};
    private static final float SHEET_ALPHA = 0.8f;
    /** How much brighter than the fog grey the deck's tops are. */
    private static final float SUNLIT = 1.27f;
    /** Grid cell size, and the deck's reach from the camera (further only if the far clip plane allows). */
    private static final int CELL = 64;
    private static final double MAX_EXTENT = 1600;
    /** The cloud texture repeats every this many blocks; each sheet is offset and drifts its own way. */
    private static final double TILE = 768;
    private static final double DRIFT = 0.6 / TILE;
    /** A sheet fades out within this many blocks above or below the camera, so flying through it shows no flat cut. */
    private static final double NEAR_SHEET = 30;

    private static float[] mask = new float[0];

    private PaleCloudSea() {}

    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || PaleShaderFog.steam()) {
            return; // with a shader pack the top of the steam is the sea of clouds (PaleSteam)
        }
        Minecraft mc = Minecraft.getInstance();
        ClientConfig.Settings config = ClientConfig.get();
        if (config == null || mc.level == null || !config.appliesTo(mc.level)) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        // Inside or under the deck in the Pale, the fog is the deck: hide it as the fog takes over.
        double cameraRadius = PaleFog.cameraRadius(event.getCamera());
        float visible = (1f - (PaleFog.inFog(cameraRadius) ? PaleFog.paleness(cameraRadius) : 0f)) * com.example.thepale.finale.FinaleView.paleStrength();
        if (visible <= 0.01f) {
            return;
        }
        PaleShape shape = PaleShape.client();
        double extent = Math.min(MAX_EXTENT, mc.gameRenderer.getDepthFar() * 0.9);
        int cells = Mth.ceil(extent / CELL);
        int size = cells * 2 + 1;
        int baseX = Mth.floor(camera.x / CELL) - cells;
        int baseZ = Mth.floor(camera.z / CELL) - cells;

        // How much deck each grid point gets: none over the islands and their walls' inner part, all of it out in the Pale.
        if (mask.length < size * size) {
            mask = new float[size * size];
        }
        boolean any = false;
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                double x = (baseX + i) * (double) CELL;
                double z = (baseZ + j) * (double) CELL;
                double depth = (shape.effectiveRadius(x, z) - config.innerRadius()) / (config.outerRadius() - config.innerRadius());
                double dx = x - camera.x;
                double dz = z - camera.z;
                double edge = 1 - smoothstep(extent * 0.65, extent, Math.sqrt(dx * dx + dz * dz));
                float value = (float) (smoothstep(0, 1, depth) * edge);
                mask[i * size + j] = value;
                any |= value > 0;
            }
        }
        if (!any) {
            return;
        }

        boolean shaderPack = PaleShaderFog.active();
        Vector3f lit = PaleFog.litColor();
        // Cloud tops in the sun: near white by day, as dark as the fog by night.
        float red = Math.min(1f, lit.x() * SUNLIT);
        float green = Math.min(1f, lit.y() * SUNLIT);
        float blue = Math.min(1f, lit.z() * SUNLIT);
        int light = LightTexture.pack(0, 15);
        double pullTo = Mth.clamp(mc.options.getEffectiveRenderDistance() * 16 * 0.6, 48, 256);
        double seconds = Util.getMillis() / 1000.0;

        BufferBuilder buffer = Tesselator.getInstance().begin(
                VertexFormat.Mode.QUADS, shaderPack ? DefaultVertexFormat.PARTICLE : DefaultVertexFormat.POSITION_TEX_COLOR);
        int quads = 0;
        for (int sheet = 0; sheet < SHEETS.length; sheet++) {
            double height = SHEETS[sheet] - camera.y;
            float near = (float) smoothstep(0, NEAR_SHEET, Math.abs(height));
            float alpha = SHEET_ALPHA * near * visible;
            if (alpha <= 0.002f) {
                continue;
            }
            double direction = sheet % 2 == 0 ? 1 : -0.8;
            double uOffset = Mth.frac(sheet * 0.618 + seconds * DRIFT * direction);
            double vOffset = Mth.frac(sheet * 0.382 + seconds * DRIFT * 0.5);
            for (int i = 0; i < size - 1; i++) {
                for (int j = 0; j < size - 1; j++) {
                    float m00 = mask[i * size + j];
                    float m10 = mask[(i + 1) * size + j];
                    float m11 = mask[(i + 1) * size + j + 1];
                    float m01 = mask[i * size + j + 1];
                    if (m00 + m10 + m11 + m01 <= 0) {
                        continue;
                    }
                    int x0 = (baseX + i) * CELL;
                    int z0 = (baseZ + j) * CELL;
                    vertex(buffer, shaderPack, camera, x0, z0, height, uOffset, vOffset, red, green, blue, alpha * m00, light, pullTo);
                    vertex(buffer, shaderPack, camera, x0, z0 + CELL, height, uOffset, vOffset, red, green, blue, alpha * m01, light, pullTo);
                    vertex(buffer, shaderPack, camera, x0 + CELL, z0 + CELL, height, uOffset, vOffset, red, green, blue, alpha * m11, light, pullTo);
                    vertex(buffer, shaderPack, camera, x0 + CELL, z0, height, uOffset, vOffset, red, green, blue, alpha * m10, light, pullTo);
                    quads++;
                }
            }
        }
        MeshData mesh = buffer.build();
        if (mesh == null || quads == 0) {
            return;
        }

        if (shaderPack) {
            // Shader packs only draw the game's own shaders properly: the particle shader, lit and fogged like particles.
            RenderSystem.setShader(GameRenderer::getParticleShader);
            mc.gameRenderer.lightTexture().turnOnLightLayer();
        } else {
            // The plain textured shader: no terrain fog, so the deck reaches the horizon.
            RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        }
        RenderSystem.setShaderTexture(0, PaleNoiseTexture.get());
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
        if (shaderPack) {
            mc.gameRenderer.lightTexture().turnOffLightLayer();
        }
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.disableBlend();
    }

    private static void vertex(
            BufferBuilder buffer, boolean shaderPack, Vec3 camera, int x, int z, double height, double uOffset, double vOffset,
            float red, float green, float blue, float alpha, int light, double pullTo) {
        double rx = x - camera.x;
        double rz = z - camera.z;
        // Texture from world coordinates, so the clouds stay put as the camera moves; counted from a whole number of
        // tiles near the camera (the texture repeats every tile, so where that base moves nothing shows).
        float u = (float) ((x - Math.floor(camera.x / TILE) * TILE) / TILE + uOffset);
        float v = (float) ((z - Math.floor(camera.z / TILE) * TILE) / TILE + vOffset);
        if (shaderPack) {
            // Shader packs fog everything out toward the render distance: pull each vertex in along its view ray.
            double distance = Math.sqrt(rx * rx + height * height + rz * rz);
            double scale = distance > pullTo ? pullTo / distance : 1;
            buffer.addVertex((float) (rx * scale), (float) (height * scale), (float) (rz * scale))
                    .setUv(u, v).setColor(red, green, blue, alpha).setLight(light);
        } else {
            buffer.addVertex((float) rx, (float) height, (float) rz).setUv(u, v).setColor(red, green, blue, alpha);
        }
    }

    private static double smoothstep(double edge0, double edge1, double x) {
        double t = Mth.clamp((x - edge0) / (edge1 - edge0), 0.0, 1.0);
        return t * t * (3.0 - 2.0 * t);
    }
}
