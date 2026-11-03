package com.example.thepale.client;

import com.example.thepale.config.ClientConfig;
import com.example.thepale.worldgen.PaleAltitude;
import com.example.thepale.worldgen.PaleShape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * White ash blowing around the player inside the Pale, spiralling outward from (0, 0). Thickens with depth p (0 at
 * innerRadius, 1 at outerRadius): 1 + 80p² particles a tick, 0.04 + 0.95p² blocks a tick, 1 + 2.5p times the size.
 */
public final class PaleParticles {
    /** The box around the player the ash is spawned in (40 x 18 x 40, a little more of it above the eyes). */
    private static final double SPREAD_XZ = 40;
    private static final double SPREAD_Y = 18;
    /** Direction of the drift: mostly around the ring, partly out from the centre. */
    private static final double OUTWARD = 0.6;
    private static final double TANGENTIAL = 0.8;

    private PaleParticles() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        ClientConfig.Settings config = ClientConfig.get();
        if (level == null || player == null || mc.isPaused() || config == null || !config.appliesTo(level)) {
            return;
        }
        double x = player.getX();
        double y = player.getEyeY();
        double z = player.getZ();
        PaleShape shape = PaleShape.client();
        double depth = (shape.effectiveRadius(x, z) - config.innerRadius()) / Math.max(config.outerRadius() - config.innerRadius(), 1.0);
        // High above the Pale the air is clear.
        depth = Math.min(depth, 1.0) * PaleAltitude.strength(y);
        if (depth <= 0) {
            return;
        }

        // Unit spiral direction: OUTWARD from the nearest island plus TANGENTIAL around it.
        PaleShape.Island island = shape.nearest(x, z);
        double outX = x - island.centerX();
        double outZ = z - island.centerZ();
        double radius = Math.max(Math.sqrt(outX * outX + outZ * outZ), 1.0);
        double dirX = (outX * OUTWARD - outZ * TANGENTIAL) / radius;
        double dirZ = (outZ * OUTWARD + outX * TANGENTIAL) / radius;
        double speed = 0.04 + depth * depth * 0.95;
        int count = (int) (1 + depth * depth * 80);
        float scale = (float) (1 + depth * 2.5);
        RandomSource random = level.random;
        for (int i = 0; i < count; i++) {
            double jitter = random.nextDouble() * 0.6 + 0.7;
            Particle particle = mc.particleEngine.createParticle(
                    ParticleTypes.WHITE_ASH,
                    x + (random.nextDouble() - 0.5) * SPREAD_XZ,
                    y + (random.nextDouble() - 0.4) * SPREAD_Y,
                    z + (random.nextDouble() - 0.5) * SPREAD_XZ,
                    0, 0, 0);
            if (particle != null) {
                particle.setParticleSpeed(dirX * speed * jitter, (random.nextDouble() - 0.5) * 0.03, dirZ * speed * jitter);
                particle.scale(scale);
            }
        }
    }
}
