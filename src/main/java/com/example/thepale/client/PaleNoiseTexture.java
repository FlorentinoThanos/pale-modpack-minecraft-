package com.example.thepale.client;

import com.example.thepale.ThePale;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * A tiling cloud texture for drawing the wall through a shader pack, which can't run the wall's own shader. Made
 * once from the same kind of value noise as pale_wall.fsh: white, with the clouds in the alpha and a little shading.
 */
public final class PaleNoiseTexture {
    public static final ResourceLocation LOCATION = ThePale.id("dynamic/pale_clouds");
    /** Texels per side. The noise lattices below all divide it, so the texture tiles without a seam. */
    private static final int SIZE = 256;

    private static boolean made;

    private PaleNoiseTexture() {}

    /** {@return the texture's location}, making it on first use (on the render thread). */
    public static ResourceLocation get() {
        if (!made) {
            made = true;
            NativeImage image = new NativeImage(NativeImage.Format.RGBA, SIZE, SIZE, false);
            for (int y = 0; y < SIZE; y++) {
                for (int x = 0; x < SIZE; x++) {
                    // Cells of 64, 32, 16 and 8 texels: with the texture at 384 blocks, about 96, 48, 24 and 12 blocks.
                    float huge = fbm(x, y, 64, 3, 11);
                    float broad = fbm(x, y, 32, 3, 23);
                    float fine = valueNoise(x, y, 8, 37);
                    float cloud = smoothstep(0.3f, 0.75f, huge * 0.45f + broad * 0.4f + fine * 0.15f);
                    float alpha = 0.16f + 0.84f * cloud;
                    float shade = 1f - 0.13f * cloud; // thick parts a little darker, as in pale_wall.fsh
                    int a = Math.round(alpha * 255);
                    int c = Math.round(shade * 255);
                    image.setPixelRGBA(x, y, a << 24 | c << 16 | c << 8 | c); // ABGR
                }
            }
            DynamicTexture texture = new DynamicTexture(image);
            texture.setFilter(true, false);
            Minecraft.getInstance().getTextureManager().register(LOCATION, texture);
        }
        return LOCATION;
    }

    /** Octaves halve the cell size each time, so every one still tiles across {@link #SIZE}. */
    private static float fbm(int x, int y, int cell, int octaves, int seed) {
        float sum = 0;
        float amplitude = 0.5f;
        float total = 0;
        for (int i = 0; i < octaves && cell >= 2; i++) {
            sum += amplitude * valueNoise(x, y, cell, seed + i * 7);
            total += amplitude;
            amplitude *= 0.5f;
            cell /= 2;
        }
        return sum / total;
    }

    private static float valueNoise(int x, int y, int cell, int seed) {
        int period = SIZE / cell;
        int cx = x / cell;
        int cy = y / cell;
        float fx = smooth((x % cell) / (float) cell);
        float fy = smooth((y % cell) / (float) cell);
        float a = hash(cx % period, cy % period, seed);
        float b = hash((cx + 1) % period, cy % period, seed);
        float c = hash(cx % period, (cy + 1) % period, seed);
        float d = hash((cx + 1) % period, (cy + 1) % period, seed);
        return lerp(fy, lerp(fx, a, b), lerp(fx, c, d));
    }

    private static float hash(int x, int y, int seed) {
        int h = x * 374761393 + y * 668265263 + seed * 1274126177;
        h = (h ^ (h >>> 13)) * 1274126177;
        h ^= h >>> 16;
        return (h & 0xFFFFFF) / (float) 0xFFFFFF;
    }

    private static float smooth(float t) {
        return t * t * (3f - 2f * t);
    }

    private static float smoothstep(float edge0, float edge1, float x) {
        float t = Math.max(0f, Math.min(1f, (x - edge0) / (edge1 - edge0)));
        return smooth(t);
    }

    private static float lerp(float t, float a, float b) {
        return a + (b - a) * t;
    }
}
