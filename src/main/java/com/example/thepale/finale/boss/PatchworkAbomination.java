package com.example.thepale.finale.boss;

import com.example.thepale.IslandMobs;
import com.example.thepale.config.CommonConfig;
import com.example.thepale.finale.FinaleAdvancements;
import com.example.thepale.finale.FinaleRegistry;
import com.example.thepale.finale.FinaleSounds;
import com.example.thepale.finale.FinaleState;
import com.example.thepale.finale.Invasion;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.AreaEffectCloud;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.event.EventHooks;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Patchwork Abomination, the boss of the finale: a ball of stitched grey flesh hanging over the shore on six
 * tentacles, with a stone face on a seventh and a clawed hand on an eighth.
 *
 * <p>Its body, its main eye and every limb are separate hitboxes ({@link AbominationPart}, NeoForge's
 * {@link PartEntity}, the way the ender dragon is built). Each limb has its own health and can be severed. The fight
 * goes through four phases:
 * <ol>
 *   <li><b>Tentacles.</b> The body is armoured (a fifth of a hit gets through). Tentacles strike from above; a ring of
 *   particles on the ground warns about a second ahead. Every tentacle severed lowers the body.</li>
 *   <li><b>Face and hand</b> (from phase2TentaclesLeft tentacles). They come down from above the body. The face
 *   screams (slowness, blindness), summons mobs and breathes poison clouds; the hand grabs a player and drags them
 *   toward the body until it is hit.</li>
 *   <li><b>Seams</b> (no tentacles left). The seam over the main eye parts. The eye opens and shuts in turn: open, it
 *   is the weak spot; shut, the body sends out shockwaves (jump over them).</li>
 *   <li><b>Fall</b> (from fallHealthFraction of its health). The body drops to the ground and crawls after the players,
 *   taking full damage.</li>
 * </ol>
 *
 * <p>Where the limbs are is worked out the same way on the server (for the hitboxes) and on the client (for the
 * model): see {@link Limbs}. The numbers are in [finale.boss] of the common config. The boss is never saved: if its
 * chunk unloads or the server stops mid-fight, the invasion counts it as a retreat and it returns the next night.
 */
public class PatchworkAbomination extends Monster implements GeoEntity {
    private static final EntityDataAccessor<Integer> DATA_LIMBS = SynchedEntityData.defineId(PatchworkAbomination.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(PatchworkAbomination.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_EYE_OPEN = SynchedEntityData.defineId(PatchworkAbomination.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_GRIP = SynchedEntityData.defineId(PatchworkAbomination.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Vector3f> DATA_FACE_AIM = SynchedEntityData.defineId(PatchworkAbomination.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> DATA_HAND_AIM = SynchedEntityData.defineId(PatchworkAbomination.class, EntityDataSerializers.VECTOR3);

    /** The health attribute: the body's health is kept as a share of this, so it isn't capped by vanilla's 1024. */
    private static final double HEALTH_UNITS = 1000;
    /** A strike after the wind-up: ticks coming down, stuck in the ground, and going back. */
    private static final int STRIKE_TICKS = 3;
    private static final int STUCK_TICKS = 30;
    private static final int RETURN_TICKS = 20;
    private static final int NO_SLAM = -1_000_000;
    /** How high above its target a tentacle rises before it strikes. */
    private static final double STRIKE_HEIGHT = 9;
    private static final int DEATH_TICKS = 100;
    /** Tag on the mobs the face summoned, so they can be counted and taken away with the boss. */
    public static final String SUMMONED_TAG = "thepale_summoned";

    private static final ParticleOptions WARNING = new DustParticleOptions(new Vector3f(0.62f, 0.2f, 0.85f), 1.6f);
    private static final ParticleOptions FLESH = new DustParticleOptions(new Vector3f(0.45f, 0.36f, 0.48f), 2.2f);

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation CRAWL = RawAnimation.begin().thenLoop("crawl");
    private static final RawAnimation DEATH = RawAnimation.begin().thenPlayAndHold("death");
    private static final RawAnimation SEAM_SHUT = RawAnimation.begin().thenPlayAndHold("seam_shut");
    private static final RawAnimation SEAM_OPEN = RawAnimation.begin().thenPlayAndHold("seam_open");
    private static final RawAnimation EYE_SHUT = RawAnimation.begin().thenPlayAndHold("eye_shut");
    private static final RawAnimation EYE_OPEN = RawAnimation.begin().thenPlayAndHold("eye_open");
    private static final RawAnimation FACE_IDLE = RawAnimation.begin().thenLoop("face_idle");
    private static final RawAnimation FACE_SCREAM = RawAnimation.begin().thenPlay("face_scream");
    private static final RawAnimation HAND_OPEN = RawAnimation.begin().thenPlayAndHold("hand_open");
    private static final RawAnimation HAND_GRAB = RawAnimation.begin().thenPlayAndHold("hand_grab");

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);

    private final AbominationPart body;
    private final AbominationPart eye;
    /** Three hitboxes along every limb: upper, lower and the tip. */
    private final AbominationPart[][] limbParts = new AbominationPart[Limbs.COUNT][3];
    private final AbominationPart[] parts;

    // Limb motion, worked out alike on both sides.
    private final int[] slamStart = new int[Limbs.TENTACLES];
    private final int[] slamWindup = new int[Limbs.TENTACLES];
    private final Vec3[] slamTarget = new Vec3[Limbs.TENTACLES];
    @Nullable
    private Vec3 faceTip;
    @Nullable
    private Vec3 faceTipOld;
    @Nullable
    private Vec3 handTip;
    @Nullable
    private Vec3 handTipOld;
    private final Vec3[] joints = new Vec3[Limbs.SEGMENTS + 1];

    // The fight, server side.
    private final ServerBossEvent bossEvent = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.PURPLE, BossEvent.BossBarOverlay.NOTCHED_10);
    @Nullable
    private Vec3 home;
    private boolean fromInvasion;
    private final float[] limbHealth = new float[Limbs.COUNT];
    private final int[] limbHurtTick = new int[Limbs.COUNT];
    private final boolean[] slamStruck = new boolean[Limbs.TENTACLES];
    /** How much the boss's health is multiplied by for the number of players; only ever goes up. */
    private double healthScale;
    private int slamCooldown = 60;
    private int spellCooldown = 100;
    private int spellWindup = -1;
    private int spell;
    private HandState handState = HandState.IDLE;
    private int handTimer = 120;
    @Nullable
    private UUID grabbed;
    @Nullable
    private Vec3 reachTarget;
    private int eyeTimer;
    private int pulseCooldown = 60;
    private int pulseWindup = -1;
    private boolean landed;
    private int emptyTicks;
    private int armoredSoundTick;
    private final List<UUID> summons = new ArrayList<>();

    private enum HandState { IDLE, REACH, HOLD }

    public PatchworkAbomination(EntityType<? extends PatchworkAbomination> type, Level level) {
        super(type, level);
        float ball = (float) (Limbs.BODY_RADIUS * 2);
        this.body = new AbominationPart(this, AbominationPart.BODY, ball, ball);
        this.eye = new AbominationPart(this, AbominationPart.EYE, 3.0f, 3.0f);
        List<AbominationPart> all = new ArrayList<>(List.of(body, eye));
        for (int limb = 0; limb < Limbs.COUNT; limb++) {
            float tip = limb == Limbs.FACE ? 2.8f : limb == Limbs.HAND ? 2.6f : 1.7f;
            limbParts[limb][0] = new AbominationPart(this, limb, 2.0f, 2.0f);
            limbParts[limb][1] = new AbominationPart(this, limb, 1.8f, 1.8f);
            limbParts[limb][2] = new AbominationPart(this, limb, tip, tip);
            all.addAll(Arrays.asList(limbParts[limb]));
        }
        this.parts = all.toArray(AbominationPart[]::new);
        Arrays.fill(slamStart, NO_SLAM);
        Arrays.fill(limbHurtTick, -1);
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.xpReward = 0;
        // As for the ender dragon: the parts' ids follow the boss's, so both sides agree on them.
        this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, HEALTH_UNITS)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.FOLLOW_RANGE, 64.0)
                .add(Attributes.ATTACK_DAMAGE, 10.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_LIMBS, (1 << Limbs.COUNT) - 1);
        builder.define(DATA_PHASE, 1);
        builder.define(DATA_EYE_OPEN, false);
        builder.define(DATA_GRIP, false);
        builder.define(DATA_FACE_AIM, new Vector3f());
        builder.define(DATA_HAND_AIM, new Vector3f());
    }

    /** Called by the invasion: the boss stays near this point and reports back to the invasion. */
    public void setHome(Vec3 home, boolean fromInvasion) {
        this.home = home;
        this.fromInvasion = fromInvasion;
    }

    // ----- Multipart plumbing -----

    @Override
    public void setId(int id) {
        super.setId(id);
        for (int i = 0; i < parts.length; i++) {
            parts[i].setId(id + i + 1);
        }
    }

    @Override
    public boolean isMultipartEntity() {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts() {
        return parts;
    }

    /** The boss itself can't be clicked; its parts can. */
    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    protected void doPush(Entity entity) {}

    @Override
    public void push(Entity entity) {}

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean canBeAffected(MobEffectInstance effect) {
        return false;
    }

    @Override
    public boolean canChangeDimensions(Level from, Level to) {
        return false;
    }

    @Override
    public void checkDespawn() {}

    @Override
    protected boolean shouldDespawnInPeaceful() {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }

    @Override
    protected boolean canRide(Entity vehicle) {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motion) {}

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(Limbs.LENGTH + 4);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        bossEvent.setName(getDisplayName());
    }

    // ----- State both sides see -----

    public int getPhase() {
        return entityData.get(DATA_PHASE);
    }

    public boolean limbAlive(int limb) {
        return (entityData.get(DATA_LIMBS) & (1 << limb)) != 0;
    }

    public int tentaclesAlive() {
        return Integer.bitCount(entityData.get(DATA_LIMBS) & ((1 << Limbs.TENTACLES) - 1));
    }

    public boolean eyeOpen() {
        return entityData.get(DATA_EYE_OPEN);
    }

    public boolean gripping() {
        return entityData.get(DATA_GRIP);
    }

    /** {@return the body's lowest point at this partial tick} */
    public Vec3 base(float partialTick) {
        return getPosition(partialTick);
    }

    public float yaw(float partialTick) {
        return Mth.rotLerp(partialTick, yRotO, getYRot());
    }

    /** {@return where the tip of this limb is at this partial tick} */
    public Vec3 tip(int limb, float partialTick) {
        Vec3 base = base(partialTick);
        float yaw = yaw(partialTick);
        if (limb == Limbs.FACE) {
            return faceTip == null ? Limbs.raised(limb, base, yaw) : faceTipOld.lerp(faceTip, partialTick);
        }
        if (limb == Limbs.HAND) {
            return handTip == null ? Limbs.raised(limb, base, yaw) : handTipOld.lerp(handTip, partialTick);
        }
        Vec3 rest = restTip(limb, base, yaw);
        float elapsed = tickCount - 1 + partialTick - slamStart[limb];
        int windup = slamWindup[limb];
        Vec3 target = slamTarget[limb];
        if (target == null || elapsed < 0 || elapsed >= windup + STRIKE_TICKS + STUCK_TICKS + RETURN_TICKS) {
            return rest;
        }
        // Up above the target, a little back toward the body; then straight down.
        Vec3 raised = new Vec3(
                Mth.lerp(0.3, target.x, base.x), target.y + STRIKE_HEIGHT, Mth.lerp(0.3, target.z, base.z));
        if (elapsed < windup) {
            return rest.lerp(raised, ease(elapsed / windup));
        }
        elapsed -= windup;
        if (elapsed < STRIKE_TICKS) {
            float fall = elapsed / STRIKE_TICKS;
            return raised.lerp(target, fall * fall);
        }
        elapsed -= STRIKE_TICKS;
        if (elapsed < STUCK_TICKS) {
            return target;
        }
        return target.lerp(rest, ease((elapsed - STUCK_TICKS) / RETURN_TICKS));
    }

    /** Fills {@code out} ({@link Limbs#SEGMENTS} + 1 points) with the joints of this limb at this partial tick. */
    public void joints(int limb, float partialTick, Vec3[] out) {
        Vec3 base = base(partialTick);
        Limbs.hang(Limbs.anchor(limb, base, yaw(partialTick)), tip(limb, partialTick), base, out);
    }

    private Vec3 restTip(int limb, Vec3 base, float yaw) {
        Vec3 column = Limbs.restColumn(limb, base, yaw);
        // The surface the game tracks on both sides (it counts water and leaves): the tips rest on that.
        double ground = level().getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(column.x), Mth.floor(column.z));
        return new Vec3(column.x, Mth.clamp(ground, base.y - 13, base.y + 3), column.z);
    }

    private static float ease(float t) {
        t = Mth.clamp(t, 0, 1);
        return t * t * (3 - 2 * t);
    }

    /** A tentacle starts a strike now (on the client: the server said so). */
    public void startSlam(int limb, Vec3 target, int windup) {
        slamStart[limb] = tickCount;
        slamTarget[limb] = target;
        slamWindup[limb] = windup;
        slamStruck[limb] = false;
    }

    private boolean slamming(int limb) {
        return tickCount - slamStart[limb] < slamWindup[limb] + STRIKE_TICKS + STUCK_TICKS + RETURN_TICKS;
    }

    // ----- Ticking -----

    @Override
    public void aiStep() {
        super.aiStep();
        // Nothing but the fight moves it.
        setDeltaMovement(Vec3.ZERO);
        if (!level().isClientSide && isAlive()) {
            fight((ServerLevel) level());
        }
        moveLimbTips();
        moveParts();
    }

    /** The face and the hand ease toward where the server aims them. */
    private void moveLimbTips() {
        Vec3 faceAim = aim(DATA_FACE_AIM, Limbs.FACE);
        Vec3 handAim = aim(DATA_HAND_AIM, Limbs.HAND);
        faceTipOld = faceTip == null ? faceAim : faceTip;
        handTipOld = handTip == null ? handAim : handTip;
        faceTip = faceTipOld.lerp(faceAim, 0.25);
        handTip = handTipOld.lerp(handAim, 0.3);
    }

    private Vec3 aim(EntityDataAccessor<Vector3f> key, int limb) {
        Vector3f aim = entityData.get(key);
        // Not aimed yet (just spawned): held up.
        return aim.lengthSquared() == 0 ? Limbs.raised(limb, position(), getYRot()) : new Vec3(aim);
    }

    private void moveParts() {
        Vec3 base = position();
        Vec3 centre = Limbs.centre(base);
        body.centreOn(centre.x, centre.y, centre.z);
        Vec3 eyeAt = Limbs.eye(base, getYRot());
        eye.centreOn(eyeAt.x, eyeAt.y, eyeAt.z);
        for (int limb = 0; limb < Limbs.COUNT; limb++) {
            boolean alive = limbAlive(limb) && !isDeadOrDying();
            for (AbominationPart part : limbParts[limb]) {
                part.setActive(alive);
            }
            if (!alive) {
                for (AbominationPart part : limbParts[limb]) {
                    part.centreOn(centre.x, centre.y, centre.z);
                }
                continue;
            }
            joints(limb, 1f, joints);
            Vec3 upper = joints[3];
            Vec3 lower = joints[5].lerp(joints[6], 0.5);
            Vec3 end = joints[Limbs.SEGMENTS];
            limbParts[limb][0].centreOn(upper.x, upper.y, upper.z);
            limbParts[limb][1].centreOn(lower.x, lower.y, lower.z);
            limbParts[limb][2].centreOn(end.x, end.y, end.z);
        }
    }

    private void fight(ServerLevel level) {
        CommonConfig.Finale config = CommonConfig.get().finale();
        if (home == null) {
            home = position(); // summoned by hand
        }
        List<ServerPlayer> present = new ArrayList<>();
        List<ServerPlayer> fighters = new ArrayList<>();
        double arena = config.arenaRadius();
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator()
                    || Mth.square(player.getX() - home.x) + Mth.square(player.getZ() - home.z) > arena * arena) {
                continue;
            }
            present.add(player);
            if (!player.isCreative()) {
                fighters.add(player);
            }
        }
        scaleHealth(config, Math.max(fighters.size(), 1));
        if (tickCount % 10 == 0) {
            showBossBar(level, arena + 16);
        }
        bossEvent.setProgress(getHealth() / getMaxHealth());

        if (present.isEmpty()) {
            if (++emptyTicks > config.retreatMinutes() * 1200) {
                retreat(level);
                return;
            }
        } else {
            emptyTicks = 0;
        }

        ServerPlayer target = null;
        double nearest = Double.MAX_VALUE;
        for (ServerPlayer player : fighters) {
            double distance = player.distanceToSqr(Limbs.centre(position()));
            if (distance < nearest) {
                nearest = distance;
                target = player;
            }
        }
        setTarget(target);

        advancePhase(level, config);
        int phase = getPhase();
        moveBody(level, config, target, phase);
        aimFaceAndHand(level, config, target, phase);
        if (phase <= 2) {
            strikeWithTentacles(level, config, fighters);
        }
        if (phase >= 2 && phase <= 3) {
            if (limbAlive(Limbs.FACE)) {
                castSpells(level, config, target, fighters);
            }
            if (limbAlive(Limbs.HAND)) {
                grab(level, config, target);
            }
        }
        if (phase == 3) {
            blinkAndPulse(level, config, fighters);
        } else if (phase == 4) {
            crawl(level, config, fighters);
        }
        if (tickCount % 120 == 0) {
            playSound(FinaleSounds.BOSS_AMBIENT.get(), 3f, 0.9f + random.nextFloat() * 0.2f);
        }
    }

    private void scaleHealth(CommonConfig.Finale config, int players) {
        double scale = 1 + config.extraPlayerHealth() * (players - 1);
        if (healthScale == 0) {
            healthScale = scale;
            for (int limb = 0; limb < Limbs.COUNT; limb++) {
                limbHealth[limb] = (float) (limbMaxHealth(config, limb) * scale);
            }
        } else if (scale > healthScale) {
            // Somebody joined the fight: the limbs gain what they'd have had; the body's bar is a share, so it holds.
            for (int limb = 0; limb < Limbs.COUNT; limb++) {
                limbHealth[limb] *= (float) (scale / healthScale);
            }
            healthScale = scale;
        }
    }

    private static double limbMaxHealth(CommonConfig.Finale config, int limb) {
        return limb == Limbs.FACE ? config.faceHealth() : limb == Limbs.HAND ? config.handHealth() : config.tentacleHealth();
    }

    private void showBossBar(ServerLevel level, double range) {
        for (ServerPlayer player : level.players()) {
            boolean near = Mth.square(player.getX() - home.x) + Mth.square(player.getZ() - home.z) <= range * range;
            if (near) {
                bossEvent.addPlayer(player);
            } else {
                bossEvent.removePlayer(player);
            }
        }
    }

    private void advancePhase(ServerLevel level, CommonConfig.Finale config) {
        int phase = getPhase();
        if (phase == 1 && tentaclesAlive() <= config.phase2TentaclesLeft()) {
            phase = 2;
            playSound(FinaleSounds.BOSS_SCREAM.get(), 4f, 0.7f);
        }
        if (phase == 2 && tentaclesAlive() == 0) {
            phase = 3;
            eyeTimer = 50;
            pulseCooldown = 30;
            playSound(FinaleSounds.BOSS_EYE_OPEN.get(), 4f, 0.8f);
        }
        if (phase == 3 && getHealth() <= getMaxHealth() * config.fallHealthFraction()) {
            phase = 4;
            for (int limb = 0; limb < Limbs.COUNT; limb++) {
                if (limbAlive(limb)) {
                    sever(level, limb, false);
                }
            }
            entityData.set(DATA_EYE_OPEN, true);
            pulseCooldown = 50;
            pulseWindup = -1;
            playSound(FinaleSounds.BOSS_FALL.get(), 4f, 0.8f);
        }
        if (phase != getPhase()) {
            entityData.set(DATA_PHASE, phase);
        }
    }

    /** How high the body hangs over the ground. */
    private double hover(int phase) {
        return switch (phase) {
            case 1, 2 -> 7.5 - 0.5 * (Limbs.TENTACLES - tentaclesAlive());
            case 3 -> eyeOpen() ? 1.2 : 3.5;
            default -> -0.5;
        };
    }

    private void moveBody(ServerLevel level, CommonConfig.Finale config, @Nullable ServerPlayer target, int phase) {
        Vec3 position = position();
        double x = position.x;
        double z = position.z;
        if (target != null) {
            double dx = target.getX() - x;
            double dz = target.getZ() - z;
            double distance = Math.sqrt(dx * dx + dz * dz);
            // Hanging, it keeps the player in reach of its tentacles; fallen, it goes for them.
            double keep = phase == 4 ? 0 : 6.5;
            double speed = phase == 4 ? 0.21 : 0.07;
            if (distance > keep + 0.5) {
                double step = Math.min(speed, distance - keep);
                x += dx / distance * step;
                z += dz / distance * step;
            }
            float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            setYRot(Mth.approachDegrees(getYRot(), wanted, phase == 4 ? 4f : 2f));
        }
        // Never further inland (or out to sea) than its leash.
        double leash = config.bossLeash() + (phase == 4 ? 8 : 0);
        double fromHomeX = x - home.x;
        double fromHomeZ = z - home.z;
        double fromHome = Math.sqrt(fromHomeX * fromHomeX + fromHomeZ * fromHomeZ);
        if (fromHome > leash) {
            x = home.x + fromHomeX / fromHome * leash;
            z = home.z + fromHomeZ / fromHome * leash;
        }
        double ground = groundUnder(level, x, z);
        double wantedY = ground + hover(phase);
        double y = position.y;
        if (phase == 4 && !landed) {
            y = Math.max(wantedY, y - 0.9);
            if (y <= wantedY + 0.01) {
                landed = true;
                shockwave(level, config, level.players(), 11);
            }
        } else {
            y += Mth.clamp(wantedY - y, -0.2, 0.2);
        }
        setPos(x, y, z);
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
    }

    private double groundUnder(ServerLevel level, double x, double z) {
        int ground = level.getMinBuildHeight();
        for (int dx = -3; dx <= 3; dx += 3) {
            for (int dz = -3; dz <= 3; dz += 3) {
                ground = Math.max(ground, level.getHeight(Heightmap.Types.MOTION_BLOCKING, Mth.floor(x) + dx, Mth.floor(z) + dz));
            }
        }
        return ground;
    }

    // ----- Phase 1 and 2: the tentacles -----

    private void strikeWithTentacles(ServerLevel level, CommonConfig.Finale config, List<ServerPlayer> fighters) {
        int windup = config.slamWindupTicks();
        for (int limb = 0; limb < Limbs.TENTACLES; limb++) {
            if (!limbAlive(limb) || slamTarget[limb] == null) {
                continue;
            }
            int elapsed = tickCount - slamStart[limb];
            Vec3 at = slamTarget[limb];
            if (elapsed < slamWindup[limb]) {
                if (elapsed % 2 == 0) {
                    ring(level, WARNING, at, config.slamRadius(), 20);
                }
            } else if (!slamStruck[limb] && elapsed >= slamWindup[limb] + STRIKE_TICKS) {
                slamStruck[limb] = true;
                strike(level, config, at);
            }
        }
        if (--slamCooldown > 0 || fighters.isEmpty()) {
            return;
        }
        List<Integer> free = new ArrayList<>();
        for (int limb = 0; limb < Limbs.TENTACLES; limb++) {
            if (limbAlive(limb) && !slamming(limb)) {
                free.add(limb);
            }
        }
        if (free.isEmpty()) {
            return;
        }
        ServerPlayer victim = fighters.get(random.nextInt(fighters.size()));
        // The tentacle that has the shortest way to them, if any reaches.
        int best = -1;
        double bestDistance = Limbs.LENGTH - 1;
        for (int limb : free) {
            double distance = Limbs.anchor(limb, position(), getYRot()).distanceTo(victim.position());
            if (distance < bestDistance) {
                bestDistance = distance;
                best = limb;
            }
        }
        if (best < 0) {
            slamCooldown = 10;
            return;
        }
        Vec3 at = victim.position();
        startSlam(best, at, windup);
        PacketDistributor.sendToPlayersTrackingEntity(this, new SlamPayload(getId(), best, at.x, at.y, at.z, windup));
        level.playSound(null, at.x, at.y, at.z, FinaleSounds.BOSS_WINDUP.get(), SoundSource.HOSTILE, 2f, 0.8f + random.nextFloat() * 0.3f);
        // Fewer tentacles strike a little more often.
        slamCooldown = (int) Math.round(config.slamIntervalTicks() * (0.55 + 0.45 * tentaclesAlive() / Limbs.TENTACLES));
    }

    private void strike(ServerLevel level, CommonConfig.Finale config, Vec3 at) {
        double radius = config.slamRadius();
        level.playSound(null, at.x, at.y, at.z, FinaleSounds.BOSS_SLAM.get(), SoundSource.HOSTILE, 3f, 0.8f + random.nextFloat() * 0.3f);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5, at.z, 3, radius * 0.3, 0.2, radius * 0.3, 0);
        level.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, at.x, at.y + 0.3, at.z, 14, radius * 0.4, 0.2, radius * 0.4, 0.03);
        for (ServerPlayer player : level.players()) {
            if (!player.isAlive() || player.isSpectator() || Math.abs(player.getY() - at.y) > 3
                    || Mth.square(player.getX() - at.x) + Mth.square(player.getZ() - at.z) > radius * radius) {
                continue;
            }
            if (player.hurt(damageSources().mobAttack(this), (float) config.slamDamage())) {
                player.addEffect(new MobEffectInstance(MobEffects.WITHER, 100, 1), this);
                Vec3 away = new Vec3(player.getX() - at.x, 0, player.getZ() - at.z);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                player.push(away.x * 0.9, 0.45, away.z * 0.9);
                player.hurtMarked = true;
            }
        }
        int breakRadius = config.blockBreakRadius();
        if (breakRadius > 0 && EventHooks.canEntityGrief(level, this)) {
            BlockPos centre = BlockPos.containing(at);
            for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-breakRadius, 0, -breakRadius), centre.offset(breakRadius, breakRadius, breakRadius))) {
                BlockState state = level.getBlockState(pos);
                if (!state.isAir() && state.getDestroySpeed(level, pos) >= 0 && state.getFluidState().isEmpty()
                        && pos.distSqr(centre) <= breakRadius * breakRadius + 1) {
                    level.destroyBlock(pos, true, this);
                }
            }
        }
    }

    // ----- Phase 2 and 3: the face and the hand -----

    private void aimFaceAndHand(ServerLevel level, CommonConfig.Finale config, @Nullable ServerPlayer target, int phase) {
        Vec3 base = position();
        Vec3 faceAim = Limbs.raised(Limbs.FACE, base, getYRot());
        Vec3 handAim = Limbs.raised(Limbs.HAND, base, getYRot());
        if (phase >= 2 && target != null) {
            Vec3 centre = Limbs.centre(base);
            Vec3 toBody = new Vec3(centre.x - target.getX(), 0, centre.z - target.getZ());
            toBody = toBody.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : toBody.normalize();
            Vec3 side = new Vec3(-toBody.z, 0, toBody.x);
            double sway = Math.sin(tickCount * 0.07);
            // The face hangs in front of the player, between them and the body; the hand off to one side.
            faceAim = reachable(Limbs.FACE, target.position().add(toBody.scale(4.5)).add(side.scale(-2 + sway)).add(0, 2.4 + 0.4 * sway, 0));
            handAim = reachable(Limbs.HAND, target.position().add(toBody.scale(3.5)).add(side.scale(4 - sway)).add(0, 3.4 - 0.4 * sway, 0));
        }
        if (handState == HandState.REACH && reachTarget != null) {
            handAim = reachable(Limbs.HAND, reachTarget);
        } else if (handState == HandState.HOLD) {
            Vec3 front = Limbs.around(0, getYRot());
            handAim = Limbs.centre(base).add(front.scale(Limbs.BODY_RADIUS + 3.5)).add(0, 2, 0);
        }
        if (limbAlive(Limbs.FACE)) {
            setAim(DATA_FACE_AIM, faceAim);
        }
        if (limbAlive(Limbs.HAND)) {
            setAim(DATA_HAND_AIM, handAim);
        }
    }

    /** {@return the point itself, or the nearest one to it that this limb reaches} */
    private Vec3 reachable(int limb, Vec3 point) {
        Vec3 anchor = Limbs.anchor(limb, position(), getYRot());
        Vec3 offset = point.subtract(anchor);
        double reach = Limbs.LENGTH - 1.5;
        return offset.length() <= reach ? point : anchor.add(offset.normalize().scale(reach));
    }

    private void setAim(EntityDataAccessor<Vector3f> key, Vec3 aim) {
        Vector3f old = entityData.get(key);
        if (old.distanceSquared((float) aim.x, (float) aim.y, (float) aim.z) > 0.01f) {
            entityData.set(key, aim.toVector3f());
        }
    }

    private void castSpells(ServerLevel level, CommonConfig.Finale config, @Nullable ServerPlayer target, List<ServerPlayer> fighters) {
        Vec3 face = faceTip == null ? position() : faceTip;
        if (spellWindup >= 0) {
            level.sendParticles(WARNING, face.x, face.y, face.z, 4, 0.9, 0.9, 0.9, 0);
            if (--spellWindup < 0) {
                switch (spell++ % 3) {
                    case 0 -> scream(level, face);
                    case 1 -> summon(level, config, face, target);
                    default -> breathePoison(level, config, target);
                }
            }
            return;
        }
        if (--spellCooldown <= 0 && target != null) {
            spellCooldown = (int) Math.round(config.screamIntervalSeconds() * 20);
            spellWindup = 20;
            triggerAnim("face", "scream");
        }
    }

    private void scream(ServerLevel level, Vec3 face) {
        level.playSound(null, face.x, face.y, face.z, FinaleSounds.BOSS_SCREAM.get(), SoundSource.HOSTILE, 3f, 1f);
        level.sendParticles(ParticleTypes.SONIC_BOOM, face.x, face.y, face.z, 1, 0, 0, 0, 0);
        for (ServerPlayer player : level.players()) {
            if (player.isAlive() && !player.isSpectator() && !player.isCreative() && player.distanceToSqr(face) <= 16 * 16) {
                player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 80, 2), this);
                player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 60, 0), this);
            }
        }
    }

    private void summon(ServerLevel level, CommonConfig.Finale config, Vec3 face, @Nullable ServerPlayer target) {
        for (Iterator<UUID> it = summons.iterator(); it.hasNext();) {
            Entity summoned = level.getEntity(it.next());
            if (summoned == null || !summoned.isAlive()) {
                it.remove();
            }
        }
        if (summons.size() >= config.maxSummons()) {
            return;
        }
        List<EntityType<?>> types = new ArrayList<>();
        for (String id : config.summonMobs()) {
            ResourceLocation key = ResourceLocation.tryParse(id.trim());
            if (key != null) {
                BuiltInRegistries.ENTITY_TYPE.getOptional(key).ifPresent(types::add);
            }
        }
        if (types.isEmpty()) {
            types.add(EntityType.ZOMBIE);
        }
        level.playSound(null, face.x, face.y, face.z, FinaleSounds.BOSS_SUMMON.get(), SoundSource.HOSTILE, 3f, 1f);
        for (int i = 0; i < config.summonCount(); i++) {
            double x = face.x + (random.nextDouble() - 0.5) * 8;
            double z = face.z + (random.nextDouble() - 0.5) * 8;
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(x), Mth.floor(z));
            Entity entity = types.get(random.nextInt(types.size())).create(level);
            if (entity == null) {
                continue;
            }
            entity.moveTo(x, y, z, random.nextFloat() * 360, 0);
            entity.addTag(SUMMONED_TAG);
            if (entity instanceof Mob mob) {
                EventHooks.finalizeMobSpawn(mob, level, level.getCurrentDifficultyAt(mob.blockPosition()), MobSpawnType.MOB_SUMMONED, null);
                mob.setTarget(target);
            }
            // The island rules (no Born in Chaos mobs on the starter island) don't apply to its summons.
            IslandMobs.allow(entity);
            if (level.addFreshEntity(entity)) {
                summons.add(entity.getUUID());
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y + 1, z, 12, 0.4, 0.8, 0.4, 0.02);
            }
        }
    }

    private void breathePoison(ServerLevel level, CommonConfig.Finale config, @Nullable ServerPlayer target) {
        if (target == null) {
            return;
        }
        AreaEffectCloud cloud = new AreaEffectCloud(level, target.getX(), target.getY(), target.getZ());
        cloud.setOwner(this);
        cloud.setRadius(3.5f);
        cloud.setWaitTime(15);
        cloud.setDuration((int) Math.round(config.poisonCloudSeconds() * 20));
        cloud.setParticle(ColorParticleOption.create(ParticleTypes.ENTITY_EFFECT, 0xE0BEBEC3));
        // The poison of the Pale.
        cloud.addEffect(new MobEffectInstance(MobEffects.WITHER, 80, 1));
        cloud.addEffect(new MobEffectInstance(MobEffects.HUNGER, 80, 2));
        level.addFreshEntity(cloud);
        level.playSound(null, target.getX(), target.getY(), target.getZ(), FinaleSounds.BOSS_SUMMON.get(), SoundSource.HOSTILE, 2f, 0.6f);
    }

    private void grab(ServerLevel level, CommonConfig.Finale config, @Nullable ServerPlayer target) {
        Vec3 hand = handTip == null ? position() : handTip;
        switch (handState) {
            case IDLE -> {
                if (--handTimer <= 0 && target != null
                        && Limbs.anchor(Limbs.HAND, position(), getYRot()).distanceTo(target.position()) < Limbs.LENGTH - 2) {
                    handState = HandState.REACH;
                    handTimer = 16;
                    reachTarget = target.position().add(0, 1, 0);
                    level.playSound(null, hand.x, hand.y, hand.z, FinaleSounds.BOSS_WINDUP.get(), SoundSource.HOSTILE, 2f, 1.3f);
                } else if (handTimer <= 0) {
                    handTimer = 20;
                }
            }
            case REACH -> {
                level.sendParticles(WARNING, hand.x, hand.y, hand.z, 3, 0.5, 0.5, 0.5, 0);
                if (--handTimer <= 0) {
                    ServerPlayer caught = null;
                    for (ServerPlayer player : level.players()) {
                        if (player.isAlive() && !player.isSpectator() && !player.isCreative()
                                && player.position().add(0, 1, 0).distanceToSqr(hand) < 2.8 * 2.8) {
                            caught = player;
                            break;
                        }
                    }
                    if (caught == null) {
                        releaseGrip(110);
                    } else {
                        grabbed = caught.getUUID();
                        handState = HandState.HOLD;
                        handTimer = 60;
                        entityData.set(DATA_GRIP, true);
                        level.playSound(null, hand.x, hand.y, hand.z, FinaleSounds.BOSS_GRAB.get(), SoundSource.HOSTILE, 2f, 1f);
                    }
                }
            }
            case HOLD -> {
                Player held = grabbed == null ? null : level.getPlayerByUUID(grabbed);
                if (held == null || !held.isAlive() || held.isSpectator()) {
                    releaseGrip(120);
                    return;
                }
                // Dragged along under the hand, toward the body.
                Vec3 pull = hand.add(0, -1.6, 0).subtract(held.position()).scale(0.45);
                if (pull.lengthSqr() > 1.4 * 1.4) {
                    pull = pull.normalize().scale(1.4);
                }
                held.setDeltaMovement(pull);
                held.hurtMarked = true;
                held.fallDistance = 0;
                if (handTimer % 20 == 0) {
                    held.hurt(damageSources().mobAttack(this), (float) config.grabDamage());
                }
                if (--handTimer <= 0) {
                    // Thrown away from the body.
                    Vec3 front = Limbs.around(0, getYRot());
                    held.setDeltaMovement(front.x * 1.2, 0.9, front.z * 1.2);
                    held.hurtMarked = true;
                    releaseGrip(140);
                }
            }
        }
    }

    private void releaseGrip(int cooldown) {
        handState = HandState.IDLE;
        handTimer = cooldown;
        grabbed = null;
        reachTarget = null;
        entityData.set(DATA_GRIP, false);
    }

    // ----- Phase 3 and 4: the body -----

    private void blinkAndPulse(ServerLevel level, CommonConfig.Finale config, List<ServerPlayer> fighters) {
        if (--eyeTimer <= 0) {
            boolean open = !eyeOpen();
            entityData.set(DATA_EYE_OPEN, open);
            eyeTimer = (int) Math.round((open ? config.eyeOpenSeconds() : config.eyeClosedSeconds()) * 20);
            playSound(FinaleSounds.BOSS_EYE_OPEN.get(), 3f, open ? 1.1f : 0.6f);
            pulseWindup = -1;
            pulseCooldown = 40;
        }
        if (!eyeOpen()) {
            pulse(level, config, fighters, 80, 9);
        }
    }

    private void crawl(ServerLevel level, CommonConfig.Finale config, List<ServerPlayer> fighters) {
        if (!landed) {
            return;
        }
        if (tickCount % 10 == 0) {
            AABB touch = body.getBoundingBox().inflate(0.6, 0, 0.6);
            for (ServerPlayer player : fighters) {
                if (player.getBoundingBox().intersects(touch) && player.hurt(damageSources().mobAttack(this), (float) config.crawlDamage())) {
                    Vec3 away = new Vec3(player.getX() - getX(), 0, player.getZ() - getZ());
                    away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                    player.push(away.x * 1.1, 0.5, away.z * 1.1);
                    player.hurtMarked = true;
                }
            }
        }
        pulse(level, config, fighters, 60, 10);
    }

    /** A shockwave along the ground, with a second of warning. Anybody in the air when it goes off is spared. */
    private void pulse(ServerLevel level, CommonConfig.Finale config, List<ServerPlayer> fighters, int interval, double radius) {
        Vec3 at = new Vec3(getX(), groundUnder(level, getX(), getZ()) + 0.2, getZ());
        if (pulseWindup >= 0) {
            ring(level, WARNING, at, radius * (1 - pulseWindup / 20.0), 36);
            if (--pulseWindup < 0) {
                shockwave(level, config, fighters, radius);
            }
        } else if (--pulseCooldown <= 0) {
            pulseCooldown = interval;
            pulseWindup = 20;
            level.playSound(null, at.x, at.y, at.z, FinaleSounds.BOSS_WINDUP.get(), SoundSource.HOSTILE, 3f, 0.6f);
        }
    }

    private void shockwave(ServerLevel level, CommonConfig.Finale config, List<ServerPlayer> players, double radius) {
        Vec3 at = new Vec3(getX(), groundUnder(level, getX(), getZ()) + 0.2, getZ());
        level.playSound(null, at.x, at.y, at.z, FinaleSounds.BOSS_SLAM.get(), SoundSource.HOSTILE, 4f, 0.6f);
        for (double r = 2; r <= radius; r += 2) {
            ring(level, ParticleTypes.CAMPFIRE_COSY_SMOKE, at, r, (int) (r * 3));
        }
        for (ServerPlayer player : players) {
            if (!player.isAlive() || player.isSpectator() || player.isCreative() || !player.onGround()
                    || Mth.square(player.getX() - at.x) + Mth.square(player.getZ() - at.z) > radius * radius) {
                continue;
            }
            if (player.hurt(damageSources().mobAttack(this), (float) (config.crawlDamage() * 0.75))) {
                Vec3 away = new Vec3(player.getX() - at.x, 0, player.getZ() - at.z);
                away = away.lengthSqr() < 1.0E-4 ? new Vec3(1, 0, 0) : away.normalize();
                player.push(away.x * 1.2, 0.6, away.z * 1.2);
                player.hurtMarked = true;
            }
        }
    }

    private void ring(ServerLevel level, ParticleOptions particle, Vec3 centre, double radius, int count) {
        double turn = random.nextDouble() * Mth.TWO_PI;
        for (int i = 0; i < count; i++) {
            double angle = turn + i * Mth.TWO_PI / count;
            level.sendParticles(particle, centre.x + Math.cos(angle) * radius, centre.y + 0.15, centre.z + Math.sin(angle) * radius, 1, 0, 0, 0, 0);
        }
    }

    // ----- Being hurt -----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount); // /kill
        }
        return hurtPart(body, source, amount);
    }

    /** A hit on one of the hitboxes. Only players (and what they shoot or set off) hurt it. */
    boolean hurtPart(AbominationPart part, DamageSource source, float amount) {
        if (level().isClientSide || isDeadOrDying() || !(level() instanceof ServerLevel level)) {
            return false;
        }
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return super.hurt(source, amount);
        }
        if (!(source.getEntity() instanceof Player) || amount <= 0) {
            return false;
        }
        CommonConfig.Finale config = CommonConfig.get().finale();
        if (healthScale == 0) {
            scaleHealth(config, 1);
        }
        int phase = getPhase();
        Vec3 at = part.getBoundingBox().getCenter();
        if (part.limb >= 0) {
            int limb = part.limb;
            if (!limbAlive(limb) || limbHurtTick[limb] == tickCount) {
                return false; // one swing can catch several hitboxes of a limb: it counts once
            }
            if (!Limbs.isTentacle(limb) && phase < 2) {
                armored(level, at);
                return false;
            }
            limbHurtTick[limb] = tickCount;
            limbHealth[limb] -= amount;
            level.sendParticles(ParticleTypes.DAMAGE_INDICATOR, at.x, at.y, at.z, 4, 0.4, 0.4, 0.4, 0.1);
            level.playSound(null, at.x, at.y, at.z, FinaleSounds.BOSS_HURT.get(), SoundSource.HOSTILE, 1.5f, 1.2f + random.nextFloat() * 0.3f);
            if (limb == Limbs.HAND && handState == HandState.HOLD) {
                releaseGrip(160);
            }
            if (limbHealth[limb] <= 0) {
                sever(level, limb, true);
            }
            return true;
        }
        double factor;
        if (phase >= 4) {
            factor = 1;
        } else if (phase == 3) {
            factor = !eyeOpen() ? config.closedEyeDamageFactor() : part == eye ? config.eyeDamageFactor() : 0.5;
        } else {
            factor = config.armoredDamageFactor();
        }
        if (factor <= 0.25) {
            armored(level, at);
        }
        // The body can't be worn down past the fall before its tentacles are gone.
        float floor = phase < 3 ? (float) (getMaxHealth() * (config.fallHealthFraction() + 0.1)) : 0;
        float units = (float) (amount * factor * HEALTH_UNITS / (config.bodyHealth() * healthScale));
        units = Math.min(units, getHealth() - floor);
        return units > 0 && super.hurt(source, units);
    }

    private void armored(ServerLevel level, Vec3 at) {
        if (tickCount - armoredSoundTick >= 8) {
            armoredSoundTick = tickCount;
            level.playSound(null, at.x, at.y, at.z, FinaleSounds.BOSS_ARMORED.get(), SoundSource.HOSTILE, 1.5f, 0.9f + random.nextFloat() * 0.2f);
        }
        level.sendParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 4, 0.5, 0.5, 0.5, 0.2);
    }

    /** Cuts a limb off. It takes a slice of the boss bar with it, unless the body is falling anyway. */
    private void sever(ServerLevel level, int limb, boolean cut) {
        joints(limb, 1f, joints);
        for (Vec3 joint : joints) {
            level.sendParticles(FLESH, joint.x, joint.y, joint.z, 6, 0.5, 0.5, 0.5, 0);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, joint.x, joint.y, joint.z, 2, 0.4, 0.4, 0.4, 0.02);
        }
        entityData.set(DATA_LIMBS, entityData.get(DATA_LIMBS) & ~(1 << limb));
        limbHealth[limb] = 0;
        if (limb == Limbs.HAND) {
            releaseGrip(0);
        }
        if (limb == Limbs.FACE) {
            spellWindup = -1;
        }
        if (cut) {
            CommonConfig.Finale config = CommonConfig.get().finale();
            Vec3 at = joints[Limbs.SEGMENTS / 2];
            level.playSound(null, at.x, at.y, at.z, FinaleSounds.BOSS_SEVER.get(), SoundSource.HOSTILE, 3f, 0.9f + random.nextFloat() * 0.2f);
            float floor = (float) (getMaxHealth() * (config.fallHealthFraction() + 0.02));
            setHealth(Math.max(Math.min(getHealth(), floor), getHealth() - (float) (getMaxHealth() * config.severDamageFraction())));
        }
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return FinaleSounds.BOSS_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound() {
        return FinaleSounds.BOSS_DEATH.get();
    }

    @Override
    protected float getSoundVolume() {
        return 4f;
    }

    // ----- Leaving and dying -----

    private void retreat(ServerLevel level) {
        removeSummons(level);
        if (fromInvasion) {
            Invasion.retreat(level.getServer(), FinaleState.get(level.getServer()), true);
        }
        if (!isRemoved()) {
            discard();
        }
    }

    private void removeSummons(ServerLevel level) {
        for (UUID id : summons) {
            Entity summoned = level.getEntity(id);
            if (summoned != null && summoned.isAlive()) {
                level.sendParticles(ParticleTypes.LARGE_SMOKE, summoned.getX(), summoned.getY() + 1, summoned.getZ(), 10, 0.3, 0.6, 0.3, 0.02);
                summoned.discard();
            }
        }
        summons.clear();
    }

    @Override
    public void die(DamageSource source) {
        // Vanilla drops the experience when the killing blow lands; the loot table follows there too.
        this.xpReward = CommonConfig.get().finale().experience();
        super.die(source);
        if (level() instanceof ServerLevel level) {
            releaseGrip(0);
            removeSummons(level);
            Vec3 at = home == null ? position() : home;
            double range = CommonConfig.get().finale().arenaRadius() + 16;
            for (ServerPlayer player : level.players()) {
                if (Mth.square(player.getX() - at.x) + Mth.square(player.getZ() - at.z) <= range * range) {
                    FinaleAdvancements.grant(player, FinaleAdvancements.SLAIN);
                }
            }
            if (fromInvasion) {
                Invasion.defeated(level.getServer());
            }
        }
    }

    @Override
    protected void tickDeath() {
        deathTime++;
        if (!(level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 centre = Limbs.centre(position());
        if (deathTime % 4 == 0) {
            level.sendParticles(ParticleTypes.EXPLOSION,
                    centre.x + (random.nextDouble() - 0.5) * 8, centre.y + (random.nextDouble() - 0.5) * 8, centre.z + (random.nextDouble() - 0.5) * 8,
                    1, 0, 0, 0, 0);
            level.sendParticles(FLESH, centre.x, centre.y, centre.z, 20, 3, 3, 3, 0);
        }
        // Sinking to the ground as it comes apart.
        double ground = groundUnder(level, getX(), getZ());
        if (getY() > ground - 1) {
            setPos(getX(), Math.max(ground - 1, getY() - 0.12), getZ());
        }
        bossEvent.setProgress(0);
        if (deathTime >= DEATH_TICKS && !isRemoved()) {
            level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, centre.x, centre.y, centre.z, 3, 2, 2, 2, 0);
            dropHeart(level, centre);
            remove(RemovalReason.KILLED);
        }
    }

    /** The Heart of the Pale: what the cleansing is started with. It doesn't burn, vanish or break. */
    private void dropHeart(ServerLevel level, Vec3 centre) {
        double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(centre.x), Mth.floor(centre.z));
        ItemEntity heart = new ItemEntity(level, centre.x, Math.max(ground, getY()) + 1.5, centre.z, new ItemStack(FinaleRegistry.HEART_OF_THE_PALE.get()));
        heart.setUnlimitedLifetime();
        heart.setInvulnerable(true);
        heart.setGlowingTag(true);
        heart.setDeltaMovement(0, 0.25, 0);
        level.addFreshEntity(heart);
    }

    @Override
    public void remove(RemovalReason reason) {
        super.remove(reason);
        bossEvent.removeAllPlayers();
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player) {
        super.stopSeenByPlayer(player);
        bossEvent.removePlayer(player);
    }

    // ----- GeckoLib -----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 8, state ->
                state.setAndContinue(isDeadOrDying() ? DEATH : getPhase() >= 4 ? CRAWL : IDLE)));
        controllers.add(new AnimationController<>(this, "seam", 0, state ->
                state.setAndContinue(getPhase() >= 3 ? SEAM_OPEN : SEAM_SHUT)));
        controllers.add(new AnimationController<>(this, "eye", 0, state ->
                state.setAndContinue(eyeOpen() && !isDeadOrDying() ? EYE_OPEN : EYE_SHUT)));
        controllers.add(new AnimationController<>(this, "face", 4, state -> state.setAndContinue(FACE_IDLE))
                .triggerableAnim("scream", FACE_SCREAM));
        controllers.add(new AnimationController<>(this, "hand", 0, state ->
                state.setAndContinue(gripping() ? HAND_GRAB : HAND_OPEN)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
