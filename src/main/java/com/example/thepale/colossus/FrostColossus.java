package com.example.thepale.colossus;

import com.example.thepale.config.CommonConfig;
import com.example.thepale.mixin.LivingEntityAccessor;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.entity.PartEntity;
import net.neoforged.neoforge.fluids.FluidType;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * The Frost Colossus: a peaceful giant of stone brick, ice and snow, some thirty blocks tall, that wanders the cold
 * island. It attacks nobody and (by default) nothing can hurt it.
 *
 * <p>It is built like the ender dragon: the entity itself is a small box at its feet that can't be clicked, and its
 * legs, hips, chest, shoulders, head, arms and hands are separate hitboxes ({@link ColossusPart}) that are solid,
 * so nobody walks through it and any of them can be clicked.
 *
 * <p>It doesn't use the game's pathfinding or physics. The server picks a place on the land of its range
 * ({@link ColossusRange}), turns it toward that place and moves it in a straight line, looking at the ground ahead
 * as it goes: a step of up to {@value #MAX_STEP} blocks is climbed, water up to {@value #MAX_WADE} deep is waded,
 * and at anything else it stops and turns away. Its feet are walked separately ({@link ColossusGait}) and its body
 * stands at the height its feet give it.
 *
 * <p>Its pose is worked out the same way on both sides ({@link ColossusPose}): the server needs it for the hitboxes,
 * the client for the model's bones.
 */
public class FrostColossus extends PathfinderMob implements GeoEntity {
    private static final EntityDataAccessor<Byte> DATA_ACTIVITY = SynchedEntityData.defineId(FrostColossus.class, EntityDataSerializers.BYTE);
    /** Its heading: sent whole, since the game only sends an entity's yaw in steps of 1.4 degrees. */
    private static final EntityDataAccessor<Float> DATA_YAW = SynchedEntityData.defineId(FrostColossus.class, EntityDataSerializers.FLOAT);
    /** Its walking speed, in blocks a tick (the server's config; the client's feet need it). */
    private static final EntityDataAccessor<Float> DATA_FULL_SPEED = SynchedEntityData.defineId(FrostColossus.class, EntityDataSerializers.FLOAT);
    /** Where its head is turned, against its body: degrees to its left, and degrees up. */
    private static final EntityDataAccessor<Float> DATA_LOOK_YAW = SynchedEntityData.defineId(FrostColossus.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> DATA_LOOK_PITCH = SynchedEntityData.defineId(FrostColossus.class, EntityDataSerializers.FLOAT);

    /** What its hand is doing ({@link HandPhase}) and which hand it is (-1 the left, 1 the right). */
    private static final EntityDataAccessor<Byte> DATA_HAND_PHASE = SynchedEntityData.defineId(FrostColossus.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> DATA_HAND_SIDE = SynchedEntityData.defineId(FrostColossus.class, EntityDataSerializers.BYTE);
    /** Where the open palm lies on the ground when it is offered: the place somebody sits in it. */
    private static final EntityDataAccessor<Vector3f> DATA_PALM = SynchedEntityData.defineId(FrostColossus.class, EntityDataSerializers.VECTOR3);
    /** The entity ids of whoever is in the hand and on the left and the right shoulder (0: nobody). */
    private static final EntityDataAccessor<Integer> DATA_HELD = SynchedEntityData.defineId(FrostColossus.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEAT_LEFT = SynchedEntityData.defineId(FrostColossus.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SEAT_RIGHT = SynchedEntityData.defineId(FrostColossus.class, EntityDataSerializers.INT);
    /** How many may ride it (the server's config; the client's hint needs it). */
    private static final EntityDataAccessor<Byte> DATA_MAX_RIDERS = SynchedEntityData.defineId(FrostColossus.class, EntityDataSerializers.BYTE);

    /**
     * What the colossus looks and sounds like on a client beyond its model: footfalls, falling snow, the screen shake.
     * Set by the client; a dedicated server keeps the one that does nothing.
     */
    public interface ClientEffects {
        /** A foot came down at this place, this hard (0 to 1). */
        void footfall(FrostColossus colossus, double x, double y, double z, float yaw, double weight);

        /** A tick of the colossus on the client. */
        void tick(FrostColossus colossus);
    }

    public static ClientEffects clientEffects = new ClientEffects() {
        @Override
        public void footfall(FrostColossus colossus, double x, double y, double z, float yaw, double weight) {}

        @Override
        public void tick(FrostColossus colossus) {}
    };

    /** What it is doing with its whole body. */
    public enum Activity { AWAKE, SITTING_DOWN, ASLEEP, STANDING_UP }

    /**
     * Lifting somebody onto a shoulder, and setting them down again.
     * <ul>
     * <li>Up: {@code TURN} (it stops and turns so that the hand can reach them), {@code OFFER} (it bows and lays its
     * open hand on the ground), {@code WAIT} (until they are at the hand; then they are in it), {@code LIFT} (the
     * hand goes up to the shoulder), {@code SEAT} (they shift from the palm onto the shoulder), {@code RETURN}.</li>
     * <li>Down: {@code TURN} (it stops), {@code FETCH} (the hand goes up, empty), {@code TAKE} (they shift onto the
     * palm), {@code LOWER} (it bows and brings the hand down), {@code RELEASE} (they step off), {@code RETURN}.</li>
     * </ul>
     */
    public enum HandPhase {
        NONE, TURN, OFFER, WAIT, LIFT, SEAT, FETCH, TAKE, LOWER, RELEASE, RETURN;

        /** The hand is (going) down on the ground, and the body bowed over it. */
        boolean down() {
            return this == OFFER || this == WAIT || this == LOWER || this == RELEASE;
        }

        /** The hand is (going) up at the shoulder. */
        boolean up() {
            return this == LIFT || this == SEAT || this == FETCH || this == TAKE;
        }
    }

    /** The length of the sit_down and stand_up animations. */
    private static final int SIT_TICKS = 60;
    /** The highest step it climbs and the deepest water it wades, in blocks. */
    private static final int MAX_STEP = 4;
    private static final int MAX_WADE = 3;
    /** How fast it turns as it walks and on the spot, in degrees a tick. */
    private static final float WALK_TURN = 0.7f;
    private static final float SPOT_TURN = 0.9f;
    /** It follows a player this near with its head. */
    private static final double WATCH_DISTANCE = 24;
    private static final float LOOK_SIDEWAYS = 62;
    private static final float LOOK_DOWN = -46;
    private static final float LOOK_UP = 30;
    /** How long the body takes to bow, the arm to reach out and the hand to go between the ground and the shoulder. */
    private static final double BOW_TICKS = 45;
    private static final double REACH_TICKS = 40;
    private static final double CARRY_TICKS = 64;
    /** How long somebody takes to shift between the palm and the shoulder, and to be gathered into the palm. */
    private static final int SHIFT_TICKS = 18;
    private static final int GATHER_TICKS = 12;
    /** Where the hand can lie on the ground, from the colossus, in blocks: this far to its side and ahead, give or take. */
    private static final double OFFER_SIDE = 8.5;
    private static final double OFFER_AHEAD = 10;
    private static final double OFFER_SLACK = 5.5;
    /** The palm is this far above the ground the hand lies on (the hand's thickness), in blocks. */
    private static final double PALM_HEIGHT = 2 * ColossusPose.PALM_OUT * ColossusPose.PX + 0.05;
    /** Whoever is this near the offered palm is gathered into it. */
    private static final double GATHER_DISTANCE = 5.5;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SIT_DOWN = RawAnimation.begin().thenPlayAndHold("sit_down");
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("sleep");
    private static final RawAnimation STAND_UP = RawAnimation.begin().thenPlayAndHold("stand_up");
    private static final RawAnimation LOOK_AROUND = RawAnimation.begin().thenPlay("look_around");
    private static final RawAnimation LOOK_SKY = RawAnimation.begin().thenPlay("look_sky");
    private static final RawAnimation SHAKE_HEAD = RawAnimation.begin().thenPlay("shake_head");
    private static final RawAnimation NOD = RawAnimation.begin().thenPlay("nod");
    private static final RawAnimation[] OPEN_HAND = {
        RawAnimation.begin().thenPlayAndHold("offer_hand_left"), RawAnimation.begin().thenPlayAndHold("offer_hand")};
    private static final RawAnimation[] CRADLE = {
        RawAnimation.begin().thenPlayAndHold("cradle_left"), RawAnimation.begin().thenPlayAndHold("cradle")};
    private static final RawAnimation[] REST_HAND = {
        RawAnimation.begin().thenPlayAndHold("rest_hand_left"), RawAnimation.begin().thenPlayAndHold("rest_hand")};

    private final AnimatableInstanceCache geoCache = GeckoLibUtil.createInstanceCache(this);
    private final ColossusPart[] parts = new ColossusPart[ColossusPose.PART_COUNT];

    // Worked out alike on both sides.
    private final ColossusGait gait = new ColossusGait();
    /** The pose at the end of this tick (the hitboxes), and a second one for whoever asks for a pose between ticks. */
    private final ColossusPose tickPose = new ColossusPose();
    private final ColossusPose framePose = new ColossusPose();
    private int activityTicks;
    private double lastX;
    private double lastZ;
    /** How fast it is walking, 0 to 1, smoothed. */
    private double pace;
    private double paceOld;
    private float lookYaw;
    private float lookYawOld;
    private float lookPitch;
    private float lookPitchOld;

    /** How far it is bowed, its arm reached out and its hand carried up to the shoulder, each 0 to 1. */
    private double bow;
    private double bowOld;
    private double reach;
    private double reachOld;
    private double carry;
    private double carryOld;
    private int handTicks;
    /** Where the one being gathered into the palm was when the hand took them. */
    @Nullable
    private Vec3 gatheredFrom;

    // The client's own, eased copy of the heading.
    private boolean yawSeen;
    /** How deep the snow has piled on it while it slept, 0 to 1 (the client's, for the model). */
    private double snow;
    private double snowOld;

    // The server's: where it is going.
    private boolean hasWaypoint;
    private double waypointX;
    private double waypointZ;
    private int pause = 40;
    private double speed;
    private float turn;
    /** How many times in a row it found no way on: it looks behind itself sooner. */
    private int blocked;
    private boolean wantsRest;
    /** Put down by hand outside its range: it stays near this place. */
    @Nullable
    private BlockPos anchor;
    @Nullable
    private LivingEntity noticed;
    private int noticedTicks;
    private int headBusy;
    private int humCooldown;
    /** Ticks until it sighs. */
    private int sighIn = 600;
    /** Whether it was night (or a thunderstorm) when last looked: it lies down and gets up as that changes. */
    @Nullable
    private Boolean wasNight;
    /** Who the hand is busy with, whether it is setting them down, and where it should turn to for them. */
    @Nullable
    private UUID handPlayer;
    private boolean settingDown;
    private float handYaw;
    /** Who sits on the left and the right shoulder, who of them has asked to get down, and who held jump last tick. */
    private final UUID[] seats = new UUID[2];
    private final boolean[] wantsDown = new boolean[2];
    private final boolean[] heldJump = new boolean[2];
    private int steerCooldown;
    /** Where a rider is put when they get off: set just before they do. Otherwise they are left where they sit. */
    @Nullable
    private Vec3 dismountTo;

    public FrostColossus(EntityType<? extends FrostColossus> type, Level level) {
        super(type, level);
        for (int i = 0; i < parts.length; i++) {
            parts[i] = new ColossusPart(this, i);
        }
        this.noPhysics = true;
        this.noCulling = true;
        this.setNoGravity(true);
        this.setPersistenceRequired();
        this.xpReward = 0;
        // As for the ender dragon: the parts' ids follow the colossus's, so both sides agree on them.
        this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 400.0)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
                .add(Attributes.ARMOR, 20.0);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(DATA_ACTIVITY, (byte) 0);
        builder.define(DATA_YAW, 0f);
        builder.define(DATA_FULL_SPEED, 0.1f);
        builder.define(DATA_LOOK_YAW, 0f);
        builder.define(DATA_LOOK_PITCH, 0f);
        builder.define(DATA_HAND_PHASE, (byte) 0);
        builder.define(DATA_HAND_SIDE, (byte) 1);
        builder.define(DATA_PALM, new Vector3f());
        builder.define(DATA_HELD, 0);
        builder.define(DATA_SEAT_LEFT, 0);
        builder.define(DATA_SEAT_RIGHT, 0);
        builder.define(DATA_MAX_RIDERS, (byte) 2);
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTIVITY.equals(key) && level().isClientSide) {
            // Just come into view asleep: it isn't shown sitting down first.
            activityTicks = tickCount < 2 ? SIT_TICKS : 0;
        }
        if (DATA_HAND_PHASE.equals(key) && level().isClientSide) {
            handTicks = 0;
            if (handPhase() != HandPhase.LIFT) {
                gatheredFrom = null;
            }
        }
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

    /** The colossus itself can't be clicked; its parts can. */
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
    public void push(double x, double y, double z) {}

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushedByFluid() {
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
    public boolean canBeLeashed() {
        return false;
    }

    @Override
    public boolean canDrownInFluidType(FluidType type) {
        return false;
    }

    @Override
    public boolean canFreeze() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source) {
        return false;
    }

    @Override
    public void makeStuckInBlock(BlockState state, Vec3 motion) {}

    @Override
    public void thunderHit(ServerLevel level, LightningBolt lightning) {}

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 384.0 * 384.0;
    }

    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(20, 0, 20).expandTowards(0, 34, 0);
    }

    @Override
    protected boolean shouldDropLoot() {
        return false;
    }

    @Nullable
    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return null;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("Asleep", activity() == Activity.ASLEEP || activity() == Activity.SITTING_DOWN);
        if (anchor != null) {
            tag.put("Anchor", NbtUtils.writeBlockPos(anchor));
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean("Asleep")) {
            entityData.set(DATA_ACTIVITY, (byte) Activity.ASLEEP.ordinal());
            activityTicks = SIT_TICKS;
            wantsRest = true;
        }
        anchor = NbtUtils.readBlockPos(tag, "Anchor").orElse(null);
    }

    // ----- State both sides see -----

    public Activity activity() {
        return Activity.values()[Mth.clamp(entityData.get(DATA_ACTIVITY), 0, Activity.values().length - 1)];
    }

    private void setActivity(Activity activity) {
        entityData.set(DATA_ACTIVITY, (byte) activity.ordinal());
        activityTicks = 0;
    }

    /** {@return how far it has sat down, 0 (standing) to 1} */
    public double sitting(float partialTick) {
        double progress = Mth.clamp((activityTicks + partialTick) / SIT_TICKS, 0, 1);
        progress = progress * progress * (3 - 2 * progress);
        return switch (activity()) {
            case AWAKE -> 0;
            case SITTING_DOWN -> progress;
            case ASLEEP -> 1;
            case STANDING_UP -> 1 - progress;
        };
    }

    public float yaw(float partialTick) {
        return Mth.rotLerp(partialTick, yRotO, getYRot());
    }

    /** {@return where the head is turned against the body, in radians as its bone takes them: to the left, and up} */
    public float lookYaw(float partialTick) {
        return Mth.lerp(partialTick, lookYawOld, lookYaw) * Mth.DEG_TO_RAD;
    }

    public float lookPitch(float partialTick) {
        return Mth.lerp(partialTick, lookPitchOld, lookPitch) * Mth.DEG_TO_RAD;
    }

    public HandPhase handPhase() {
        return HandPhase.values()[Mth.clamp(entityData.get(DATA_HAND_PHASE), 0, HandPhase.values().length - 1)];
    }

    /** {@return the hand that is busy: -1 the left, 1 the right} */
    public int handSide() {
        return entityData.get(DATA_HAND_SIDE) < 0 ? -1 : 1;
    }

    private void setHandPhase(HandPhase phase) {
        entityData.set(DATA_HAND_PHASE, (byte) phase.ordinal());
        handTicks = 0;
        if (phase != HandPhase.LIFT) {
            gatheredFrom = null;
        }
    }

    /** {@return whether a click on it now would have it lift somebody up, as far as a client can tell} */
    public boolean offersRide() {
        int taken = (entityData.get(DATA_SEAT_LEFT) != 0 ? 1 : 0) + (entityData.get(DATA_SEAT_RIGHT) != 0 ? 1 : 0);
        return isAlive() && activity() == Activity.AWAKE && handPhase() == HandPhase.NONE && taken < entityData.get(DATA_MAX_RIDERS);
    }

    /** {@return whether it is walking: the walk animation plays} */
    public boolean walking() {
        return pace > 0.03;
    }

    /** A place it stays near instead of its own range (put down by hand somewhere else), or null. */
    public void setAnchor(@Nullable BlockPos anchor) {
        this.anchor = anchor;
    }

    /** Sent to sleep or woken by a command. */
    public void setRest(boolean rest) {
        wantsRest = rest;
    }

    /**
     * {@return its pose at this partial tick} (1 for the end of the tick). The same object is filled in again at
     * every call.
     */
    public ColossusPose pose(float partialTick) {
        fillPose(framePose, partialTick);
        return framePose;
    }

    private void fillPose(ColossusPose pose, float partialTick) {
        Vec3 base = getPosition(partialTick);
        float yaw = yaw(partialTick);
        // The renderer turns the model by 180 - yaw; model space is the world turned back by that.
        double turnBy = Math.toRadians(180 - yaw);
        double cos = Math.cos(turnBy);
        double sin = Math.sin(turnBy);
        for (int foot = 0; foot < 2; foot++) {
            double x = Mth.lerp(partialTick, gait.xOld[foot], gait.x[foot]) - base.x;
            double y = Mth.lerp(partialTick, gait.yOld[foot], gait.y[foot]) - base.y;
            double z = Mth.lerp(partialTick, gait.zOld[foot], gait.z[foot]) - base.z;
            if (!gait.placed()) {
                pose.foot[foot][0] = foot == 0 ? -ColossusPose.HIP_X : ColossusPose.HIP_X;
                pose.foot[foot][1] = 0;
                pose.foot[foot][2] = 0;
                pose.footYaw[foot] = 0;
                pose.footSwing[foot] = 0;
                continue;
            }
            pose.foot[foot][0] = (x * cos - z * sin) / ColossusPose.PX;
            pose.foot[foot][1] = y / ColossusPose.PX;
            pose.foot[foot][2] = (x * sin + z * cos) / ColossusPose.PX;
            // A greater yaw is a turn to the right; a bone's turn about y is to the left.
            pose.footYaw[foot] = -Math.toRadians(Mth.wrapDegrees(Mth.rotLerp(partialTick, gait.yawOld[foot], gait.yaw[foot]) - yaw));
            pose.footSwing[foot] = Mth.lerp(partialTick, gait.raisedOld[foot], gait.raised[foot]);
        }
        pose.speed = Mth.lerp(partialTick, paceOld, pace);
        pose.sit = sitting(partialTick);
        pose.bow = Mth.lerp(partialTick, bowOld, bow);
        pose.time = tickCount + partialTick;
        pose.reach = Mth.lerp(partialTick, reachOld, reach);
        pose.reachSide = pose.reach > 0 ? handSide() : 0;
        pose.palmUp = Math.min(1, pose.reach * 1.6);
        pose.carry = Mth.lerp(partialTick, carryOld, carry);
        Vector3f palm = entityData.get(DATA_PALM);
        double palmX = palm.x - base.x;
        double palmZ = palm.z - base.z;
        pose.palmGround[0] = (palmX * cos - palmZ * sin) / ColossusPose.PX;
        pose.palmGround[1] = (palm.y - base.y) / ColossusPose.PX;
        pose.palmGround[2] = (palmX * sin + palmZ * cos) / ColossusPose.PX;
        pose.solve();
    }

    /** {@return a point of the pose (model pixels) as a place in the world} */
    public Vec3 toWorld(double[] point, float partialTick) {
        Vec3 base = getPosition(partialTick);
        double turnBy = Math.toRadians(180 - yaw(partialTick));
        double cos = Math.cos(turnBy);
        double sin = Math.sin(turnBy);
        return new Vec3(
                base.x + (point[0] * cos + point[2] * sin) * ColossusPose.PX,
                base.y + point[1] * ColossusPose.PX,
                base.z + (-point[0] * sin + point[2] * cos) * ColossusPose.PX);
    }

    // ----- Ticking -----

    @Override
    public void aiStep() {
        super.aiStep();
        // Nothing but its own walking moves it.
        setDeltaMovement(Vec3.ZERO);
        activityTicks++;
        handTicks++;
        lookYawOld = lookYaw;
        lookPitchOld = lookPitch;
        paceOld = pace;
        if (level().isClientSide) {
            clientStep();
        } else if (isAlive()) {
            serverStep((ServerLevel) level());
        }
        if (!gait.placed()) {
            lastX = getX();
            lastZ = getZ();
        }
        double movedX = getX() - lastX;
        double movedZ = getZ() - lastZ;
        lastX = getX();
        lastZ = getZ();
        double fullSpeed = entityData.get(DATA_FULL_SPEED);
        gait.tick(level(), getX(), getY(), getZ(), getYRot(), movedX, movedZ, fullSpeed, activity() == Activity.AWAKE);
        if (gait.landed >= 0 && isAlive()) {
            int foot = gait.landed;
            if (level().isClientSide) {
                clientEffects.footfall(this, gait.x[foot], gait.y[foot], gait.z[foot], gait.yaw[foot], gait.landedWeight);
            } else {
                flatten((ServerLevel) level(), gait.x[foot], gait.y[foot], gait.z[foot], gait.yaw[foot]);
            }
        }
        double moved = Math.sqrt(movedX * movedX + movedZ * movedZ);
        pace += (Mth.clamp(moved / Math.max(fullSpeed, 1.0E-4), 0, 1) - pace) * 0.2;
        if (!level().isClientSide && isAlive()) {
            standOnFeet();
        }
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
        moveHand();
        fillPose(tickPose, 1f);
        moveParts();
        if (level().isClientSide) {
            keepPlayerOut();
            clientEffects.tick(this);
        }
    }

    /** The bow, the reach and the carry ease toward what the hand's phase asks for, alike on both sides. */
    private void moveHand() {
        HandPhase phase = handPhase();
        bowOld = bow;
        reachOld = reach;
        carryOld = carry;
        bow = approach(bow, phase.down() ? 1 : 0, 1 / BOW_TICKS);
        boolean reaching = phase != HandPhase.NONE && phase != HandPhase.TURN && phase != HandPhase.RETURN;
        if (reach == 0 && reaching) {
            // The arm sets out for the ground or for the shoulder; where the hand was last doesn't matter.
            carry = phase.up() ? 1 : 0;
            carryOld = carry;
        }
        reach = approach(reach, reaching ? 1 : 0, 1 / REACH_TICKS);
        if (phase.up()) {
            carry = approach(carry, 1, 1 / CARRY_TICKS);
        } else if (phase.down()) {
            carry = approach(carry, 0, 1 / CARRY_TICKS);
        }
    }

    private static double approach(double value, double target, double step) {
        return value < target ? Math.min(target, value + step) : Math.max(target, value - step);
    }

    /** The client: the heading and the head ease toward what the server sent. */
    private void clientStep() {
        float sent = entityData.get(DATA_YAW);
        if (!yawSeen) {
            yawSeen = true;
            setYRot(sent);
            yRotO = sent;
            lookYaw = entityData.get(DATA_LOOK_YAW);
            lookPitch = entityData.get(DATA_LOOK_PITCH);
            lookYawOld = lookYaw;
            lookPitchOld = lookPitch;
        } else {
            // The game eased the yaw toward its own, coarser copy in the meantime: start again from last tick's.
            setYRot(yRotO + Mth.wrapDegrees(sent - yRotO) * 0.4f);
        }
        lookYaw += (entityData.get(DATA_LOOK_YAW) - lookYaw) * 0.09f;
        lookPitch += (entityData.get(DATA_LOOK_PITCH) - lookPitch) * 0.09f;
        snowOld = snow;
        switch (activity()) {
            // Come into view asleep: it has been asleep a while.
            case ASLEEP -> snow = tickCount < 3 ? 1 : Math.min(1, snow + 1 / 1800.0);
            case SITTING_DOWN -> snow = Math.min(1, snow + 1 / 1800.0);
            // Shaken off as it gets up.
            case STANDING_UP -> snow = activityTicks < 10 ? snow : Math.max(0, snow - 1 / 30.0);
            case AWAKE -> snow = Math.max(0, snow - 1 / 200.0);
        }
        if (tickCount < 3) {
            snowOld = snow;
        }
    }

    /** {@return how deep the snow lies on it from its sleep, 0 to 1} (client only) */
    public double snowCover(float partialTick) {
        return Mth.lerp(partialTick, snowOld, snow);
    }

    /** {@return whether its eyes are dark: it has nodded off, or not yet woken} */
    public boolean eyesClosed(float partialTick) {
        return switch (activity()) {
            case AWAKE -> false;
            case ASLEEP -> true;
            case SITTING_DOWN -> activityTicks + partialTick > SIT_TICKS * 0.75;
            case STANDING_UP -> activityTicks + partialTick < SIT_TICKS * 0.3;
        };
    }

    private void serverStep(ServerLevel level) {
        CommonConfig.Colossus config = CommonConfig.get().colossus();
        if (!config.enabled()) {
            discard();
            return;
        }
        float fullSpeed = (float) (config.walkSpeed() / 20.0);
        if (entityData.get(DATA_FULL_SPEED) != fullSpeed) {
            entityData.set(DATA_FULL_SPEED, fullSpeed);
        }
        byte maxRiders = (byte) Mth.clamp(config.maxRiders(), 0, 2);
        if (entityData.get(DATA_MAX_RIDERS) != maxRiders) {
            entityData.set(DATA_MAX_RIDERS, maxRiders);
        }
        if (humCooldown > 0) {
            humCooldown--;
        }
        if (headBusy > 0) {
            headBusy--;
        }
        if (tickCount % 20 == 0) {
            keepHours(level, config);
        }
        if (--sighIn <= 0) {
            sigh(level);
        }
        switch (activity()) {
            case AWAKE -> {
                tickRiders(level, config);
                if (handPhase() != HandPhase.NONE) {
                    tickHand(level, fullSpeed);
                } else if (wantsRest && getPassengers().isEmpty() && speed == 0 && !hasWaypoint && gait.settled(getX(), getZ(), getYRot())) {
                    setActivity(Activity.SITTING_DOWN);
                } else {
                    wander(level, fullSpeed);
                }
            }
            case SITTING_DOWN -> {
                if (activityTicks >= SIT_TICKS) {
                    setActivity(Activity.ASLEEP);
                }
            }
            case ASLEEP -> {
                if (!wantsRest) {
                    setActivity(Activity.STANDING_UP);
                }
            }
            case STANDING_UP -> {
                if (activityTicks >= SIT_TICKS) {
                    setActivity(Activity.AWAKE);
                    pause = 40 + random.nextInt(60);
                }
            }
        }
        if (activity() != Activity.AWAKE && handPhase() != HandPhase.NONE) {
            endHand();
        }
        if (tickCount % 4 == 0) {
            watch(level);
        }
        if (entityData.get(DATA_YAW) != getYRot()) {
            entityData.set(DATA_YAW, getYRot());
        }
    }

    /**
     * It lies down when night falls or a thunderstorm comes, and gets up when it is over. Only as that changes: put
     * to sleep or woken by a command or a click, it stays so until the next dusk or dawn.
     */
    private void keepHours(ServerLevel level, CommonConfig.Colossus config) {
        long time = level.getDayTime() % 24000;
        boolean night = (time >= 13000 && time < 23300) || level.isThundering();
        if (wasNight == null || night != wasNight) {
            if (config.sleepAtNight() && (wasNight != null || night)) {
                wantsRest = night;
            }
            wasNight = night;
        }
    }

    /** Now and then a long, low breath: awake, a sigh; asleep, slower and softer. */
    private void sigh(ServerLevel level) {
        boolean asleep = activity() == Activity.ASLEEP;
        sighIn = asleep ? 300 + random.nextInt(300) : 1200 + random.nextInt(1800);
        if (activity() == Activity.SITTING_DOWN || activity() == Activity.STANDING_UP) {
            return;
        }
        Vec3 head = toWorld(tickPose.part[ColossusPose.HEAD], 1f);
        level.playSound(null, head.x, head.y, head.z, ColossusRegistry.SIGH.get(), getSoundSource(),
                asleep ? 2.5f : 4f, (asleep ? 0.8f : 0.95f) + random.nextFloat() * 0.1f);
    }

    /** Where a foot came down: snow layers, grass, flowers and leaves under it are trodden flat, if the config allows. */
    private void flatten(ServerLevel level, double x, double y, double z, float yaw) {
        if (!CommonConfig.get().colossus().breakSoftBlocks() || !net.neoforged.neoforge.event.EventHooks.canEntityGrief(level, this)) {
            return;
        }
        double sin = Math.sin(Math.toRadians(yaw));
        double cos = Math.cos(Math.toRadians(yaw));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double along = -1.5; along <= 1.5; along += 1) {
            for (double across = -1; across <= 1; across += 1) {
                int blockX = Mth.floor(x - sin * along + cos * across);
                int blockZ = Mth.floor(z + cos * along + sin * across);
                for (int up = -1; up <= 2; up++) {
                    pos.set(blockX, Mth.floor(y) + up, blockZ);
                    BlockState state = level.getBlockState(pos);
                    if (!state.isAir() && (state.canBeReplaced() || state.is(net.minecraft.tags.BlockTags.LEAVES))
                            && state.getFluidState().isEmpty()) {
                        level.destroyBlock(pos, false, this);
                    }
                }
            }
        }
    }

    // ----- Walking (server) -----

    private void wander(ServerLevel level, double fullSpeed) {
        float yaw = getYRot();
        double want = 0;
        float wantTurn = 0;
        if (wantsRest && getPassengers().isEmpty()) {
            hasWaypoint = false;
        }
        if (hasWaypoint) {
            double dx = waypointX - getX();
            double dz = waypointZ - getZ();
            double distance = Math.sqrt(dx * dx + dz * dz);
            if (distance < 2.5) {
                arrive();
            } else {
                float off = Mth.wrapDegrees((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) - yaw);
                float most = speed > 0.01 ? WALK_TURN : SPOT_TURN;
                wantTurn = Mth.clamp(off * 0.12f, -most, most);
                // Full speed when it faces the way it is going, none while it is still turning toward it.
                want = fullSpeed * Mth.clamp(1.5 - Math.abs(off) / 40.0, 0, 1);
                // And slowing in time to stop there.
                want = Math.min(want, Math.max(fullSpeed * 0.15, Math.sqrt(2 * (fullSpeed / 30) * Math.max(distance - 2, 0))));
                if (tickCount % 5 == 0 && speed > 0.01 && clearFor(level, getX(), getZ(), yaw, 7) < 7) {
                    // A cliff, a wall, deep water or the edge of its range: stop and look for another way.
                    hasWaypoint = false;
                    blocked++;
                    pause = 30 + random.nextInt(30);
                }
            }
        } else if (pause > 0) {
            pause--;
        } else if (!wantsRest || !getPassengers().isEmpty()) {
            chooseWaypoint(level);
        }
        drive(want, wantTurn, fullSpeed);
    }

    /** Eases its speed and its turning toward what is wanted, and moves it. */
    private void drive(double want, float wantTurn, double fullSpeed) {
        float yaw = getYRot();
        turn += (wantTurn - turn) * 0.12f;
        if (wantTurn == 0 && Math.abs(turn) < 0.01f) {
            turn = 0;
        }
        speed += Mth.clamp(want - speed, -fullSpeed / 30, fullSpeed / 45);
        if (want == 0 && speed < 1.0E-4) {
            speed = 0;
        }
        yaw = Mth.wrapDegrees(yaw + turn);
        setYRot(yaw);
        double radians = Math.toRadians(yaw);
        setPos(getX() - Math.sin(radians) * speed, getY(), getZ() + Math.cos(radians) * speed);
    }

    private void arrive() {
        hasWaypoint = false;
        blocked = 0;
        pause = 80 + random.nextInt(260);
        float whim = random.nextFloat();
        if (whim < 0.3f) {
            playHead("look_around", 135);
        } else if (whim < 0.45f) {
            playHead("look_sky", 140);
        }
    }

    /** Plays one of the head's animations; meanwhile the head doesn't follow anybody. */
    private void playHead(String animation, int ticks) {
        headBusy = ticks;
        triggerAnim("head", animation);
    }

    private void chooseWaypoint(ServerLevel level) {
        boolean lost = !ColossusRange.allowed(getX(), getZ(), anchor);
        double[] heart = anchor != null ? new double[] {anchor.getX(), anchor.getZ()} : ColossusRange.heart();
        for (int attempt = 0; attempt < 6; attempt++) {
            float angle;
            if (lost) {
                // Outside its range: back toward the middle of it.
                angle = (float) (Mth.atan2(-(heart[0] - getX()), heart[1] - getZ()) * Mth.RAD_TO_DEG) + (random.nextFloat() - 0.5f) * 60;
            } else if (blocked >= 2) {
                angle = getYRot() + 180 + (random.nextFloat() - 0.5f) * 200;
            } else {
                // Mostly on ahead, sometimes off to a side.
                angle = getYRot() + (random.nextFloat() - random.nextFloat()) * 120;
            }
            double wanted = 24 + random.nextDouble() * 56;
            double clear = clearFor(level, getX(), getZ(), angle, wanted);
            if (clear >= 14) {
                double radians = Math.toRadians(angle);
                waypointX = getX() - Math.sin(radians) * (clear - 3);
                waypointZ = getZ() + Math.cos(radians) * (clear - 3);
                hasWaypoint = true;
                return;
            }
        }
        blocked++;
        pause = 40 + random.nextInt(60);
    }

    /**
     * {@return how far it could walk from here in this direction, up to {@code distance} blocks}: until the ground
     * steps up or down too far, the water gets too deep, its range ends or the world isn't loaded.
     */
    private double clearFor(ServerLevel level, double fromX, double fromZ, float angle, double distance) {
        double radians = Math.toRadians(angle);
        double stepX = -Math.sin(radians);
        double stepZ = Math.cos(radians);
        boolean lost = !ColossusRange.allowed(fromX, fromZ, anchor);
        double[] heart = anchor != null ? new double[] {anchor.getX(), anchor.getZ()} : ColossusRange.heart();
        double startAway = Mth.square(fromX - heart[0]) + Mth.square(fromZ - heart[1]);
        double before = gait.placed() ? gait.support() : getY();
        // Standing in deeper water than it wades (put there by hand), it may at least walk out through it.
        int wade = Math.max(MAX_WADE, ColossusGround.waterDepth(level, fromX, fromZ));
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double reached = 0;
        for (double along = Math.min(3, distance); reached < distance; along = Math.min(along + 3, distance)) {
            double x = fromX + stepX * along;
            double z = fromZ + stepZ * along;
            if (!ColossusRange.allowed(x, z, anchor)
                    && !(lost && Mth.square(x - heart[0]) + Mth.square(z - heart[1]) < startAway)) {
                break;
            }
            if (!level.isPositionEntityTicking(pos.set(Mth.floor(x), 64, Mth.floor(z)))) {
                break;
            }
            double ground = ColossusGround.underFoot(level, x, z, angle);
            if (Double.isNaN(ground) || Math.abs(ground - before) > MAX_STEP || ColossusGround.waterDepth(level, x, z) > wade) {
                break;
            }
            before = ground;
            reached = along;
        }
        return reached;
    }

    /** {@return what it is doing, for /thepale colossus status} */
    public String status() {
        ServerLevel level = (ServerLevel) level();
        StringBuilder out = new StringBuilder();
        out.append(activity()).append(hasWaypoint ? String.format(", walking to %.0f %.0f", waypointX, waypointZ) : ", no waypoint")
                .append(String.format(", hand %s %s (bow %.2f reach %.2f carry %.2f), riders %d",
                        handPhase(), handSide() < 0 ? "left" : "right", bow, reach, carry, getPassengers().size()))
                .append(String.format(", pause %d, speed %.3f, blocked %d, y %.1f, feet %.1f", pause, speed, blocked, getY(), gait.placed() ? gait.support() : Double.NaN))
                .append(ColossusRange.allowed(getX(), getZ(), anchor) ? ", in range" : ", OUT OF RANGE")
                .append(anchor != null ? ", anchored at " + anchor.toShortString() : "")
                .append(". Clear ahead by direction:");
        for (int i = 0; i < 8; i++) {
            out.append(String.format(" %d:%.0f", i * 45, clearFor(level, getX(), getZ(), i * 45f, 60)));
        }
        return out.toString();
    }

    /** Its body stands at the height its feet give it, easing there. */
    private void standOnFeet() {
        if (!gait.placed()) {
            return;
        }
        double change = (gait.support() - getY()) * 0.12;
        setPos(getX(), getY() + Mth.clamp(change, -0.25, 0.25), getZ());
    }

    // ----- The head (server) -----

    private void watch(ServerLevel level) {
        float wantYaw = 0;
        float wantPitch = 0;
        if (noticedTicks > 0) {
            noticedTicks -= 4;
            if (noticedTicks <= 0 || noticed == null || !noticed.isAlive() || noticed.level() != level) {
                noticed = null;
                noticedTicks = 0;
            }
        }
        LivingEntity target = noticed;
        if (target == null && headBusy <= 0 && activity() == Activity.AWAKE) {
            double nearest = WATCH_DISTANCE * WATCH_DISTANCE;
            for (Player player : level.players()) {
                double distance = Mth.square(player.getX() - getX()) + Mth.square(player.getZ() - getZ());
                // Its riders are out of its sight, up on its shoulders.
                if (distance < nearest && player.isAlive() && !player.isSpectator() && !player.isInvisible() && player.getVehicle() != this) {
                    nearest = distance;
                    target = player;
                }
            }
        }
        if (target != null && activity() == Activity.AWAKE) {
            Vec3 head = toWorld(tickPose.part[ColossusPose.HEAD], 1f);
            Vec3 eyes = target.getEyePosition();
            double dx = eyes.x - head.x;
            double dy = eyes.y - head.y;
            double dz = eyes.z - head.z;
            float toward = Mth.wrapDegrees((float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG) - getYRot());
            // Somebody behind it: it doesn't wring its neck, it looks ahead.
            if (Math.abs(toward) < 125) {
                wantYaw = -Mth.clamp(toward, -LOOK_SIDEWAYS, LOOK_SIDEWAYS);
                wantPitch = Mth.clamp((float) (Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG), LOOK_DOWN, LOOK_UP);
            }
        }
        if (Math.abs(entityData.get(DATA_LOOK_YAW) - wantYaw) > 1 || Math.abs(entityData.get(DATA_LOOK_PITCH) - wantPitch) > 1) {
            entityData.set(DATA_LOOK_YAW, wantYaw);
            entityData.set(DATA_LOOK_PITCH, wantPitch);
        }
    }

    // ----- Being hit and clicked -----

    @Override
    public boolean hurt(DamageSource source, float amount) {
        if (level().isClientSide || !isAlive()) {
            return false;
        }
        Entity attacker = source.getEntity();
        if (attacker instanceof Player player && player.isCreative() && player.isShiftKeyDown() && player.getVehicle() != this) {
            // The one way to take it out of the world by hand: a creative player, sneaking.
            ejectPassengers();
            discard();
            return true;
        }
        if (attacker instanceof LivingEntity living && attacker != this && living.getVehicle() != this) {
            notice(living);
        }
        if (CommonConfig.get().colossus().invulnerable()) {
            return false;
        }
        // Even when it can be hurt, only somebody can hurt it: not a fall, a wall, fire, cold or cactus.
        if (attacker == null && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            return false;
        }
        return super.hurt(source, amount);
    }

    /** It turns its head toward whoever struck it, and hums. */
    private void notice(LivingEntity attacker) {
        if (activity() != Activity.AWAKE) {
            return;
        }
        noticed = attacker;
        noticedTicks = 120;
        headBusy = 0;
        if (humCooldown <= 0) {
            humCooldown = 70;
            Vec3 head = toWorld(tickPose.part[ColossusPose.HEAD], 1f);
            level().playSound(null, head.x, head.y, head.z, ColossusRegistry.HUM.get(), getSoundSource(), 5f, 0.9f + random.nextFloat() * 0.15f);
        }
    }

    /** A click on any of its parts: with an empty hand, it lifts the player onto a shoulder. */
    InteractionResult interactPart(ColossusPart part, Player player, InteractionHand hand) {
        if (hand != InteractionHand.MAIN_HAND || !player.getMainHandItem().isEmpty() || player.isSpectator() || player.getVehicle() == this) {
            return InteractionResult.PASS;
        }
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        CommonConfig.Colossus config = CommonConfig.get().colossus();
        if (!config.enabled() || config.maxRiders() <= 0 || player.isPassenger()) {
            return InteractionResult.PASS;
        }
        if (activity() != Activity.AWAKE) {
            // Asleep it is woken; it gets up, and then it can be asked again.
            if (activity() == Activity.ASLEEP || activity() == Activity.SITTING_DOWN) {
                wantsRest = false;
                tell(player, "waking");
            } else {
                tell(player, "busy");
            }
            return InteractionResult.SUCCESS;
        }
        if (handPhase() != HandPhase.NONE) {
            // Whoever it is already reaching for needn't be told.
            if (!player.getUUID().equals(handPlayer)) {
                tell(player, "busy");
            }
            return InteractionResult.SUCCESS;
        }
        int taken = (seats[0] != null ? 1 : 0) + (seats[1] != null ? 1 : 0);
        if (taken >= Math.min(2, config.maxRiders())) {
            playHead("shake_head", 50);
            tell(player, "full");
            return InteractionResult.SUCCESS;
        }
        // The free shoulder; with both free, the one on the player's side.
        int side = seats[0] != null ? 1 : seats[1] != null ? -1 : toItsRight(player.getX(), player.getZ()) < 0 ? -1 : 1;
        handPlayer = player.getUUID();
        settingDown = false;
        entityData.set(DATA_HAND_SIDE, (byte) side);
        hasWaypoint = false;
        setHandPhase(HandPhase.TURN);
        return InteractionResult.SUCCESS;
    }

    private static void tell(Player player, String message, Object... arguments) {
        player.displayClientMessage(Component.translatable("message.thepale.colossus." + message, arguments), true);
    }

    /** {@return how far to the colossus's right this place is, in blocks (negative: to its left)} */
    private double toItsRight(double x, double z) {
        double turnBy = Math.toRadians(180 - getYRot());
        return (x - getX()) * Math.cos(turnBy) - (z - getZ()) * Math.sin(turnBy);
    }

    /** {@return a place given from the colossus (blocks to its right and ahead of it) as a place in the world, on the ground} */
    private Vec3 groundFromIt(double right, double ahead) {
        double turnBy = Math.toRadians(180 - getYRot());
        double x = getX() + right * Math.cos(turnBy) - ahead * Math.sin(turnBy);
        double z = getZ() - right * Math.sin(turnBy) - ahead * Math.cos(turnBy);
        double feet = gait.placed() ? gait.support() : getY();
        double ground = ColossusGround.height(level(), x, z);
        // Where the ground can't be known or the hand couldn't follow it, the level it stands on.
        return new Vec3(x, Double.isNaN(ground) ? feet : Mth.clamp(ground, feet - 4, feet + 4), z);
    }

    // ----- Riders (server) -----

    @Nullable
    private ServerPlayer rider(int seat) {
        UUID id = seats[seat];
        if (id != null) {
            for (Entity passenger : getPassengers()) {
                if (passenger instanceof ServerPlayer player && player.getUUID().equals(id)) {
                    return player;
                }
            }
        }
        return null;
    }

    private void seatId(int seat, int id) {
        EntityDataAccessor<Integer> key = seat == 0 ? DATA_SEAT_LEFT : DATA_SEAT_RIGHT;
        if (entityData.get(key) != id) {
            entityData.set(key, id);
        }
    }

    /** What the riders ask for: sneaking to be set down, jump to point the way. */
    private void tickRiders(ServerLevel level, CommonConfig.Colossus config) {
        if (steerCooldown > 0) {
            steerCooldown--;
        }
        for (Entity passenger : getPassengers()) {
            // Sneaking would have a rider jump off, from this height. The hand sets them down instead.
            if (passenger instanceof ServerPlayer player && player.isShiftKeyDown()) {
                player.setShiftKeyDown(false);
                for (int seat = 0; seat < 2; seat++) {
                    if (player.getUUID().equals(seats[seat]) && !player.getUUID().equals(handPlayer)) {
                        wantsDown[seat] = true;
                    }
                }
            }
        }
        for (int seat = 0; seat < 2; seat++) {
            ServerPlayer rider = rider(seat);
            if (rider == null) {
                seats[seat] = null;
                wantsDown[seat] = false;
                heldJump[seat] = false;
                seatId(seat, 0);
                continue;
            }
            boolean jump = ((LivingEntityAccessor) rider).thepale$isJumping();
            if (jump && !heldJump[seat] && config.riderSteering() && handPhase() == HandPhase.NONE && steerCooldown <= 0) {
                steerCooldown = 15;
                steer(level, rider);
            }
            heldJump[seat] = jump;
        }
        for (int seat = 0; seat < 2 && handPhase() == HandPhase.NONE; seat++) {
            if (wantsDown[seat] && seats[seat] != null) {
                wantsDown[seat] = false;
                handPlayer = seats[seat];
                settingDown = true;
                entityData.set(DATA_HAND_SIDE, (byte) (seat == 0 ? -1 : 1));
                hasWaypoint = false;
                setHandPhase(HandPhase.TURN);
            }
        }
    }

    /** A rider looked at a place and pressed jump: it goes there, if that is land it may walk on. */
    private void steer(ServerLevel level, ServerPlayer rider) {
        Vec3 eye = rider.getEyePosition();
        Vec3 look = rider.getLookAngle();
        Vec3 place = null;
        // Along their line of sight to the ground; only through chunks that are loaded.
        for (double along = 6; along <= 220; along += 1) {
            double x = eye.x + look.x * along;
            double y = eye.y + look.y * along;
            double z = eye.z + look.z * along;
            double ground = ColossusGround.height(level, x, z);
            if (Double.isNaN(ground)) {
                break;
            }
            if (y <= ground) {
                place = new Vec3(x, ground, z);
                break;
            }
        }
        if (place == null
                || !ColossusRange.allowed(place.x, place.z, anchor)
                || ColossusGround.waterDepth(level, place.x, place.z) > MAX_WADE
                || Mth.square(place.x - getX()) + Mth.square(place.z - getZ()) < 6 * 6) {
            playHead("shake_head", 50);
            tell(rider, "no_way");
            return;
        }
        waypointX = place.x;
        waypointZ = place.z;
        hasWaypoint = true;
        pause = 0;
        blocked = 0;
        playHead("nod", 28);
        // Only they see where they pointed.
        level.sendParticles(rider, ParticleTypes.SNOWFLAKE, true, place.x, place.y + 1.5, place.z, 40, 0.8, 1.5, 0.8, 0.02);
        level.sendParticles(rider, ParticleTypes.END_ROD, true, place.x, place.y + 2.5, place.z, 12, 0.3, 2.0, 0.3, 0.01);
    }

    // ----- The hand (server) -----

    private void tickHand(ServerLevel level, double fullSpeed) {
        int side = handSide();
        int seat = side < 0 ? 0 : 1;
        ServerPlayer player = handPlayer == null ? null : level.getServer().getPlayerList().getPlayer(handPlayer);
        boolean here = player != null && player.isAlive() && !player.isSpectator() && player.level() == level;
        boolean riding = here && player.getVehicle() == this;
        float wantTurn = 0;
        switch (handPhase()) {
            case TURN -> {
                if (settingDown) {
                    if (!riding) {
                        endHand();
                    } else if (speed == 0) {
                        setHandPhase(HandPhase.FETCH);
                    }
                } else if (!here || player.isPassenger() || distanceToSqr(player) > 28 * 28) {
                    endHand();
                } else {
                    // Turn so that they are off to the side of the hand, where it reaches the ground.
                    double dx = player.getX() - getX();
                    double dz = player.getZ() - getZ();
                    float toward = (float) (Mth.atan2(-dx, dz) * Mth.RAD_TO_DEG);
                    float bearing = (float) (Mth.atan2(OFFER_SIDE, OFFER_AHEAD) * Mth.RAD_TO_DEG);
                    float off = dx * dx + dz * dz < 4 * 4 ? 0 : Mth.wrapDegrees(toward - side * bearing - getYRot());
                    if (speed == 0 && (Math.abs(off) < 6 || handTicks > 200)) {
                        offerPalm(player, side);
                        setHandPhase(HandPhase.OFFER);
                    } else if (speed < 0.01) {
                        wantTurn = Mth.clamp(off * 0.12f, -SPOT_TURN, SPOT_TURN);
                    }
                }
            }
            case OFFER -> {
                if (!here || player.isPassenger()) {
                    setHandPhase(HandPhase.RETURN);
                } else if (bow >= 1 && reach >= 1) {
                    setHandPhase(HandPhase.WAIT);
                }
            }
            case WAIT -> {
                if (!here || player.isPassenger() || handTicks > 240) {
                    setHandPhase(HandPhase.RETURN);
                    break;
                }
                Vec3 palm = toWorld(tickPose.palm, 1f);
                boolean near = Mth.square(player.getX() - palm.x) + Mth.square(player.getZ() - palm.z) < GATHER_DISTANCE * GATHER_DISTANCE
                        && Math.abs(player.getY() - (palm.y - PALM_HEIGHT)) < 5;
                if (near) {
                    // Into the hand: from here on they ride, first in the palm.
                    entityData.set(DATA_HELD, player.getId());
                    setHandPhase(HandPhase.LIFT);
                    if (!player.startRiding(this, true)) {
                        entityData.set(DATA_HELD, 0);
                        setHandPhase(HandPhase.RETURN);
                    }
                } else if (handTicks == 30) {
                    tell(player, "come_closer");
                }
            }
            case LIFT -> {
                if (!riding) {
                    entityData.set(DATA_HELD, 0);
                    setHandPhase(HandPhase.RETURN);
                } else if (carry >= 1 && bow <= 0) {
                    setHandPhase(HandPhase.SEAT);
                }
            }
            case SEAT -> {
                if (!riding) {
                    entityData.set(DATA_HELD, 0);
                    setHandPhase(HandPhase.RETURN);
                } else if (handTicks >= SHIFT_TICKS) {
                    seats[seat] = player.getUUID();
                    seatId(seat, player.getId());
                    entityData.set(DATA_HELD, 0);
                    setHandPhase(HandPhase.RETURN);
                    tell(player, CommonConfig.get().colossus().riderSteering() ? "riding" : "riding_plain",
                            Component.keybind("key.sneak"), Component.keybind("key.jump"));
                }
            }
            case FETCH -> {
                if (!riding) {
                    setHandPhase(HandPhase.RETURN);
                } else if (reach >= 1 && carry >= 1) {
                    entityData.set(DATA_HELD, player.getId());
                    seatId(seat, 0);
                    setHandPhase(HandPhase.TAKE);
                }
            }
            case TAKE -> {
                if (!riding) {
                    entityData.set(DATA_HELD, 0);
                    setHandPhase(HandPhase.RETURN);
                } else if (handTicks >= SHIFT_TICKS) {
                    setDownPalm(level, side);
                    setHandPhase(HandPhase.LOWER);
                }
            }
            case LOWER -> {
                if (!riding) {
                    entityData.set(DATA_HELD, 0);
                    setHandPhase(HandPhase.RELEASE);
                } else if (carry <= 0 && bow >= 1) {
                    // Off the hand, onto the ground beside it, on the side away from the colossus.
                    Vec3 palm = toWorld(tickPose.palm, 1f);
                    double awayX = palm.x - getX();
                    double awayZ = palm.z - getZ();
                    double length = Math.max(Math.sqrt(awayX * awayX + awayZ * awayZ), 1.0E-3);
                    double x = palm.x + awayX / length * 3.4;
                    double z = palm.z + awayZ / length * 3.4;
                    double ground = ColossusGround.height(level, x, z);
                    double floor = palm.y - PALM_HEIGHT;
                    dismountTo = new Vec3(x, Double.isNaN(ground) || Math.abs(ground - floor) > 4 ? palm.y : ground, z);
                    player.stopRiding();
                    dismountTo = null;
                    entityData.set(DATA_HELD, 0);
                    setHandPhase(HandPhase.RELEASE);
                }
            }
            case RELEASE -> {
                if (handTicks >= 25) {
                    setHandPhase(HandPhase.RETURN);
                }
            }
            case RETURN -> {
                if (reach <= 0 && bow <= 0) {
                    endHand();
                }
            }
            case NONE -> {}
        }
        drive(0, wantTurn, fullSpeed);
    }

    private void endHand() {
        setHandPhase(HandPhase.NONE);
        entityData.set(DATA_HELD, 0);
        handPlayer = null;
        settingDown = false;
        pause = Math.max(pause, 30);
    }

    /** Where the open hand is laid for this player: beside them if the arm reaches that far, else as near as it reaches. */
    private void offerPalm(Player player, int side) {
        double turnBy = Math.toRadians(180 - getYRot());
        double dx = player.getX() - getX();
        double dz = player.getZ() - getZ();
        double right = dx * Math.cos(turnBy) - dz * Math.sin(turnBy);
        double ahead = -(dx * Math.sin(turnBy) + dz * Math.cos(turnBy));
        // From the player a little toward the middle of where the hand reaches, and no further from that middle
        // than the arm allows.
        double offRight = right - side * OFFER_SIDE;
        double offAhead = ahead - OFFER_AHEAD;
        double off = Math.sqrt(offRight * offRight + offAhead * offAhead);
        double keep = off < 1.0E-3 ? 0 : Mth.clamp(off - 2.6, 0, OFFER_SLACK) / off;
        Vec3 ground = groundFromIt(side * OFFER_SIDE + offRight * keep, OFFER_AHEAD + offAhead * keep);
        entityData.set(DATA_PALM, new Vector3f((float) ground.x, (float) (ground.y + PALM_HEIGHT), (float) ground.z));
    }

    /** Where the hand comes down to let somebody off: beside and ahead of the colossus, on ground that isn't under water. */
    private void setDownPalm(ServerLevel level, int side) {
        double[][] places = {{OFFER_SIDE, OFFER_AHEAD}, {OFFER_SIDE + 2.5, OFFER_AHEAD - 3}, {OFFER_SIDE - 3, OFFER_AHEAD + 2.5}, {OFFER_SIDE + 4, OFFER_AHEAD - 6}};
        Vec3 chosen = null;
        double feet = gait.placed() ? gait.support() : getY();
        for (double[] place : places) {
            Vec3 ground = groundFromIt(side * place[0], place[1]);
            double real = ColossusGround.height(level, ground.x, ground.z);
            if (chosen == null) {
                chosen = ground;
            }
            if (!Double.isNaN(real) && Math.abs(real - feet) <= 3.5 && ColossusGround.waterDepth(level, ground.x, ground.z) <= 1) {
                chosen = ground;
                break;
            }
        }
        entityData.set(DATA_PALM, new Vector3f((float) chosen.x, (float) (chosen.y + PALM_HEIGHT), (float) chosen.z));
    }

    /**
     * Takes a rider off at once and puts them on the ground beside the colossus: when they die or leave the game,
     * so that neither they nor what they leave behind stays up in the air.
     */
    public void putDown(ServerPlayer player) {
        if (player.getVehicle() != this) {
            return;
        }
        int side = player.getUUID().equals(seats[0]) ? -1 : 1;
        dismountTo = groundFromIt(side * (OFFER_SIDE + 1), 3);
        player.stopRiding();
        dismountTo = null;
    }

    // ----- Carrying riders (both sides) -----

    /** Only players ride it, and only by its hand. */
    @Override
    protected boolean canAddPassenger(Entity passenger) {
        return passenger instanceof Player && getPassengers().size() < Mth.clamp(CommonConfig.get().colossus().maxRiders(), 0, 2);
    }

    /** No health bar of its own over the rider's hotbar: their food bar stays. */
    @Override
    public boolean showVehicleHealth() {
        return false;
    }

    /** A player who leaves the game on its shoulder doesn't take the colossus out of the world with them. */
    @Override
    public boolean hasExactlyOnePlayerPassenger() {
        return false;
    }

    @Override
    public Vec3 getPassengerRidingPosition(Entity passenger) {
        int id = passenger.getId();
        int handSeat = handSide() < 0 ? 0 : 1;
        if (entityData.get(DATA_HELD) == id) {
            Vec3 palm = toWorld(tickPose.palm, 1f);
            Vec3 seat = toWorld(tickPose.seat[handSeat], 1f);
            double shift = Mth.clamp(handTicks / (double) SHIFT_TICKS, 0, 1);
            shift = shift * shift * (3 - 2 * shift);
            switch (handPhase()) {
                case LIFT -> {
                    // Gathered into the palm from where they stood.
                    if (gatheredFrom == null) {
                        gatheredFrom = passenger.position().add(passenger.getVehicleAttachmentPoint(this));
                    }
                    double gather = Mth.clamp(handTicks / (double) GATHER_TICKS, 0, 1);
                    return gatheredFrom.lerp(palm, gather * gather * (3 - 2 * gather));
                }
                case SEAT -> {
                    return palm.lerp(seat, shift);
                }
                case TAKE -> {
                    return seat.lerp(palm, shift);
                }
                case NONE, TURN, RETURN -> {
                    return seat;
                }
                default -> {
                    return palm;
                }
            }
        }
        return toWorld(tickPose.seat[entityData.get(DATA_SEAT_RIGHT) == id ? 1 : entityData.get(DATA_SEAT_LEFT) == id ? 0 : handSeat], 1f);
    }

    @Override
    protected void removePassenger(Entity passenger) {
        super.removePassenger(passenger);
        if (level().isClientSide) {
            return;
        }
        for (int seat = 0; seat < 2; seat++) {
            if (passenger.getUUID().equals(seats[seat])) {
                seats[seat] = null;
                wantsDown[seat] = false;
                seatId(seat, 0);
            }
        }
        if (entityData.get(DATA_HELD) == passenger.getId()) {
            entityData.set(DATA_HELD, 0);
        }
        if (dismountTo == null && passenger instanceof ServerPlayer player && player.isAlive() && !player.hasDisconnected()) {
            // Off its shoulder some other way (the colossus is gone, a command): they drift down unharmed.
            player.addEffect(new MobEffectInstance(MobEffects.SLOW_FALLING, 100, 0, false, false, true));
            ColossusRiding.protectFromFall(player);
        }
    }

    /** By its hand they are put on the ground; any other way they are left where they sat, to drift down. */
    @Override
    public Vec3 getDismountLocationForPassenger(LivingEntity passenger) {
        return dismountTo != null ? dismountTo : passenger.position();
    }

    // ----- The hitboxes -----

    private void moveParts() {
        double turnBy = Math.toRadians(180 - getYRot());
        double cos = Math.cos(turnBy);
        double sin = Math.sin(turnBy);
        for (int i = 0; i < parts.length; i++) {
            double[] point = tickPose.part[i];
            parts[i].centreOn(
                    getX() + (point[0] * cos + point[2] * sin) * ColossusPose.PX,
                    getY() + point[1] * ColossusPose.PX,
                    getZ() + (-point[0] * sin + point[2] * cos) * ColossusPose.PX);
        }
    }

    /**
     * A part that moves into a player (a foot coming down, a swinging hand) would leave them standing inside it:
     * the player is eased out sideways. Their own client does it, as it does all their movement. Standing on top of
     * a part is left alone.
     */
    private void keepPlayerOut() {
        for (Player player : level().players()) {
            if (!player.isLocalPlayer() || player.isSpectator() || player.isPassenger() || player.distanceToSqr(this) > 48 * 48) {
                continue;
            }
            AABB box = player.getBoundingBox();
            for (ColossusPart part : parts) {
                AABB solid = part.getBoundingBox();
                if (!box.intersects(solid) || box.minY > solid.maxY - 0.6) {
                    continue;
                }
                double dx = player.getX() - part.getX();
                double dz = player.getZ() - part.getZ();
                double length = Math.sqrt(dx * dx + dz * dz);
                if (length < 1.0E-3) {
                    dx = 1;
                    dz = 0;
                    length = 1;
                }
                player.push(dx / length * 0.12, 0, dz / length * 0.12);
            }
        }
    }

    // ----- Animations -----

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "body", 8, state -> state.setAndContinue(switch (activity()) {
            case SITTING_DOWN -> SIT_DOWN;
            case ASLEEP -> SLEEP;
            case STANDING_UP -> STAND_UP;
            case AWAKE -> walking() ? WALK : IDLE;
        })));
        // The head's own gestures, started by the server; they play over whatever the body does.
        controllers.add(new AnimationController<>(this, "head", 6, state -> PlayState.STOP)
                .triggerableAnim("look_around", LOOK_AROUND)
                .triggerableAnim("look_sky", LOOK_SKY)
                .triggerableAnim("shake_head", SHAKE_HEAD)
                .triggerableAnim("nod", NOD));
        // The fingers of the hand that lifts somebody: open to offer, closed around them, and back to rest.
        controllers.add(new AnimationController<>(this, "hands", 6, state -> {
            int hand = handSide() < 0 ? 0 : 1;
            return switch (handPhase()) {
                case OFFER, WAIT, SEAT, FETCH, RELEASE -> state.setAndContinue(OPEN_HAND[hand]);
                case LIFT, TAKE, LOWER -> state.setAndContinue(CRADLE[hand]);
                case RETURN -> state.setAndContinue(REST_HAND[hand]);
                default -> PlayState.STOP;
            };
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
