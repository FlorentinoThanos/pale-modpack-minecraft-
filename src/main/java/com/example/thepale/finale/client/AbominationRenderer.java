package com.example.thepale.finale.client;

import com.example.thepale.finale.boss.PatchworkAbomination;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Draws the boss: the GeckoLib model, with the eyes glowing (the texture's _glowmask). */
public final class AbominationRenderer extends GeoEntityRenderer<PatchworkAbomination> {
    public AbominationRenderer(EntityRendererProvider.Context context) {
        super(context, new AbominationModel());
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0;
    }

    /** Turned by its own yaw only (a mob's body yaw follows its movement), and it doesn't keel over when it dies. */
    @Override
    protected void applyRotations(PatchworkAbomination boss, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - boss.yaw(partialTick)));
    }

    @Override
    protected float getDeathMaxRotation(PatchworkAbomination boss) {
        return 0;
    }
}
