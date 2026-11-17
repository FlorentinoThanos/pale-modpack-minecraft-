package com.example.thepale.colossus;

import com.example.thepale.config.CommonConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
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

    /** What it is doing with its whole body. */
    public enum Activity { AWAKE, SITTING_DOWN, ASLEEP, STANDING_UP }

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

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop("walk");
    private static final RawAnimation SIT_DOWN = RawAnimation.begin().thenPlayAndHold("sit_down");
    private static final RawAnimation SLEEP = RawAnimation.begin().thenLoop("sleep");
    private static final RawAnimation STAND_UP = RawAnimation.begin().thenPlayAndHold("stand_up");
    private static final RawAnimation LOOK_AROUND = RawAnimation.begin().thenPlay("look_around");
    private static final RawAnimation LOOK_SKY = RawAnimation.begin().thenPlay("look_sky");
    private static final RawAnimation SHAKE_HEAD = RawAnimation.begin().thenPlay("shake_head");
    private static final RawAnimation NOD = RawAnimation.begin().thenPlay("nod");

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

    // The client's own, eased copy of the heading.
    private boolean yawSeen;

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
                .add(Attributes.MAX_HEALTH, 1000.0)
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
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTIVITY.equals(key) && level().isClientSide) {
            // Just come into view asleep: it isn't shown sitting down first.
            activityTicks = tickCount < 2 ? SIT_TICKS : 0;
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
        pose.bow = 0;
        pose.time = tickCount + partialTick;
        pose.reachSide = 0;
        pose.reach = 0;
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
        double moved = Math.sqrt(movedX * movedX + movedZ * movedZ);
        pace += (Mth.clamp(moved / Math.max(fullSpeed, 1.0E-4), 0, 1) - pace) * 0.2;
        if (!level().isClientSide && isAlive()) {
            standOnFeet();
        }
        setYBodyRot(getYRot());
        setYHeadRot(getYRot());
        fillPose(tickPose, 1f);
        moveParts();
        if (level().isClientSide) {
            keepPlayerOut();
        }
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
        if (humCooldown > 0) {
            humCooldown--;
        }
        if (headBusy > 0) {
            headBusy--;
        }
        switch (activity()) {
            case AWAKE -> {
                if (wantsRest && speed == 0 && !hasWaypoint && gait.settled(getX(), getZ(), getYRot())) {
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
        if (tickCount % 4 == 0) {
            watch(level);
        }
        if (entityData.get(DATA_YAW) != getYRot()) {
            entityData.set(DATA_YAW, getYRot());
        }
    }

    // ----- Walking (server) -----

    private void wander(ServerLevel level, double fullSpeed) {
        float yaw = getYRot();
        double want = 0;
        float wantTurn = 0;
        if (wantsRest) {
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
        } else if (!wantsRest) {
            chooseWaypoint(level);
        }
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
        out.append(activity()).append(hasWaypoint ? String.format(" walking to %.0f %.0f", waypointX, waypointZ) : " no waypoint")
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
                if (distance < nearest && player.isAlive() && !player.isSpectator() && !player.isInvisible()) {
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
        if (attacker instanceof Player player && player.isCreative() && player.isShiftKeyDown()) {
            // The one way to take it out of the world by hand: a creative player, sneaking.
            ejectPassengers();
            discard();
            return true;
        }
        if (attacker instanceof LivingEntity living && attacker != this) {
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

    /** A click on any of its parts. */
    InteractionResult interactPart(ColossusPart part, Player player, InteractionHand hand) {
        return InteractionResult.PASS;
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
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return geoCache;
    }
}
