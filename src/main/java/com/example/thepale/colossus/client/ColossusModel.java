package com.example.thepale.colossus.client;

import com.example.thepale.ThePale;
import com.example.thepale.colossus.ColossusPose;
import com.example.thepale.colossus.FrostColossus;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.loading.math.MathParser;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The Frost Colossus's model (assets/thepale/geo/entity/frost_colossus.geo.json with its animations and texture, all
 * written by tools/gen_colossus_model.py).
 *
 * <p>The animation file moves the arms, the hands, the head's gestures and the whole body when it sits down, sleeps
 * and gets up. The rest is put here every frame from {@link ColossusPose}, the same pose the server has its hitboxes
 * at: the body sinks and leans with the steps, each leg reaches from its hip to where its foot stands on the ground,
 * the head turns toward whoever it is watching, and the arm that lifts a rider reaches for where its hand has to be.
 */
public final class ColossusModel extends DefaultedEntityGeoModel<FrostColossus> {
    /** The caches it carries on a walk, in the order of {@link FrostColossus#hasCache}. */
    private static final String[] CACHES = {"cache_left", "cache_right", "cache_back"};
    /** The snow caps, and how many times thicker they get over a night's sleep. */
    private static final String[] SNOW_BONES = {
        "snow_shoulders", "snow_head", "snow_left_arm", "snow_right_arm", "snow_left_forearm", "snow_right_forearm"};
    private static final float SNOW_DEEPEN = 1.0f;

    private double armLeft;
    private double armRight;
    private double stride;

    public ColossusModel() {
        super(ThePale.id("frost_colossus"));
        // What the walk animation reads (see the generator script).
        MathParser.setVariable("query.colossus_arm_left", () -> armLeft);
        MathParser.setVariable("query.colossus_arm_right", () -> armRight);
        MathParser.setVariable("query.colossus_stride", () -> stride);
    }

    /** The ice is a little see-through. */
    @Override
    public RenderType getRenderType(FrostColossus colossus, ResourceLocation texture) {
        return RenderType.entityTranslucent(texture);
    }

    @Override
    public void applyMolangQueries(AnimationState<FrostColossus> state, double animTime) {
        ColossusPose pose = state.getAnimatable().pose(state.getPartialTick());
        armLeft = pose.armSwing[0];
        armRight = pose.armSwing[1];
        stride = pose.speed;
    }

    @Override
    public void setCustomAnimations(FrostColossus colossus, long instanceId, AnimationState<FrostColossus> state) {
        float partialTick = state.getPartialTick();
        ColossusPose pose = colossus.pose(partialTick);
        GeoBone body = getBone("body").orElse(null);
        if (body != null) {
            body.setPosY((float) pose.bodyY);
            body.setRotX((float) pose.bodyPitch);
            body.setRotZ((float) pose.bodyRoll);
        }
        GeoBone head = getBone("head").orElse(null);
        if (head != null) {
            head.setRotY(colossus.lookYaw(partialTick));
            head.setRotX(colossus.lookPitch(partialTick));
        }
        // Sitting down, asleep and getting up, the animation folds the legs; awake, each reaches for its foot.
        boolean awake = colossus.activity() == FrostColossus.Activity.AWAKE;
        leg("left_leg", pose, 0, awake);
        leg("right_leg", pose, 1, awake);
        // Asleep, its eyes go dark and the snow piles up on it; getting up, it shakes the snow off.
        for (int cache = 0; cache < CACHES.length; cache++) {
            GeoBone bone = getBone(CACHES[cache]).orElse(null);
            if (bone != null) {
                bone.setHidden(!colossus.hasCache(cache));
            }
        }
        GeoBone eyes = getBone("eyes").orElse(null);
        if (eyes != null) {
            eyes.setHidden(colossus.eyesClosed(partialTick));
        }
        float snow = (float) colossus.snowCover(partialTick);
        for (String name : SNOW_BONES) {
            GeoBone bone = getBone(name).orElse(null);
            if (bone != null) {
                bone.setScaleY(1 + SNOW_DEEPEN * snow);
            }
        }
        // The arm that lifts somebody reaches for where its hand has to be.
        if (pose.reachSide != 0) {
            String side = pose.reachSide < 0 ? "left" : "right";
            turn(side + "_arm", pose.armRot);
            turn(side + "_forearm", pose.forearmRot);
            turn(side + "_hand", pose.handRot);
        }
    }

    private void turn(String name, double[] angles) {
        GeoBone bone = getBone(name).orElse(null);
        if (bone != null) {
            bone.setRotX((float) angles[0]);
            bone.setRotY((float) angles[1]);
            bone.setRotZ((float) angles[2]);
        }
    }

    private void leg(String name, ColossusPose pose, int leg, boolean awake) {
        GeoBone bone = getBone(name).orElse(null);
        if (bone == null) {
            return;
        }
        bone.setPosY((float) pose.legY[leg]);
        if (awake) {
            bone.setRotX((float) pose.legRot[leg][0]);
            bone.setRotY((float) pose.legRot[leg][1]);
            bone.setRotZ((float) pose.legRot[leg][2]);
        }
    }
}
