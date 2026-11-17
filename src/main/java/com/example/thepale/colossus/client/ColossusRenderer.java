package com.example.thepale.colossus.client;

import com.example.thepale.colossus.ColossusPose;
import com.example.thepale.colossus.FrostColossus;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/** Draws the Frost Colossus: the GeckoLib model at twice its size, with the eyes glowing (the texture's _glowmask). */
public final class ColossusRenderer extends GeoEntityRenderer<FrostColossus> {
    public ColossusRenderer(EntityRendererProvider.Context context) {
        super(context, new ColossusModel());
        withScale(ColossusPose.SCALE);
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
        this.shadowRadius = 0;
    }

    /** Turned by its own heading only (a mob's body yaw trails its movement), and it doesn't keel over when it dies. */
    @Override
    protected void applyRotations(FrostColossus colossus, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick, float nativeScale) {
        poseStack.mulPose(Axis.YP.rotationDegrees(180f - colossus.yaw(partialTick)));
    }

    @Override
    protected float getDeathMaxRotation(FrostColossus colossus) {
        return 0;
    }
}
