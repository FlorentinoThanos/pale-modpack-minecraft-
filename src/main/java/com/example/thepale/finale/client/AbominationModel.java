package com.example.thepale.finale.client;

import com.example.thepale.ThePale;
import com.example.thepale.finale.boss.Limbs;
import com.example.thepale.finale.boss.PatchworkAbomination;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;

/**
 * The boss's model (assets/thepale/geo/entity/patchwork_abomination.geo.json and its animations and texture).
 *
 * <p>The body, the seam, the eye, the face's jaw and the hand's fingers are animated by the animation file. The limbs
 * are not: every frame each limb segment's bone is put where {@link Limbs} hangs it, the same place the server has its
 * hitboxes, and the bones of severed limbs are hidden. For that the segment bones are flat (no parents), named
 * {@code tentacle1_seg1} to {@code tentacle6_seg8}, {@code face_seg1..8} and {@code hand_seg1..8}, each modelled
 * hanging straight down from its pivot; {@code face} and {@code hand} are put at the end of their limbs. Bones the
 * model doesn't have are skipped, so a replacement model may leave some out.
 */
public final class AbominationModel extends DefaultedEntityGeoModel<PatchworkAbomination> {
    private static final String[] NAMES = {"tentacle1", "tentacle2", "tentacle3", "tentacle4", "tentacle5", "tentacle6", "face", "hand"};

    private final Vec3[] joints = new Vec3[Limbs.SEGMENTS + 1];

    public AbominationModel() {
        super(ThePale.id("patchwork_abomination"));
    }

    @Override
    public void setCustomAnimations(PatchworkAbomination boss, long instanceId, AnimationState<PatchworkAbomination> state) {
        float partialTick = state.getPartialTick();
        Vec3 base = boss.base(partialTick);
        // The renderer turns the model by 180 - yaw; model space is the world turned back by that.
        double turn = Math.toRadians(180 - boss.yaw(partialTick));
        double cos = Math.cos(turn);
        double sin = Math.sin(turn);
        for (int limb = 0; limb < Limbs.COUNT; limb++) {
            boolean shown = boss.limbAlive(limb) && !boss.isDeadOrDying();
            if (shown) {
                boss.joints(limb, partialTick, joints);
            }
            for (int segment = 0; segment < Limbs.SEGMENTS; segment++) {
                GeoBone bone = getBone(NAMES[limb] + "_seg" + (segment + 1)).orElse(null);
                if (bone == null) {
                    continue;
                }
                bone.setHidden(!shown);
                if (shown) {
                    place(bone, joints[segment], joints[segment + 1].subtract(joints[segment]), base, cos, sin, false);
                }
            }
            if (!Limbs.isTentacle(limb)) {
                GeoBone end = getBone(NAMES[limb]).orElse(null);
                if (end != null) {
                    end.setHidden(!shown);
                    if (shown) {
                        Vec3 tip = joints[Limbs.SEGMENTS];
                        // The face looks away from the body, level; the hand hangs on along its limb.
                        Vec3 along = limb == Limbs.FACE
                                ? new Vec3(tip.x - base.x, 0, tip.z - base.z)
                                : tip.subtract(joints[Limbs.SEGMENTS - 1]);
                        place(end, tip, along, base, cos, sin, limb == Limbs.FACE);
                    }
                }
            }
        }
    }

    /**
     * Moves a bone so that its pivot is at {@code at} (a world position) and turns it along {@code along}: a segment's
     * own "down" (it is modelled hanging from its pivot), or for the face its front (-Z in the model).
     */
    private static void place(GeoBone bone, Vec3 at, Vec3 along, Vec3 base, double cos, double sin, boolean front) {
        double relX = at.x - base.x;
        double relZ = at.z - base.z;
        double x = relX * cos - relZ * sin;
        double y = at.y - base.y;
        double z = relX * sin + relZ * cos;
        // GeckoLib mirrors X: a bone's offset moves it by (-posX, posY, posZ), and its baked pivot is already mirrored.
        bone.setPosX((float) -(x * 16 - bone.getPivotX()));
        bone.setPosY((float) (y * 16 - bone.getPivotY()));
        bone.setPosZ((float) (z * 16 - bone.getPivotZ()));

        double length = along.length();
        if (length < 1.0E-5) {
            return;
        }
        double dirX = (along.x * cos - along.z * sin) / length;
        double dirY = along.y / length;
        double dirZ = (along.x * sin + along.z * cos) / length;
        if (front) {
            // Turning (0, 0, -1) about Y onto the direction.
            bone.setRotX(0);
            bone.setRotY((float) Mth.atan2(-dirX, -dirZ));
        } else {
            // GeckoLib turns a bone about X first, then Y: that takes (0, -1, 0) to (-sin x sin y, -cos x, -sin x cos y).
            bone.setRotX((float) Math.acos(Mth.clamp(-dirY, -1, 1)));
            bone.setRotY((float) Mth.atan2(-dirX, -dirZ));
        }
        bone.setRotZ(0);
    }
}
