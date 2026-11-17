package com.example.thepale.colossus.client;

import com.example.thepale.colossus.ColossusPose;
import com.example.thepale.colossus.ColossusRegistry;
import com.example.thepale.colossus.FrostColossus;
import com.example.thepale.config.ClientConfig;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * What a client sees and hears of the Frost Colossus besides its model: each footfall (a deep thud, a cloud of snow,
 * cracks in the ground and the screen shaking a little), snow sifting off it and drips from its ice, and the snow it
 * shakes off when it gets up in the morning. Everything here is the client's own, worked out from the colossus it
 * sees; nothing is sent for it.
 */
public final class ColossusEffects implements FrostColossus.ClientEffects {
    /** Footfalls are felt this far away, in blocks. */
    private static final double SHAKE_DISTANCE = 32;
    /** How hard a full step shakes the camera at the foot, in degrees, and how much of it a rider feels. */
    private static final float SHAKE_DEGREES = 1.4f;
    private static final double RIDER_SHAKE = 0.15;
    /** How long the cracks of a footfall stay, in ticks. */
    private static final int CRACK_TICKS = 120;
    private static final BlockParticleOption SNOW_DUST = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.SNOW_BLOCK.defaultBlockState());
    private static final BlockParticleOption SNOW_CHUNK = new BlockParticleOption(ParticleTypes.BLOCK, Blocks.SNOW_BLOCK.defaultBlockState());
    private static final BlockParticleOption ICE_DRIP = new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.PACKED_ICE.defaultBlockState());
    /** The parts snow sifts off: the shoulders, the head, the chest and the arms. */
    private static final int[] SNOWY_PARTS = {
        ColossusPose.CHEST, ColossusPose.LEFT_SHOULDER, ColossusPose.RIGHT_SHOULDER, ColossusPose.HEAD,
        ColossusPose.LEFT_UPPER_ARM, ColossusPose.RIGHT_UPPER_ARM, ColossusPose.LEFT_FOREARM, ColossusPose.RIGHT_FOREARM};

    private static final List<Crack> CRACKS = new ArrayList<>();
    /** Ids for the cracks' block-breaking overlays: well clear of entity ids, which the game uses for its own. */
    private static int nextCrackId = -1_000_000;
    private static double shake;
    private static double shakeOld;

    private record Crack(int id, BlockPos pos, int stage, int[] age) {}

    @Override
    public void footfall(FrostColossus colossus, double x, double y, double z, float yaw, double weight) {
        ClientLevel level = (ClientLevel) colossus.level();
        Minecraft minecraft = Minecraft.getInstance();
        RandomSource random = level.random;
        // A deep thud, heard far off; a small step is a softer one.
        level.playLocalSound(x, y, z, ColossusRegistry.STEP.get(), SoundSource.NEUTRAL,
                (float) (1.5 + 2.5 * weight), 0.85f + random.nextFloat() * 0.15f, false);

        BlockPos under = BlockPos.containing(x, y - 0.5, z);
        BlockState ground = level.getBlockState(under);
        if (ground.isAir() || !ground.getFluidState().isEmpty()) {
            ground = Blocks.SNOW_BLOCK.defaultBlockState();
        }
        BlockParticleOption dust = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        int count = (int) (40 + 100 * weight);
        for (int i = 0; i < count; i++) {
            // A ring thrown out from under the foot, low over the ground: snow, bits of the ground, and a white puff.
            double angle = random.nextDouble() * Math.PI * 2;
            double out = 1.8 + random.nextDouble() * 1.8;
            double speed = (0.06 + random.nextDouble() * 0.16) * (0.5 + weight);
            ParticleOptions kind = switch (i % 4) {
                case 0 -> ParticleTypes.SNOWFLAKE;
                case 1 -> dust;
                case 2 -> SNOW_CHUNK;
                default -> ParticleTypes.CLOUD;
            };
            double lift = kind == ParticleTypes.CLOUD ? 0.01 + random.nextDouble() * 0.03 : 0.08 + random.nextDouble() * 0.14;
            if (kind == ParticleTypes.CLOUD) {
                speed *= 0.6;
            }
            level.addParticle(kind, x + Math.cos(angle) * out, y + 0.15 + random.nextDouble() * 0.4, z + Math.sin(angle) * out,
                    Math.cos(angle) * speed, lift, Math.sin(angle) * speed);
        }
        if (weight > 0.3) {
            crack(level, x, y, z, yaw, weight);
        }

        Player player = minecraft.player;
        double strength = ClientConfig.get() == null ? 1 : ClientConfig.get().colossusScreenShake();
        if (player != null && strength > 0) {
            double distance = Math.sqrt(player.distanceToSqr(x, y, z));
            double felt = weight * Math.max(0, 1 - distance / SHAKE_DISTANCE) * strength;
            if (player.getVehicle() == colossus) {
                felt = weight * RIDER_SHAKE * strength;
            }
            shake = Math.max(shake, felt);
        }
    }

    /** Cracks in the ground around the foot (under it they'd be hidden): the game's block-breaking overlay, fading out. */
    private static void crack(ClientLevel level, double x, double y, double z, float yaw, double weight) {
        double sin = Math.sin(Math.toRadians(yaw));
        double cos = Math.cos(Math.toRadians(yaw));
        RandomSource random = level.random;
        int groundY = Mth.floor(y - 0.5);
        for (int blockX = Mth.floor(x - 4); blockX <= Mth.floor(x + 4); blockX++) {
            for (int blockZ = Mth.floor(z - 4); blockZ <= Mth.floor(z + 4); blockZ++) {
                double dx = blockX + 0.5 - x;
                double dz = blockZ + 0.5 - z;
                double along = -dx * sin + dz * cos;
                double across = dx * cos + dz * sin;
                double distance = Math.sqrt(dx * dx + dz * dz);
                boolean underFoot = Math.abs(along) < 1.75 && Math.abs(across) < 1.25;
                if (underFoot || distance > 3.6 || random.nextFloat() < 0.3f) {
                    continue;
                }
                BlockPos pos = new BlockPos(blockX, groundY, blockZ);
                // The top of the ground there: one block up or down from where the foot stands.
                for (int step = 0; step < 3 && !level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty(); step++) {
                    pos = pos.above();
                }
                if (!level.getBlockState(pos).isSolidRender(level, pos)) {
                    pos = pos.below();
                    if (!level.getBlockState(pos).isSolidRender(level, pos)) {
                        continue;
                    }
                }
                int stage = Mth.clamp((int) Math.round(9 * weight - 1.5 * (distance - 1.8)) + random.nextInt(2), 1, 8);
                Crack crack = new Crack(nextCrackId--, pos, stage, new int[] {0});
                CRACKS.add(crack);
                Minecraft.getInstance().levelRenderer.destroyBlockProgress(crack.id(), pos, stage);
            }
        }
        if (nextCrackId < -2_000_000) {
            nextCrackId = -1_000_000;
        }
    }

    @Override
    public void tick(FrostColossus colossus) {
        ClientLevel level = (ClientLevel) colossus.level();
        RandomSource random = level.random;
        ColossusPose pose = colossus.pose(1f);
        FrostColossus.Activity activity = colossus.activity();
        Player player = Minecraft.getInstance().player;
        if (player == null || player.distanceToSqr(colossus) > 96 * 96) {
            return;
        }
        // A glimmer about each cluster of crystals it still carries, so that they are seen from the ground.
        for (int cluster = 0; cluster < 3; cluster++) {
            if (colossus.hasCrystals(cluster) && random.nextFloat() < 0.25f) {
                Vec3 at = colossus.toWorld(pose.part[ColossusPose.LEFT_CRYSTALS + cluster], 1f);
                level.addParticle(ParticleTypes.GLOW, at.x + (random.nextDouble() - 0.5) * 3, at.y + (random.nextDouble() - 0.5) * 4,
                        at.z + (random.nextDouble() - 0.5) * 3, 0, 0.01, 0);
            }
        }
        // Snow sifting off its shoulders and arms, and now and then a drop from its ice.
        if (random.nextFloat() < (activity == FrostColossus.Activity.ASLEEP ? 0.15f : 0.4f)) {
            Vec3 at = colossus.toWorld(pose.part[SNOWY_PARTS[random.nextInt(SNOWY_PARTS.length)]], 1f);
            level.addParticle(random.nextBoolean() ? ParticleTypes.SNOWFLAKE : SNOW_DUST,
                    at.x + (random.nextDouble() - 0.5) * 4, at.y + random.nextDouble() * 2, at.z + (random.nextDouble() - 0.5) * 4,
                    (random.nextDouble() - 0.5) * 0.05, -0.05, (random.nextDouble() - 0.5) * 0.05);
        }
        if (random.nextFloat() < 0.12f) {
            int arm = random.nextBoolean() ? ColossusPose.LEFT_FOREARM : ColossusPose.RIGHT_FOREARM;
            Vec3 at = colossus.toWorld(pose.part[arm], 1f);
            level.addParticle(ICE_DRIP, at.x + (random.nextDouble() - 0.5) * 2, at.y - 1, at.z + (random.nextDouble() - 0.5) * 2, 0, 0, 0);
        }
        // Getting up in the morning, it shakes the night's snow off.
        if (activity == FrostColossus.Activity.STANDING_UP && colossus.snowCover(1f) > 0.02) {
            for (int i = 0; i < 24; i++) {
                Vec3 at = colossus.toWorld(pose.part[SNOWY_PARTS[random.nextInt(SNOWY_PARTS.length)]], 1f);
                level.addParticle(i % 2 == 0 ? SNOW_DUST : ParticleTypes.SNOWFLAKE,
                        at.x + (random.nextDouble() - 0.5) * 6, at.y + random.nextDouble() * 2.5, at.z + (random.nextDouble() - 0.5) * 6,
                        (random.nextDouble() - 0.5) * 0.3, 0.05 + random.nextDouble() * 0.1, (random.nextDouble() - 0.5) * 0.3);
            }
            if (colossus.tickCount % 12 == 0) {
                Vec3 chest = colossus.toWorld(pose.part[ColossusPose.HEAD], 1f);
                level.playLocalSound(chest.x, chest.y, chest.z, ColossusRegistry.SHAKE_OFF.get(), SoundSource.NEUTRAL,
                        2.5f, 0.8f + random.nextFloat() * 0.2f, false);
            }
        }
    }

    /** The cracks fade and the shake dies down, once a tick. */
    public static void onClientTick(ClientTickEvent.Post event) {
        shakeOld = shake;
        shake = shake < 0.005 ? 0 : shake * 0.82;
        if (CRACKS.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Iterator<Crack> cracks = CRACKS.iterator();
        while (cracks.hasNext()) {
            Crack crack = cracks.next();
            int age = ++crack.age()[0];
            if (minecraft.level == null || age >= CRACK_TICKS) {
                minecraft.levelRenderer.destroyBlockProgress(crack.id(), crack.pos(), -1);
                cracks.remove();
            } else if (age > CRACK_TICKS / 2) {
                // Fading: a stage less every few ticks.
                int stage = (int) Math.round(crack.stage() * (CRACK_TICKS - age) / (CRACK_TICKS / 2.0));
                minecraft.levelRenderer.destroyBlockProgress(crack.id(), crack.pos(), Math.max(0, stage));
            }
        }
    }

    /** The camera trembles after a nearby footfall: a quick, dying wobble. */
    public static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        double now = Mth.lerp(event.getPartialTick(), shakeOld, shake);
        if (now <= 0.001) {
            return;
        }
        Camera camera = event.getCamera();
        double time = (Minecraft.getInstance().level == null ? 0 : Minecraft.getInstance().level.getGameTime()) + event.getPartialTick();
        float amount = (float) (now * SHAKE_DEGREES);
        event.setPitch(event.getPitch() + amount * (float) Math.sin(time * 2.9));
        event.setRoll(event.getRoll() + amount * 0.7f * (float) Math.sin(time * 2.3 + 1.3));
        if (camera.isDetached()) {
            event.setYaw(event.getYaw() + amount * 0.3f * (float) Math.sin(time * 3.7));
        }
    }
}
