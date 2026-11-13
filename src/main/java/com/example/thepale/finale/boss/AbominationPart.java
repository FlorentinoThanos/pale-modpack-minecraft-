package com.example.thepale.finale.boss;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

/** One hitbox of the Patchwork Abomination: the body, the main eye, or a stretch of a limb. Hits go to the boss. */
public final class AbominationPart extends PartEntity<PatchworkAbomination> {
    /** {@link #limb} of the body and of the main eye. */
    public static final int BODY = -1;
    public static final int EYE = -2;

    /** Which limb this is part of ({@link Limbs}), or {@link #BODY} or {@link #EYE}. */
    public final int limb;
    private final EntityDimensions size;
    /** A severed limb's hitboxes are switched off. */
    private boolean active = true;

    AbominationPart(PatchworkAbomination parent, int limb, float width, float height) {
        super(parent);
        this.limb = limb;
        this.size = EntityDimensions.scalable(width, height);
        this.refreshDimensions();
    }

    void setActive(boolean active) {
        this.active = active;
    }

    /** Moves the hitbox so that its middle is at this point, remembering where it was for interpolation. */
    void centreOn(double x, double y, double z) {
        double bottom = y - size.height() / 2;
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.xOld = this.xo;
        this.yOld = this.yo;
        this.zOld = this.zo;
        this.setPos(x, bottom, z);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    @Override
    public boolean isPickable() {
        return active && getParent().isAlive();
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return active && !isInvulnerableTo(source) && getParent().hurtPart(this, source, amount);
    }

    @Override
    public boolean is(Entity entity) {
        return this == entity || getParent() == entity;
    }

    @Override
    public EntityDimensions getDimensions(Pose pose) {
        return size;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
