package com.example.thepale.client;

import com.example.thepale.ThePale;
import com.example.thepale.config.ClientConfig;
import com.example.thepale.worldgen.PaleAltitude;
import com.example.thepale.worldgen.PaleShape;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.logging.LogUtils;
import java.io.IOException;
import java.io.Reader;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.apache.commons.io.IOUtils;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL14;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL21;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GL33;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;
import org.slf4j.Logger;

/**
 * The Pale as soft steam, like the Basalt Deltas' haze, for when a shader pack is on. Shader packs draw the world
 * their own way and ignore the game's fog, and anything drawn into the world for them (the old layered wall) comes out
 * as hard sheets. So the steam is worked out on its own: once the level is drawn, each pixel's view ray is marched
 * through the fog up to whatever the pixel shows (shader {@code steam.fsh}), into a texture of its own; and that is
 * laid over the pack's finished frame before the HUD ({@link #composite}).
 *
 * <p>Everything here is plain OpenGL rather than the game's shader system, which shader packs take over while the
 * level is drawn, and every bit of GL state it changes is put back as it was.
 */
public final class PaleSteam {
    private static final Logger LOGGER = LogUtils.getLogger();

    // Texture units, above the 12 the game keeps track of.
    private static final int DEPTH_UNIT = 12;
    private static final int DH_DEPTH_UNIT = 13;
    private static final int FIELD_UNIT = 14;
    private static final int NOISE_UNIT = 15;
    /** For uploads. */
    static final int SCRATCH_UNIT = 15;
    private static final int[] UNITS = {DEPTH_UNIT, DH_DEPTH_UNIT, FIELD_UNIT, NOISE_UNIT};
    private static final int[] COMPOSITE_UNITS = {DEPTH_UNIT};

    /** Top of the sea: water fills up to y = 62 and its surface sits a little below the block top. */
    private static final float SEA_SURFACE_Y = 62.9f;
    /** Optical depth over one visibility (how far the camera fog says you can see): about 92% fogged there. */
    private static final float DENSITY = 2.5f;
    /** The darkest the steam gets, at night: about the moonlit sea in Complementary. */
    private static final float MOONLIT_RED = 0.11f;
    private static final float MOONLIT_GREEN = 0.12f;
    private static final float MOONLIT_BLUE = 0.16f;
    /** How far the bank's ragged front pushes in and out, in blocks. */
    private static final float EDGE = 80f;
    /** Noise drift, in blocks a second: the billows roll along and rise, the curls go their own way. */
    private static final Vec3 BROAD_DRIFT = new Vec3(0.55, 0.32, 0.38);
    private static final Vec3 FINE_DRIFT = new Vec3(0.95, 0.55, -0.7);
    /** Noise cell sizes, as in steam.fsh; the noise repeats every 256 cells. */
    private static final double BROAD_CELL = 88;
    private static final double FINE_CELL = 29;
    private static final int NOISE_SIZE = 256;

    private static int steamProgram;
    private static int compositeProgram;
    private static int vertexArray;
    private static int noiseTexture;
    private static int framebuffer;
    private static int steamTexture;
    private static int width;
    private static int height;
    /** The shaders didn't compile or something else went wrong: the old way is used instead. */
    private static boolean failed;
    /** Whether the player has been told that the steam couldn't start. */
    private static boolean toldPlayer;
    /** Whether {@link #steamTexture} holds this frame's steam. */
    private static boolean rendered;
    private static boolean loggedFirst;
    private static boolean usedDhDepth;

    // Uniform locations in steamProgram.
    private static int uInvViewProj;
    private static int uInvDhViewProj;
    private static int uUseDh;
    private static int uScreenSize;
    private static int uCameraY;
    private static int uFieldOrigin;
    private static int uFieldScale;
    private static int uBroadOffset;
    private static int uFineOffset;
    private static int uRadii;
    private static int uVisibility;
    private static int uHeights;
    private static int uFogColor;
    private static int uFarColor;

    private static final Snapshot SNAPSHOT = new Snapshot();
    private static final FloatBuffer MATRIX = MemoryUtil.memAllocFloat(16);

    private PaleSteam() {}

    /** Whether the steam is in charge of the Pale with a shader pack on (it works on this machine). */
    public static boolean usable() {
        return !failed;
    }

    /** The resources were reloaded (maybe a new resource pack): build the shaders again on next use. */
    static void onResourcesReloaded() {
        deletePrograms();
        failed = false;
        toldPlayer = false;
        loggedFirst = false;
    }

    /**
     * Gives up on the steam until the resources are reloaded (the reason is logged), and tells the player once in
     * chat, so the old wall isn't taken for the steam.
     */
    private static void giveUp() {
        failed = true;
        LocalPlayer player = Minecraft.getInstance().player;
        if (!toldPlayer && player != null) {
            toldPlayer = true;
            player.displayClientMessage(Component.translatable("message.thepale.steam_failed").withStyle(ChatFormatting.GRAY), false);
        }
    }

    /**
     * Works out this frame's steam, once the level is drawn (and before the shader pack's own passes finish the frame).
     */
    static void render(RenderLevelStageEvent event, float normalRed, float normalGreen, float normalBlue) {
        rendered = false;
        Minecraft mc = Minecraft.getInstance();
        ClientConfig.Settings config = ClientConfig.get();
        if (failed || config == null || mc.level == null || !config.appliesTo(mc.level)) {
            return;
        }
        Camera camera = event.getCamera();
        // Under water, lava or powder snow, or blinded, the shader pack's own thick fog is the right one.
        if (camera.getFluidInCamera() != FogType.NONE || PaleFog.blinded(camera)) {
            return;
        }
        try {
            draw(event, mc, config, camera, normalRed, normalGreen, normalBlue);
        } catch (RuntimeException | IOException e) {
            LOGGER.error("[thepale] Could not draw the Pale steam; falling back to the plain fog over shader packs", e);
            giveUp();
        }
    }

    private static void draw(
            RenderLevelStageEvent event, Minecraft mc, ClientConfig.Settings config, Camera camera,
            float normalRed, float normalGreen, float normalBlue) throws IOException {
        Vec3 pos = camera.getPosition();
        double fogStart = config.innerRadius() - PaleFog.APPROACH;
        // The shader jumps across anything further than this from the fog's start (the steam's front billows out to EDGE / 2).
        if (!PaleSteamField.prepare(pos.x, pos.z, PaleShape.client(), fogStart, EDGE / 2 + 8)) {
            return;
        }
        if (steamProgram == 0 && !buildPrograms()) {
            return;
        }
        RenderTarget main = mc.getMainRenderTarget();
        if (vertexArray == 0) {
            vertexArray = GL30.glGenVertexArrays();
        }
        if (noiseTexture == 0) {
            noiseTexture = makeNoiseTexture();
        }

        Matrix4f viewProj = new Matrix4f(event.getProjectionMatrix()).mul(event.getModelViewMatrix());
        Matrix4f inverse = new Matrix4f(viewProj).invert();
        int dhDepth = DhDepth.texture();
        Matrix4f dhInverse = dhDepth == 0 ? inverse : DhDepth.projection().mul(event.getModelViewMatrix()).invert();

        SNAPSHOT.save(UNITS);
        try {
            if (!ensureTarget(main.width, main.height)) {
                return;
            }
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
            GL11.glViewport(0, 0, width, height);
            plainRasterState();
            GL20.glUseProgram(steamProgram);
            GL30.glBindVertexArray(vertexArray);
            bindTexture(DEPTH_UNIT, main.getDepthTextureId());
            bindTexture(DH_DEPTH_UNIT, dhDepth == 0 ? main.getDepthTextureId() : dhDepth);
            bindTexture(FIELD_UNIT, PaleSteamField.texture());
            bindTexture(NOISE_UNIT, noiseTexture);

            setMatrix(uInvViewProj, inverse);
            setMatrix(uInvDhViewProj, dhInverse);
            GL20.glUniform1i(uUseDh, dhDepth == 0 ? 0 : 1);
            GL20.glUniform2f(uScreenSize, width, height);
            GL20.glUniform1f(uCameraY, (float) pos.y);
            GL20.glUniform2f(uFieldOrigin, (float) (pos.x - PaleSteamField.originX()), (float) (pos.z - PaleSteamField.originZ()));
            GL20.glUniform2f(uFieldScale, 1f / (PaleSteamField.SIZE * PaleSteamField.CELL), PaleSteamField.CELL);
            double seconds = Util.getMillis() / 1000.0;
            setNoiseOffset(uBroadOffset, pos, BROAD_DRIFT, seconds, BROAD_CELL);
            setNoiseOffset(uFineOffset, pos, FINE_DRIFT, seconds, FINE_CELL);
            GL20.glUniform4f(uRadii, (float) PaleFog.APPROACH, (float) (config.innerRadius() - fogStart),
                    (float) Math.max(1, config.outerRadius() - config.innerRadius()), EDGE);
            GL20.glUniform4f(uVisibility, mc.options.getEffectiveRenderDistance() * 16f,
                    (float) config.fogEntryVisibility(), (float) config.fogFullVisibility(), DENSITY * com.example.thepale.finale.FinaleView.paleStrength());
            GL20.glUniform3f(uHeights, SEA_SURFACE_Y, (float) PaleAltitude.CLEARS_FROM, (float) PaleAltitude.CLEAR_AT);
            // The Pale grey as the camera fog has it, but never darker than moonlit mist: shader packs light the
            // night, and the steam would stand against it as a black wall.
            Vector3f lit = PaleFog.litColor();
            GL20.glUniform3f(uFogColor, Math.max(lit.x(), MOONLIT_RED), Math.max(lit.y(), MOONLIT_GREEN), Math.max(lit.z(), MOONLIT_BLUE));
            GL20.glUniform3f(uFarColor, normalRed, normalGreen, normalBlue);

            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            rendered = true;
        } finally {
            SNAPSHOT.restore();
        }
        if (!loggedFirst) {
            loggedFirst = true;
            LOGGER.info("[thepale] Drawing the Pale as steam over the shader pack ({}x{})", width, height);
        }
        if ((dhDepth != 0) != usedDhDepth) {
            usedDhDepth = dhDepth != 0;
            LOGGER.info("[thepale] The steam {} Distant Horizons' depth", usedDhDepth ? "now uses" : "no longer uses");
        }
    }

    /**
     * Lays this frame's steam over the finished frame, before the HUD.
     *
     * @return whether there was steam to lay over
     */
    static boolean composite() {
        if (!rendered) {
            return false;
        }
        rendered = false;
        SNAPSHOT.save(COMPOSITE_UNITS);
        try {
            // Only straight onto a frame the size the steam was made for.
            if (SNAPSHOT.viewport[2] != width || SNAPSHOT.viewport[3] != height || SNAPSHOT.viewport[0] != 0 || SNAPSHOT.viewport[1] != 0) {
                return false;
            }
            plainRasterState();
            GL11.glEnable(GL11.GL_BLEND);
            GL20.glBlendEquationSeparate(GL14.GL_FUNC_ADD, GL14.GL_FUNC_ADD);
            // Premultiplied; the frame's own alpha is left alone.
            GL14.glBlendFuncSeparate(GL11.GL_ONE, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ZERO, GL11.GL_ONE);
            GL20.glUseProgram(compositeProgram);
            GL30.glBindVertexArray(vertexArray);
            bindTexture(DEPTH_UNIT, steamTexture);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 3);
            return true;
        } finally {
            SNAPSHOT.restore();
        }
    }

    /** Forgets this frame's steam (the fog isn't needed after all, e.g. the shader pack was just turned off). */
    static void discardFrame() {
        rendered = false;
    }

    /** No blending, depth, culling, scissor or stencil, and all colour channels written. */
    private static void plainRasterState() {
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glDepthMask(false);
        GL11.glDisable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
        GL11.glDisable(GL11.GL_STENCIL_TEST);
        GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
        GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
        GL11.glColorMask(true, true, true, true);
    }

    private static void bindTexture(int unit, int texture) {
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + unit);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
        // Sampler objects (shader packs use them) would override the texture's own filtering and wrapping.
        GL33.glBindSampler(unit, 0);
    }

    private static void setMatrix(int location, Matrix4f matrix) {
        matrix.get(MATRIX);
        GL20.glUniformMatrix4fv(location, false, MATRIX);
    }

    /** The camera's position moved by the drift, wrapped to the noise's period so it stays precise in a float. */
    private static void setNoiseOffset(int location, Vec3 camera, Vec3 drift, double seconds, double cell) {
        double period = NOISE_SIZE * cell;
        GL20.glUniform3f(location,
                (float) Mth.positiveModulo(camera.x - drift.x * seconds, period),
                (float) Mth.positiveModulo(camera.y - drift.y * seconds, period),
                (float) Mth.positiveModulo(camera.z - drift.z * seconds, period));
    }

    /** Makes (or resizes) the texture the steam is drawn into. */
    private static boolean ensureTarget(int newWidth, int newHeight) {
        if (framebuffer != 0 && width == newWidth && height == newHeight) {
            return true;
        }
        if (newWidth <= 0 || newHeight <= 0) {
            return false;
        }
        if (framebuffer == 0) {
            framebuffer = GL30.glGenFramebuffers();
            steamTexture = GL11.glGenTextures();
        }
        width = newWidth;
        height = newHeight;
        GL13.glActiveTexture(GL13.GL_TEXTURE0 + SCRATCH_UNIT);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, steamTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
        withPlainUnpacking(() -> {
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA8, width, height, 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, (ByteBuffer) null);
        });
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, framebuffer);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, steamTexture, 0);
        int status = GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER);
        if (status != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Steam framebuffer incomplete: 0x" + Integer.toHexString(status));
        }
        return true;
    }

    /**
     * Runs an upload with the pixel unpacking reset to plain tightly packed rows; the game leaves its last image's row
     * length and offsets set. Put back afterwards.
     */
    static void withPlainUnpacking(Runnable upload) {
        int rowLength = GL11.glGetInteger(GL12.GL_UNPACK_ROW_LENGTH);
        int skipPixels = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_PIXELS);
        int skipRows = GL11.glGetInteger(GL12.GL_UNPACK_SKIP_ROWS);
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, 0);
        GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, 0);
        GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
        try {
            upload.run();
        } finally {
            GL11.glPixelStorei(GL12.GL_UNPACK_ROW_LENGTH, rowLength);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_PIXELS, skipPixels);
            GL11.glPixelStorei(GL12.GL_UNPACK_SKIP_ROWS, skipRows);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
        }
    }

    /**
     * 256 x 256 of smooth value noise for {@code valueNoise} in steam.fsh: red is the noise, green the same noise 37 and 17
     * texels on, which is where the next slice up starts, so one filtered lookup gives two slices to blend.
     */
    private static int makeNoiseTexture() {
        ByteBuffer pixels = MemoryUtil.memAlloc(NOISE_SIZE * NOISE_SIZE * 2);
        try {
            for (int z = 0; z < NOISE_SIZE; z++) {
                for (int x = 0; x < NOISE_SIZE; x++) {
                    int index = (z * NOISE_SIZE + x) * 2;
                    pixels.put(index, (byte) hash(x, z));
                    pixels.put(index + 1, (byte) hash((x + 37) & (NOISE_SIZE - 1), (z + 17) & (NOISE_SIZE - 1)));
                }
            }
            int previousUnit = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            GL13.glActiveTexture(GL13.GL_TEXTURE0 + SCRATCH_UNIT);
            int previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            int previousUnpack = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
            int texture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL11.GL_REPEAT);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL12.GL_TEXTURE_MAX_LEVEL, 0);
            withPlainUnpacking(() -> {
                GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
                GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RG8, NOISE_SIZE, NOISE_SIZE, 0, GL30.GL_RG, GL11.GL_UNSIGNED_BYTE, pixels);
            });
            GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, previousUnpack);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousTexture);
            GL13.glActiveTexture(previousUnit);
            return texture;
        } finally {
            MemoryUtil.memFree(pixels);
        }
    }

    /** Fixed pseudo-random 0 to 255 for a texel. */
    private static int hash(int x, int z) {
        int h = x * 374761393 + z * 668265263 + 0x5A1E;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return h & 0xFF;
    }

    private static boolean buildPrograms() throws IOException {
        String vertex = source("fullscreen.vsh");
        int steam = link("steam", vertex, source("steam.fsh"));
        int composite = link("composite", vertex, source("composite.fsh"));
        if (steam == 0 || composite == 0) {
            giveUp();
            if (steam != 0) {
                GL20.glDeleteProgram(steam);
            }
            if (composite != 0) {
                GL20.glDeleteProgram(composite);
            }
            return false;
        }
        steamProgram = steam;
        compositeProgram = composite;

        int previousProgram = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        GL20.glUseProgram(steamProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(steamProgram, "SceneDepth"), DEPTH_UNIT);
        GL20.glUniform1i(GL20.glGetUniformLocation(steamProgram, "DhDepth"), DH_DEPTH_UNIT);
        GL20.glUniform1i(GL20.glGetUniformLocation(steamProgram, "Field"), FIELD_UNIT);
        GL20.glUniform1i(GL20.glGetUniformLocation(steamProgram, "Noise"), NOISE_UNIT);
        uInvViewProj = GL20.glGetUniformLocation(steamProgram, "InvViewProj");
        uInvDhViewProj = GL20.glGetUniformLocation(steamProgram, "InvDhViewProj");
        uUseDh = GL20.glGetUniformLocation(steamProgram, "UseDh");
        uScreenSize = GL20.glGetUniformLocation(steamProgram, "ScreenSize");
        uCameraY = GL20.glGetUniformLocation(steamProgram, "CameraY");
        uFieldOrigin = GL20.glGetUniformLocation(steamProgram, "FieldOrigin");
        uFieldScale = GL20.glGetUniformLocation(steamProgram, "FieldScale");
        uBroadOffset = GL20.glGetUniformLocation(steamProgram, "BroadOffset");
        uFineOffset = GL20.glGetUniformLocation(steamProgram, "FineOffset");
        uRadii = GL20.glGetUniformLocation(steamProgram, "Radii");
        uVisibility = GL20.glGetUniformLocation(steamProgram, "Visibility");
        uHeights = GL20.glGetUniformLocation(steamProgram, "Heights");
        uFogColor = GL20.glGetUniformLocation(steamProgram, "FogColor");
        uFarColor = GL20.glGetUniformLocation(steamProgram, "FarColor");
        GL20.glUseProgram(compositeProgram);
        GL20.glUniform1i(GL20.glGetUniformLocation(compositeProgram, "Steam"), DEPTH_UNIT);
        GL20.glUseProgram(previousProgram);
        return true;
    }

    private static String source(String name) throws IOException {
        ResourceLocation location = ThePale.id("shaders/steam/" + name);
        try (Reader reader = Minecraft.getInstance().getResourceManager().getResourceOrThrow(location).openAsReader()) {
            return IOUtils.toString(reader);
        }
    }

    /** {@return the linked program, or 0 (logged) if it didn't compile or link} */
    private static int link(String name, String vertexSource, String fragmentSource) {
        int vertex = compile(name + ".vsh", GL20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compile(name + ".fsh", GL20.GL_FRAGMENT_SHADER, fragmentSource);
        if (vertex == 0 || fragment == 0) {
            GL20.glDeleteShader(vertex);
            GL20.glDeleteShader(fragment);
            return 0;
        }
        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertex);
        GL20.glAttachShader(program, fragment);
        GL20.glLinkProgram(program);
        GL20.glDetachShader(program, vertex);
        GL20.glDetachShader(program, fragment);
        GL20.glDeleteShader(vertex);
        GL20.glDeleteShader(fragment);
        if (GL20.glGetProgrami(program, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            LOGGER.error("[thepale] Could not link the steam shader {}: {}", name, GL20.glGetProgramInfoLog(program).trim());
            GL20.glDeleteProgram(program);
            return 0;
        }
        return program;
    }

    private static int compile(String name, int type, String source) {
        int shader = GL20.glCreateShader(type);
        GL20.glShaderSource(shader, source);
        GL20.glCompileShader(shader);
        if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            LOGGER.error("[thepale] Could not compile the steam shader {}: {}", name, GL20.glGetShaderInfoLog(shader).trim());
            GL20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static void deletePrograms() {
        if (steamProgram != 0) {
            GL20.glDeleteProgram(steamProgram);
            steamProgram = 0;
        }
        if (compositeProgram != 0) {
            GL20.glDeleteProgram(compositeProgram);
            compositeProgram = 0;
        }
        rendered = false;
    }

    /**
     * Distant Horizons' depth buffer, through Iris (by reflection: both are optional), so far-off LOD land in front of
     * the fog isn't fogged as if it were sky.
     */
    private static final class DhDepth {
        private static boolean looked;
        @Nullable
        private static Method pipelineManager;
        @Nullable
        private static Method pipelineNullable;
        @Nullable
        private static Class<?> irisPipeline;
        @Nullable
        private static Method dhCompat;
        @Nullable
        private static Method depthTex;
        @Nullable
        private static Method projection;
        @Nullable
        private static Method renderingEnabled;

        /** {@return the depth texture Distant Horizons drew into this frame, or 0 if there's none to use} */
        static int texture() {
            if (!looked) {
                looked = true;
                if (!ModList.get().isLoaded("distanthorizons")) {
                    return 0;
                }
                try {
                    Class<?> iris = Class.forName("net.irisshaders.iris.Iris");
                    pipelineManager = iris.getMethod("getPipelineManager");
                    pipelineNullable = Class.forName("net.irisshaders.iris.pipeline.PipelineManager").getMethod("getPipelineNullable");
                    irisPipeline = Class.forName("net.irisshaders.iris.pipeline.IrisRenderingPipeline");
                    dhCompat = irisPipeline.getMethod("getDHCompat");
                    Class<?> compat = Class.forName("net.irisshaders.iris.compat.dh.DHCompat");
                    depthTex = compat.getMethod("getDepthTex");
                    projection = compat.getMethod("getProjection");
                    renderingEnabled = compat.getMethod("hasRenderingEnabled");
                } catch (ReflectiveOperationException | LinkageError e) {
                    pipelineManager = null;
                    LOGGER.info("[thepale] No Distant Horizons depth from Iris; far LODs are fogged as sky");
                }
            }
            if (pipelineManager == null || pipelineNullable == null || irisPipeline == null || dhCompat == null
                    || depthTex == null || renderingEnabled == null) {
                return 0;
            }
            try {
                if (!(boolean) renderingEnabled.invoke(null)) {
                    return 0;
                }
                Object pipeline = pipelineNullable.invoke(pipelineManager.invoke(null));
                if (!irisPipeline.isInstance(pipeline)) {
                    return 0;
                }
                Object compat = dhCompat.invoke(pipeline);
                if (compat == null) {
                    return 0;
                }
                int texture = (int) depthTex.invoke(compat);
                return texture > 0 && GL11.glIsTexture(texture) ? texture : 0;
            } catch (ReflectiveOperationException | RuntimeException e) {
                LOGGER.warn("[thepale] Could not get Distant Horizons' depth from Iris; far LODs are fogged as sky", e);
                pipelineManager = null;
                return 0;
            }
        }

        /** {@return Distant Horizons' projection as Iris hands it to shader packs} (only after {@link #texture} found one) */
        static Matrix4f projection() {
            try {
                return new Matrix4f((Matrix4f) projection.invoke(null));
            } catch (ReflectiveOperationException | RuntimeException e) {
                throw new IllegalStateException("Could not get Distant Horizons' projection from Iris", e);
            }
        }
    }

    /** The GL state the steam passes change, saved before and put back after. */
    private static final class Snapshot {
        final int[] viewport = new int[4];
        private int drawFramebuffer;
        private int readFramebuffer;
        private int program;
        private int vertexArrayBinding;
        private int activeTexture;
        private int[] units = new int[0];
        private final int[] textures = new int[8];
        private final int[] samplers = new int[8];
        private boolean blend;
        private boolean depthTest;
        private boolean cullFace;
        private boolean scissorTest;
        private boolean stencilTest;
        private boolean logicOp;
        private boolean srgb;
        private boolean depthMask;
        private final boolean[] colorMask = new boolean[4];
        private int blendSrcRgb;
        private int blendDstRgb;
        private int blendSrcAlpha;
        private int blendDstAlpha;
        private int blendEquationRgb;
        private int blendEquationAlpha;

        void save(int[] unitsUsed) {
            units = unitsUsed;
            drawFramebuffer = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
            readFramebuffer = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
            GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
            program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
            vertexArrayBinding = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
            activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
            for (int i = 0; i < units.length; i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + units[i]);
                textures[i] = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                samplers[i] = GL11.glGetInteger(GL33.GL_SAMPLER_BINDING);
            }
            GL13.glActiveTexture(activeTexture);
            blend = GL11.glIsEnabled(GL11.GL_BLEND);
            depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
            cullFace = GL11.glIsEnabled(GL11.GL_CULL_FACE);
            scissorTest = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
            stencilTest = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
            logicOp = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
            srgb = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
            depthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
            try (MemoryStack stack = MemoryStack.stackPush()) {
                ByteBuffer mask = stack.malloc(4);
                GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, mask);
                for (int i = 0; i < 4; i++) {
                    colorMask[i] = mask.get(i) != 0;
                }
            }
            blendSrcRgb = GL11.glGetInteger(GL14.GL_BLEND_SRC_RGB);
            blendDstRgb = GL11.glGetInteger(GL14.GL_BLEND_DST_RGB);
            blendSrcAlpha = GL11.glGetInteger(GL14.GL_BLEND_SRC_ALPHA);
            blendDstAlpha = GL11.glGetInteger(GL14.GL_BLEND_DST_ALPHA);
            blendEquationRgb = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_RGB);
            blendEquationAlpha = GL11.glGetInteger(GL20.GL_BLEND_EQUATION_ALPHA);
        }

        void restore() {
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFramebuffer);
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFramebuffer);
            GL11.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vertexArrayBinding);
            for (int i = 0; i < units.length; i++) {
                GL13.glActiveTexture(GL13.GL_TEXTURE0 + units[i]);
                GL11.glBindTexture(GL11.GL_TEXTURE_2D, textures[i]);
                GL33.glBindSampler(units[i], samplers[i]);
            }
            GL13.glActiveTexture(activeTexture);
            set(GL11.GL_BLEND, blend);
            set(GL11.GL_DEPTH_TEST, depthTest);
            set(GL11.GL_CULL_FACE, cullFace);
            set(GL11.GL_SCISSOR_TEST, scissorTest);
            set(GL11.GL_STENCIL_TEST, stencilTest);
            set(GL11.GL_COLOR_LOGIC_OP, logicOp);
            set(GL30.GL_FRAMEBUFFER_SRGB, srgb);
            GL11.glDepthMask(depthMask);
            GL11.glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
            GL14.glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
            GL20.glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        }

        private static void set(int capability, boolean enabled) {
            if (enabled) {
                GL11.glEnable(capability);
            } else {
                GL11.glDisable(capability);
            }
        }
    }
}
