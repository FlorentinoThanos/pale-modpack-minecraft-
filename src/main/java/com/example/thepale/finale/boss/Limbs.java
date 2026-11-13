package com.example.thepale.finale.boss;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The shape of the Patchwork Abomination, shared by the server (hitboxes) and the client (the model's bones): where
 * each limb is attached to the body, where its tip rests, and how the chain of segments hangs between the two. The
 * numbers here are what the model has to match (see the README, "Replacing the boss model").
 *
 * <p>Limbs 0 to 5 are the tentacles, {@link #FACE} carries the stone face and {@link #HAND} the clawed hand. Angles are
 * measured from the front of the body (where the main eye is), turning toward its left.
 */
public final class Limbs {
    public static final int TENTACLES = 6;
    public static final int FACE = 6;
    public static final int HAND = 7;
    public static final int COUNT = 8;

    /** Segments in a limb, and the length of each in blocks. */
    public static final int SEGMENTS = 8;
    public static final double SEGMENT_LENGTH = 2.0;
    public static final double LENGTH = SEGMENTS * SEGMENT_LENGTH;

    /** The body is a ball of this radius; the entity's position is its lowest point. */
    public static final double BODY_RADIUS = 4.5;

    /** Where each limb leaves the body: degrees around it, and degrees above its equator. */
    private static final double[] AZIMUTH = {30, 90, 150, 210, 270, 330, 305, 55};
    private static final double[] ELEVATION = {-38, -38, -38, -38, -38, -38, 22, 22};
    /** How far from the body's axis a tentacle's tip rests on the ground. */
    private static final double REST_RADIUS = 9.5;
    /** The face and the hand, held up out of reach in phase 1: this far from the axis and above the top of the body. */
    private static final double RAISED_RADIUS = 7.5;
    private static final double RAISED_ABOVE_TOP = 2.5;

    private Limbs() {}

    public static boolean isTentacle(int limb) {
        return limb < TENTACLES;
    }

    /** {@return the unit vector for this many degrees around the body, for a body turned to {@code yaw} (Minecraft degrees)} */
    public static Vec3 around(double degrees, float yaw) {
        // Yaw 0 looks toward +Z, yaw 90 toward -X.
        double angle = Math.toRadians(yaw + degrees);
        return new Vec3(-Math.sin(angle), 0, Math.cos(angle));
    }

    public static Vec3 centre(Vec3 base) {
        return base.add(0, BODY_RADIUS, 0);
    }

    /** {@return where limb {@code limb} is attached, for a body whose lowest point is {@code base}} */
    public static Vec3 anchor(int limb, Vec3 base, float yaw) {
        double elevation = Math.toRadians(ELEVATION[limb]);
        Vec3 out = around(AZIMUTH[limb], yaw);
        double reach = BODY_RADIUS * 0.9;
        return centre(base).add(out.x * Math.cos(elevation) * reach, Math.sin(elevation) * reach, out.z * Math.cos(elevation) * reach);
    }

    /** {@return the column a tentacle's tip rests in (y is not set)} */
    public static Vec3 restColumn(int limb, Vec3 base, float yaw) {
        Vec3 out = around(AZIMUTH[limb], yaw);
        return new Vec3(base.x + out.x * REST_RADIUS, base.y, base.z + out.z * REST_RADIUS);
    }

    /** {@return where the face or the hand is held while it is out of the fight} */
    public static Vec3 raised(int limb, Vec3 base, float yaw) {
        Vec3 out = around(AZIMUTH[limb], yaw);
        return new Vec3(base.x + out.x * RAISED_RADIUS, base.y + 2 * BODY_RADIUS + RAISED_ABOVE_TOP, base.z + out.z * RAISED_RADIUS);
    }

    /** {@return the middle of the main eye, on the front of the body a little below its equator} */
    public static Vec3 eye(Vec3 base, float yaw) {
        Vec3 front = around(0, yaw);
        return centre(base).add(front.x * (BODY_RADIUS - 0.4), -1.3, front.z * (BODY_RADIUS - 0.4));
    }

    /**
     * Hangs a limb between its anchor and its tip: {@link #SEGMENTS} + 1 joints, the first at the anchor. The limb
     * has a fixed length, so with the tip nearer than that it bows out, away from the body and upward; with the tip
     * out of reach it points straight at it and ends short.
     *
     * @param axis   any point on the body's vertical axis, to bow away from
     * @param joints receives the joints; must hold {@link #SEGMENTS} + 1
     */
    public static void hang(Vec3 anchor, Vec3 tip, Vec3 axis, Vec3[] joints) {
        Vec3 span = tip.subtract(anchor);
        double distance = span.length();
        if (distance >= LENGTH - 0.05) {
            Vec3 step = span.scale(SEGMENT_LENGTH / Math.max(distance, 1.0E-4));
            for (int i = 0; i <= SEGMENTS; i++) {
                joints[i] = anchor.add(step.scale(i));
            }
            return;
        }
        Vec3 middle = anchor.add(span.scale(0.5));
        double outX = middle.x - axis.x;
        double outZ = middle.z - axis.z;
        double outLength = Math.sqrt(outX * outX + outZ * outZ);
        if (outLength < 0.01) {
            outX = span.x;
            outZ = span.z;
            outLength = Math.max(Math.sqrt(outX * outX + outZ * outZ), 0.01);
        }
        Vec3 bow = new Vec3(outX / outLength, 0.75, outZ / outLength).normalize();
        // The curve is a quadratic Bezier; find how far to push its middle out for the curve to be as long as the limb.
        double low = 0;
        double high = LENGTH * 1.5;
        for (int i = 0; i < 14; i++) {
            double tryAt = (low + high) / 2;
            if (length(anchor, middle.add(bow.scale(tryAt)), tip) < LENGTH) {
                low = tryAt;
            } else {
                high = tryAt;
            }
        }
        Vec3 control = middle.add(bow.scale((low + high) / 2));
        // Joints at equal distances along the curve.
        final int samples = 48;
        joints[0] = anchor;
        Vec3 previous = anchor;
        double walked = 0;
        int next = 1;
        for (int i = 1; i <= samples && next <= SEGMENTS; i++) {
            Vec3 point = bezier(anchor, control, tip, i / (double) samples);
            double stretch = point.distanceTo(previous);
            while (next <= SEGMENTS && walked + stretch >= next * SEGMENT_LENGTH) {
                double part = stretch <= 1.0E-6 ? 0 : (next * SEGMENT_LENGTH - walked) / stretch;
                joints[next++] = previous.lerp(point, Mth.clamp(part, 0, 1));
            }
            walked += stretch;
            previous = point;
        }
        while (next <= SEGMENTS) {
            joints[next++] = tip;
        }
    }

    private static Vec3 bezier(Vec3 from, Vec3 control, Vec3 to, double t) {
        double u = 1 - t;
        return new Vec3(
                u * u * from.x + 2 * u * t * control.x + t * t * to.x,
                u * u * from.y + 2 * u * t * control.y + t * t * to.y,
                u * u * from.z + 2 * u * t * control.z + t * t * to.z);
    }

    private static double length(Vec3 from, Vec3 control, Vec3 to) {
        double total = 0;
        Vec3 previous = from;
        for (int i = 1; i <= 16; i++) {
            Vec3 point = bezier(from, control, to, i / 16.0);
            total += point.distanceTo(previous);
            previous = point;
        }
        return total;
    }
}
