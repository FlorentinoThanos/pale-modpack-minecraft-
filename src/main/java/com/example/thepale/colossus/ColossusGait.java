package com.example.thepale.colossus;

import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;

/**
 * The Frost Colossus's feet. Each foot stands where it was put down and stays there while the body moves on above
 * it; when the body has left it far enough behind (or has turned too far over it), the foot is lifted and carried to
 * where the body will be, one foot at a time. So the feet never slide, whether it walks, turns on the spot, slows
 * down or stands on a slope, and the steps get shorter by themselves as it slows.
 *
 * <p>The server walks the feet for the hitboxes and for what a footfall does to the world, and every client walks
 * them again for what it draws, from where it sees the body. The two may differ by a step; nothing depends on them
 * agreeing.
 */
public final class ColossusGait {
    /** The hips' half width, in blocks: where a foot stands when it stands at rest. */
    private static final double HIP = ColossusPose.HIP_X * ColossusPose.PX;
    private static final double REACH = ColossusPose.REACH_BLOCKS;
    /** A foot lifted while standing or turning comes down after this many ticks. */
    private static final int SETTLE_TICKS = 22;
    /** Standing still for this many ticks, it brings its feet together. */
    private static final int STILL_TICKS = 12;
    /** How high a foot is lifted at a full walk, in blocks, and in a small step. */
    private static final double LIFT = 1.6;
    private static final double SMALL_LIFT = 0.55;

    /** Where each foot is now (0 = left, 1 = right): its place in the world, which way it points, how high it is. */
    public final double[] x = new double[2];
    public final double[] y = new double[2];
    public final double[] z = new double[2];
    public final float[] yaw = new float[2];
    /** How far off the ground each foot is in its step, 0 (standing) to 1 (the top of the step). */
    public final double[] raised = new double[2];
    /** The same a tick ago, for drawing between ticks. */
    public final double[] xOld = new double[2];
    public final double[] yOld = new double[2];
    public final double[] zOld = new double[2];
    public final float[] yawOld = new float[2];
    public final double[] raisedOld = new double[2];

    /** The foot that came down this tick, or -1; and how hard (0 to 1: a full stride is 1). */
    public int landed = -1;
    public double landedWeight;

    private final boolean[] swinging = new boolean[2];
    private final double[] progress = new double[2];
    private final int[] duration = new int[2];
    private final double[] lift = new double[2];
    private final double[] fromX = new double[2];
    private final double[] fromY = new double[2];
    private final double[] fromZ = new double[2];
    private final float[] fromYaw = new float[2];
    private final double[] toX = new double[2];
    private final double[] toY = new double[2];
    private final double[] toZ = new double[2];
    private final double[] weight = new double[2];
    private boolean placed;
    private int still;
    private int lastStepped = 1;
    private float bodyYawOld;

    /** Whether the feet have been put down yet (they are, at the first tick). */
    public boolean placed() {
        return placed;
    }

    /** {@return whether a foot is in the air} */
    public boolean stepping() {
        return swinging[0] || swinging[1];
    }

    /** {@return whether both feet stand where they belong: under the hips, pointing ahead} */
    public boolean settled(double bodyX, double bodyZ, float bodyYaw) {
        if (!placed || stepping()) {
            return false;
        }
        for (int foot = 0; foot < 2; foot++) {
            double restX = restX(foot, bodyX, bodyYaw);
            double restZ = restZ(foot, bodyZ, bodyYaw);
            if (Mth.square(x[foot] - restX) + Mth.square(z[foot] - restZ) > 0.2 || Math.abs(Mth.wrapDegrees(bodyYaw - yaw[foot])) > 4) {
                return false;
            }
        }
        return true;
    }

    /** Puts both feet down under the hips, where the body is now. */
    public void place(Level level, double bodyX, double bodyY, double bodyZ, float bodyYaw) {
        for (int foot = 0; foot < 2; foot++) {
            x[foot] = restX(foot, bodyX, bodyYaw);
            z[foot] = restZ(foot, bodyZ, bodyYaw);
            double ground = ColossusGround.underFoot(level, x[foot], z[foot], bodyYaw);
            // Put down in the air (by a command), its feet are on the ground all the same and its body comes down to them.
            y[foot] = Double.isNaN(ground) ? bodyY : ground;
            yaw[foot] = bodyYaw;
            raised[foot] = 0;
            swinging[foot] = false;
            xOld[foot] = x[foot];
            yOld[foot] = y[foot];
            zOld[foot] = z[foot];
            yawOld[foot] = yaw[foot];
            raisedOld[foot] = 0;
        }
        bodyYawOld = bodyYaw;
        placed = true;
        still = STILL_TICKS;
        landed = -1;
    }

    /**
     * A tick of walking.
     *
     * @param moveX     how far the body moved this tick, in blocks
     * @param fullSpeed its walking speed, in blocks a tick
     * @param maySteps  whether it may lift a foot (not while it sits)
     */
    public void tick(Level level, double bodyX, double bodyY, double bodyZ, float bodyYaw, double moveX, double moveZ, double fullSpeed, boolean maySteps) {
        landed = -1;
        if (!placed || Mth.square(x[0] - bodyX) + Mth.square(z[0] - bodyZ) > 20 * 20) {
            // New, or moved by a command.
            place(level, bodyX, bodyY, bodyZ, bodyYaw);
            return;
        }
        for (int foot = 0; foot < 2; foot++) {
            xOld[foot] = x[foot];
            yOld[foot] = y[foot];
            zOld[foot] = z[foot];
            yawOld[foot] = yaw[foot];
            raisedOld[foot] = raised[foot];
        }
        float turned = Mth.wrapDegrees(bodyYaw - bodyYawOld);
        bodyYawOld = bodyYaw;
        double speed = Math.sqrt(moveX * moveX + moveZ * moveZ);
        boolean walking = speed > 0.004;
        double pace = Mth.clamp(speed / Math.max(fullSpeed, 1.0E-4), 0, 1);
        still = walking || Math.abs(turned) > 0.05f ? 0 : still + 1;
        double aheadX = walking ? moveX / speed : 0;
        double aheadZ = walking ? moveZ / speed : 0;

        for (int foot = 0; foot < 2; foot++) {
            if (!swinging[foot]) {
                continue;
            }
            progress[foot] = Math.min(1, progress[foot] + 1.0 / duration[foot]);
            double left = (1 - progress[foot]) * duration[foot];
            // Where the hip will be when the foot comes down, and a stride's reach beyond it.
            float landingYaw = bodyYaw + turned * (float) Math.min(left, 10);
            double aimX = restX(foot, bodyX + moveX * left, landingYaw) + aheadX * REACH * pace;
            double aimZ = restZ(foot, bodyZ + moveZ * left, landingYaw) + aheadZ * REACH * pace;
            if (Mth.square(aimX - toX[foot]) + Mth.square(aimZ - toZ[foot]) > 0.5 * 0.5 || progress[foot] >= 1) {
                toX[foot] = aimX;
                toZ[foot] = aimZ;
                double ground = ColossusGround.underFoot(level, aimX, aimZ, landingYaw);
                toY[foot] = Double.isNaN(ground) ? fromY[foot] : Mth.clamp(ground, bodyY - 6, bodyY + 6);
            }
            double eased = ease(progress[foot]);
            raised[foot] = Math.sin(Math.PI * progress[foot]);
            x[foot] = Mth.lerp(eased, fromX[foot], aimX);
            z[foot] = Mth.lerp(eased, fromZ[foot], aimZ);
            y[foot] = Mth.lerp(eased, fromY[foot], toY[foot]) + lift[foot] * raised[foot];
            yaw[foot] = fromYaw[foot] + Mth.wrapDegrees(landingYaw - fromYaw[foot]) * (float) eased;
            if (progress[foot] >= 1) {
                swinging[foot] = false;
                raised[foot] = 0;
                y[foot] = toY[foot];
                landed = foot;
                landedWeight = weight[foot];
                lastStepped = foot;
            }
        }

        if (!maySteps || swinging[0] || swinging[1]) {
            return;
        }
        // How badly each foot needs to step: 1 and over, it does. The worse one goes; the other waits its turn.
        double[] behind = new double[2];
        double[] aside = new double[2];
        double[] twist = new double[2];
        for (int foot = 0; foot < 2; foot++) {
            double offX = restX(foot, bodyX, bodyYaw) - x[foot];
            double offZ = restZ(foot, bodyZ, bodyYaw) - z[foot];
            if (walking) {
                behind[foot] = offX * aheadX + offZ * aheadZ;
                aside[foot] = Math.abs(offX * aheadZ - offZ * aheadX);
            } else {
                behind[foot] = Math.sqrt(offX * offX + offZ * offZ);
            }
            twist[foot] = Math.abs(Mth.wrapDegrees(bodyYaw - yaw[foot]));
        }
        double[] need = new double[2];
        for (int foot = 0; foot < 2; foot++) {
            if (walking) {
                // A foot is left behind by a stride's reach before it is lifted; sooner while the other foot isn't
                // out in front yet (setting off), so it doesn't lean out of its first step.
                double reach = Math.min(Mth.lerp(pace, 0.6, REACH), 0.5 + 1.25 * Math.max(0, -behind[1 - foot]));
                need[foot] = Math.max(Math.max(behind[foot] / reach, aside[foot] / 1.5), twist[foot] / 14);
            } else if (still < STILL_TICKS) {
                need[foot] = Math.max(behind[foot] / 0.9, twist[foot] / 11);
            } else {
                need[foot] = Math.max(behind[foot] / 0.3, twist[foot] / 3);
            }
        }
        if (need[0] < 1 && need[1] < 1) {
            return;
        }
        int next = need[0] > need[1] ? 0 : 1;
        if (Math.abs(need[0] - need[1]) < 0.1 * Math.max(need[0], need[1])) {
            next = 1 - lastStepped;
        }
        swinging[next] = true;
        progress[next] = 0;
        // At a full walk a foot is in the air for the rest of the cycle its stance leaves.
        double stride = ColossusPose.CYCLE_BLOCKS * (1 - ColossusPose.STANCE) / Math.max(fullSpeed, 0.01);
        duration[next] = (int) Math.round(Mth.clamp(Mth.lerp(pace, SETTLE_TICKS, stride), 10, 80));
        lift[next] = Mth.lerp(pace, SMALL_LIFT, LIFT);
        weight[next] = Mth.lerp(pace, 0.35, 1.0);
        fromX[next] = x[next];
        fromY[next] = y[next];
        fromZ[next] = z[next];
        fromYaw[next] = yaw[next];
        toX[next] = x[next];
        toZ[next] = z[next];
        toY[next] = y[next];
    }

    /** {@return the height the body stands at over these feet: between them, a foot in the air counting as where it is going} */
    public double support() {
        double left = swinging[0] ? Mth.lerp(ease(progress[0]), fromY[0], toY[0]) : y[0];
        double right = swinging[1] ? Mth.lerp(ease(progress[1]), fromY[1], toY[1]) : y[1];
        return (left + right) / 2;
    }

    private static double restX(int foot, double bodyX, float bodyYaw) {
        // The colossus's right is (-cos yaw, -sin yaw); its left foot stands on the other side.
        return bodyX + (foot == 0 ? HIP : -HIP) * Math.cos(Math.toRadians(bodyYaw));
    }

    private static double restZ(int foot, double bodyZ, float bodyYaw) {
        return bodyZ + (foot == 0 ? HIP : -HIP) * Math.sin(Math.toRadians(bodyYaw));
    }

    private static double ease(double t) {
        return t * t * (3 - 2 * t);
    }
}
