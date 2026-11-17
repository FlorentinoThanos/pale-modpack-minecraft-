package com.example.thepale.colossus;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.entity.PartEntity;
import org.jetbrains.annotations.Nullable;

/**
 * One hitbox of the Frost Colossus: a leg, the hips, the chest, a shoulder, the head, a stretch of an arm or a hand
 * (see {@link ColossusPose#PART_NAMES}). It is solid, so nobody walks through the colossus, and a click or a hit on
 * it is a click or a hit on the colossus.
 */
public final class ColossusPart extends PartEntity<FrostColossus> {
    /** Which hitbox this is: an index into {@link ColossusPose#PART_NAMES}. */
    public final int index;
    private final EntityDimensions size;

    ColossusPart(FrostColossus parent, int index) {
        super(parent);
        this.index = index;
        this.size = EntityDimensions.scalable(ColossusPose.PART_SIZE[index][0], ColossusPose.PART_SIZE[index][1]);
        this.refreshDimensions();
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
        return getParent().isAlive();
    }

    @Override
    public boolean canBeCollidedWith() {
        return getParent().isAlive();
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Nullable
    @Override
    public ItemStack getPickResult() {
        return getParent().getPickResult();
    }

    @Override
    public boolean hurt(DamageSource source, float amount) {
        return getParent().hurt(source, amount);
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        return getParent().interactPart(this, player, hand);
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
