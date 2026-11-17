package com.example.thepale.colossus;

/**
 * The shape and the motion of the Frost Colossus, shared by the server (hitboxes, the seats on its shoulders, the
 * hand that lifts a rider) and the client (the model's bones): the same numbers go in on both sides and the same
 * pose comes out, so a rider sits exactly on the bone they are drawn on.
 *
 * <p>Everything here is in the model's own space, in model pixels: x toward the colossus's right, y up, z toward its
 * back (the front is -z), the origin on the ground between its feet. The model is drawn at half size and doubled by
 * the renderer ({@link #SCALE}), so a pixel is {@link #PX} of a block. Angles are radians, turned the way the game
 * turns a bone: about z, then y, then x around the bone's pivot, and a positive turn about x swings a hanging limb
 * forward. The joints must match tools/gen_colossus_model.py, which draws the model.
 *
 * <p>No Minecraft classes are used here, so the poses can be checked outside the game.
 */
public final class ColossusPose {
    /** The renderer draws the model this many times its size. */
    public static final float SCALE = 2f;
    /** Blocks in a model pixel. */
    public static final double PX = SCALE / 16.0;

    // ----- The skeleton (tools/gen_colossus_model.py) -----
    public static final double HIP_Y = 84;
    public static final double HIP_X = 21;
    public static final double WAIST_Y = 96;
    public static final double SHOULDER_X = 64;
    public static final double SHOULDER_Y = 178;
    public static final double UPPER_ARM = 54;
    public static final double FOREARM = 62;
    public static final double SEAT_X = 46;
    public static final double SEAT_Y = 200;
    /** Toward the front edge of the shoulder, so that a rider looking ahead and down sees the ground, not snow. */
    public static final double SEAT_Z = -10;
    /** How far the hips come down to sit, and how far the chest sinks forward over the legs (the sit_down animation). */
    public static final double SIT_DROP = 62;
    public static final double SIT_HUNCH = Math.toRadians(34);
    public static final double SIT_LEGS = Math.toRadians(82);
    /** Where somebody sits in the open hand, from the wrist: on the palm (toward the body), a little down the hand. */
    public static final double PALM_OUT = 9;
    private static final double PALM_DOWN = 10;
    /** Where the palm is held when the hand is up at the shoulder: level with the seat and this far in front of it. */
    private static final double HAND_AT_SEAT_X = SEAT_X;
    private static final double HAND_AT_SEAT_Z = -44;
    /** The arms hang this far out from the body with the elbows bent this much (the idle animation). */
    private static final double ARM_OUT = Math.toRadians(4);
    private static final double ARM_BEND = Math.toRadians(9);
    private static final double ARM_SWING = Math.toRadians(15);

    // ----- The gait (the feet themselves are walked by ColossusGait) -----
    /** Blocks walked in one full cycle (a step of each leg). */
    public static final double CYCLE_BLOCKS = 10;
    /** The share of a cycle a foot is on the ground. */
    public static final double STANCE = 0.6;
    /** How far in front of and behind the hip a foot is put down at a full walk, in blocks: it follows from the two above. */
    public static final double REACH_BLOCKS = CYCLE_BLOCKS * STANCE / 2;
    private static final double ROLL = Math.toRadians(2.2);
    private static final double LEAN = Math.toRadians(2.0);
    /** A leg can slide this far into the body and this far out of it to stand on uneven ground. */
    public static final double LEG_UP = 36;
    public static final double LEG_DOWN = 16;
    /** A foot stays within this turn of the body (the leg is a square column in a square socket). */
    public static final double FOOT_TURN = Math.toRadians(26);

    // ----- Bowing down to offer a hand -----
    private static final double BOW_PITCH = Math.toRadians(60);
    private static final double BOW_CROUCH = 30;

    // ----- What goes in -----
    /** Where each foot stands against the colossus's own place on the ground, in pixels (0 = left, 1 = right). */
    public final double[][] foot = {{-HIP_X, 0, 0}, {HIP_X, 0, 0}};
    /** How far each foot is turned against the body: positive to the left, as a bone turns about y. */
    public final double[] footYaw = new double[2];
    /** How far off the ground each foot is in its step: 0 standing, 1 at the top of the step. */
    public final double[] footSwing = new double[2];
    /** How fast it walks, 0 (standing) to 1. */
    public double speed;
    /** How far it is bowed down, 0 to 1. */
    public double bow;
    /** How far it has sat down, 0 to 1. Only the server's hitboxes use it; the client plays the animation. */
    public double sit;
    /** Ticks, for the slow sway of standing. */
    public double time;
    /** The arm that is reaching (-1 the left, 1 the right, 0 neither), how far along it is and how flat its hand is. */
    public int reachSide;
    public double reach;
    public double palmUp;
    /** Where the open palm lies when it is offered on the ground (the place somebody sits in it). */
    public final double[] palmGround = new double[3];
    /** How far the hand has carried its palm from there up to the shoulder, 0 to 1. */
    public double carry;

    // ----- What comes out -----
    public double bodyY;
    public double bodyPitch;
    public double bodyRoll;
    /** Each leg's turn (x, y, z) and how far it slides up into the body. */
    public final double[][] legRot = new double[2][3];
    public final double[] legY = new double[2];
    /** How far forward each arm swings as it walks, -1 to 1 (the walk animation takes it from the model class). */
    public final double[] armSwing = new double[2];
    /** The reaching arm's bones: x, y, z turns of the upper arm, the forearm and the hand. */
    public final double[] armRot = new double[3];
    public final double[] forearmRot = new double[3];
    public final double[] handRot = new double[3];
    /** Where the palm should be, which way its fingers point (a direction on the ground), and where it ended up (the arm may not reach). */
    public final double[] palmTarget = new double[3];
    public final double[] fingers = {0, 0, -1};
    public final double[] palm = new double[3];
    /** The seats on the shoulders (0 = left, 1 = right). */
    public final double[][] seat = new double[2][3];
    /** Middles of the hitboxes: see {@link #PART_NAMES}. */
    public final double[][] part = new double[PART_COUNT][3];

    public static final int LEFT_LEG = 0;
    public static final int RIGHT_LEG = 1;
    public static final int HIPS = 2;
    public static final int CHEST = 3;
    public static final int LEFT_SHOULDER = 4;
    public static final int RIGHT_SHOULDER = 5;
    public static final int HEAD = 6;
    public static final int LEFT_UPPER_ARM = 7;
    public static final int RIGHT_UPPER_ARM = 8;
    public static final int LEFT_FOREARM = 9;
    public static final int RIGHT_FOREARM = 10;
    public static final int LEFT_HAND = 11;
    public static final int RIGHT_HAND = 12;
    /** The diamond crystals it carries on a walk: behind each seat, and among the rocks on its back. */
    public static final int LEFT_CRYSTALS = 13;
    public static final int RIGHT_CRYSTALS = 14;
    public static final int BACK_CRYSTALS = 15;
    public static final int PART_COUNT = 16;
    public static final String[] PART_NAMES = {
        "left_leg", "right_leg", "hips", "chest", "left_shoulder", "right_shoulder", "head",
        "left_upper_arm", "right_upper_arm", "left_forearm", "right_forearm", "left_hand", "right_hand",
        "left_crystals", "right_crystals", "back_crystals"};
    /** Width and height of each hitbox, in blocks. */
    public static final float[][] PART_SIZE = {
        {3.6f, 10.5f}, {3.6f, 10.5f}, {8.0f, 6.5f}, {9.0f, 8.0f}, {4.4f, 3.0f}, {4.4f, 3.0f}, {3.8f, 3.4f},
        {3.6f, 7.0f}, {3.6f, 7.0f}, {4.4f, 8.0f}, {4.4f, 8.0f}, {4.6f, 5.6f}, {4.6f, 5.6f},
        {3.4f, 6.0f}, {3.4f, 6.0f}, {3.4f, 5.0f}};

    private final double[] torso = new double[9];
    private final double[] torsoAt = new double[3];
    private final double[] m1 = new double[9];
    private final double[] m2 = new double[9];
    private final double[] m3 = new double[9];
    private final double[] v1 = new double[3];
    private final double[] v2 = new double[3];
    private final double[] v3 = new double[3];

    private static double smooth(double t) {
        t = Math.max(0, Math.min(1, t));
        return t * t * (3 - 2 * t);
    }

    /** Works the pose out from the fields above. */
    public void solve() {
        // The legs first: each is a stiff column from its hip to where its foot stands, so a foot far out in front
        // or behind asks the hips to come down. The body sinks by what the feet on the ground ask for.
        double asked = 0;
        double weight = 0;
        double[] reachDrop = new double[2];
        for (int leg = 0; leg < 2; leg++) {
            double side = leg == 0 ? -HIP_X : HIP_X;
            double dx = (foot[leg][0] - side) / HIP_Y;
            double dz = foot[leg][2] / HIP_Y;
            double turn = Math.max(-FOOT_TURN, Math.min(FOOT_TURN, footYaw[leg]));
            // A point straight below the hip goes to (cos a sin b - sin a sin c cos b, ..., -sin a cos c) for turns
            // a, c, b about x, y, z: solve that for the foot.
            double swing = Math.asin(Math.max(-0.9, Math.min(0.9, -dz / Math.cos(turn))));
            double p = Math.cos(swing);
            double q = Math.sin(swing) * Math.sin(turn);
            double splay = Math.asin(Math.max(-0.6, Math.min(0.6, dx / Math.sqrt(p * p + q * q)))) + Math.atan2(q, p);
            legRot[leg][0] = swing;
            legRot[leg][1] = turn;
            legRot[leg][2] = splay;
            reachDrop[leg] = HIP_Y * (1 - (Math.sin(swing) * Math.sin(turn) * Math.sin(splay) + Math.cos(swing) * Math.cos(splay)));
            double grounded = 1 - footSwing[leg];
            asked += reachDrop[leg] * grounded;
            weight += grounded;
        }
        double calm = 1 - speed;
        // Leans over the leg that carries it while the other swings, and sways a little when it stands.
        bodyY = -(weight > 1.0E-3 ? asked / weight : 0) * (1 - sit)
                + Math.sin(time * 0.035) * 0.6 * calm
                - BOW_CROUCH * bow;
        bodyRoll = ROLL * (footSwing[1] - footSwing[0]) + Math.toRadians(0.4) * Math.sin(time * 0.021) * calm;
        bodyPitch = -LEAN * speed - BOW_PITCH * smooth(bow);
        armSwing[0] = Math.max(-1, Math.min(1, -foot[1][2] * PX / REACH_BLOCKS));
        armSwing[1] = Math.max(-1, Math.min(1, -foot[0][2] * PX / REACH_BLOCKS));

        // The torso's place: the root (sitting), the body about the hips, the torso about the waist (sitting).
        double drop = -SIT_DROP * sit;
        rotation(torso, bodyPitch - SIT_HUNCH * sit, 0, bodyRoll);
        // Sitting turns the torso about the waist, not the hips; the difference is small, so one turn stands for both.
        torsoAt[0] = 0;
        torsoAt[1] = HIP_Y + bodyY + drop;
        torsoAt[2] = 0;

        for (int leg = 0; leg < 2; leg++) {
            double side = leg == 0 ? -HIP_X : HIP_X;
            legY[leg] = Math.max(-LEG_DOWN, Math.min(LEG_UP, foot[leg][1] - reachDrop[leg]));
            double[] middle = part[leg];
            if (sit > 0) {
                // Legs out in front, on the ground.
                double fold = SIT_LEGS * sit + legRot[leg][0] * (1 - sit);
                middle[0] = side;
                middle[1] = HIP_Y + legY[leg] + drop - Math.cos(fold) * HIP_Y / 2;
                middle[2] = -Math.sin(fold) * HIP_Y / 2;
            } else {
                middle[0] = (side + foot[leg][0]) / 2;
                middle[1] = (HIP_Y + legY[leg] + foot[leg][1]) / 2;
                middle[2] = foot[leg][2] / 2;
            }
        }

        onTorso(part[HIPS], 0, 100, 0);
        onTorso(part[CHEST], 0, 158, 0);
        onTorso(part[LEFT_SHOULDER], -SEAT_X, 188, 0);
        onTorso(part[RIGHT_SHOULDER], SEAT_X, 188, 0);
        onTorso(part[HEAD], 0, 205, -8);
        // The crystals (gen_colossus_model.py: its x is the other way round).
        onTorso(part[LEFT_CRYSTALS], -53, 222, 9);
        onTorso(part[RIGHT_CRYSTALS], 53, 224, 9);
        onTorso(part[BACK_CRYSTALS], 0, 240, 10);
        onTorso(seat[0], -SEAT_X, SEAT_Y, SEAT_Z);
        onTorso(seat[1], SEAT_X, SEAT_Y, SEAT_Z);

        if (reachSide != 0 && reach > 0) {
            // The palm's way from the ground to the shoulder: it ends level with the seat, in front of it, fingers
            // toward the neck, so whoever sits in it only has to shift back onto the shoulder. On the way it swings
            // out and forward, around the body.
            double k = smooth(carry);
            onTorso(v2, reachSide * HAND_AT_SEAT_X, SEAT_Y, HAND_AT_SEAT_Z);
            double around = Math.sin(Math.PI * k);
            palmTarget[0] = palmGround[0] + (v2[0] - palmGround[0]) * k + reachSide * 14 * around;
            palmTarget[1] = palmGround[1] + (v2[1] - palmGround[1]) * k;
            palmTarget[2] = palmGround[2] + (v2[2] - palmGround[2]) * k - 12 * around;
            fingers[0] = -reachSide * k;
            fingers[1] = 0;
            fingers[2] = -(1 - k);
        }
        for (int arm = 0; arm < 2; arm++) {
            int side = arm == 0 ? -1 : 1;
            if (side == reachSide && reach > 0) {
                solveReach(arm, side);
            } else {
                hangArm(arm, side);
            }
        }
    }

    /** An arm the animations move: only its hitboxes are worked out, from the same swing the walk animation has. */
    private void hangArm(int arm, int side) {
        double swing = ARM_SWING * armSwing[arm] * (1 - sit) + SIT_HUNCH * sit;
        rotation(m1, swing, 0, side * ARM_OUT);
        multiply(torso, m1, m2);
        onTorso(v1, side * SHOULDER_X, SHOULDER_Y, 0);
        along(part[LEFT_UPPER_ARM + arm], v1, m2, UPPER_ARM / 2);
        along(v2, v1, m2, UPPER_ARM);
        rotation(m1, ARM_BEND + Math.toRadians(40) * sit, 0, 0);
        multiply(m2, m1, m3);
        along(part[LEFT_FOREARM + arm], v2, m3, FOREARM / 2);
        along(v3, v2, m3, FOREARM);
        along(part[LEFT_HAND + arm], v3, m3, 20);
    }

    /**
     * The arm that reaches for {@link #palmTarget}: the wrist goes from where it hangs to where the open hand needs
     * it, the elbow bends out and back, and the hand turns palm up. Fills in the three bones' turns and {@link #palm}.
     */
    private void solveReach(int arm, int side) {
        double[] shoulder = v1;
        onTorso(shoulder, side * SHOULDER_X, SHOULDER_Y, 0);

        // Where the wrist hangs when the arm is left to the animations.
        rotation(m1, 0, 0, side * ARM_OUT);
        multiply(torso, m1, m2);
        double[] hangingElbow = new double[3];
        along(hangingElbow, shoulder, m2, UPPER_ARM);
        rotation(m1, ARM_BEND, 0, 0);
        multiply(m2, m1, m3);
        double[] hangingWrist = new double[3];
        along(hangingWrist, hangingElbow, m3, FOREARM);
        double[] hangingHand = m3.clone();

        // The open hand: fingers along the ground, the palm (the side toward the body) up.
        double[] flat = new double[9];
        double fx = fingers[0];
        double fz = fingers[2];
        double length = Math.sqrt(fx * fx + fz * fz);
        if (length < 1.0E-6) {
            fx = 0;
            fz = -1;
            length = 1;
        }
        fx /= length;
        fz /= length;
        // Columns: where the hand's own x, y and z axes point. Its -y runs along the fingers, its palm faces -side x.
        double[] xAxis = {0, -side, 0};
        double[] yAxis = {-fx, 0, -fz};
        double[] zAxis = cross(xAxis, yAxis);
        columns(flat, xAxis, yAxis, zAxis);

        double[] offset = {-side * PALM_OUT, -PALM_DOWN, 0};
        double[] reachedWrist = new double[3];
        apply(flat, offset, reachedWrist);
        for (int i = 0; i < 3; i++) {
            reachedWrist[i] = palmTarget[i] - reachedWrist[i];
        }
        double t = smooth(reach);
        double[] wrist = new double[3];
        for (int i = 0; i < 3; i++) {
            wrist[i] = hangingWrist[i] + (reachedWrist[i] - hangingWrist[i]) * t;
        }
        // Out to the side on the way, so the hand doesn't pass through the leg.
        wrist[0] += side * 14 * Math.sin(Math.PI * t) * 0.5;

        // Two bones to the wrist: the elbow goes back at first (as it hangs) and out to the side as the arm reaches.
        double dx = wrist[0] - shoulder[0];
        double dy = wrist[1] - shoulder[1];
        double dz = wrist[2] - shoulder[2];
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double longest = (UPPER_ARM + FOREARM) * 0.985;
        double shortest = Math.abs(FOREARM - UPPER_ARM) + 6;
        double clamped = Math.max(shortest, Math.min(longest, distance));
        if (distance < 1.0E-6) {
            dx = 0;
            dy = -1;
            dz = 0;
            distance = 1;
        }
        dx /= distance;
        dy /= distance;
        dz /= distance;
        wrist[0] = shoulder[0] + dx * clamped;
        wrist[1] = shoulder[1] + dy * clamped;
        wrist[2] = shoulder[2] + dz * clamped;
        double along = (UPPER_ARM * UPPER_ARM - FOREARM * FOREARM + clamped * clamped) / (2 * clamped);
        double out = Math.sqrt(Math.max(UPPER_ARM * UPPER_ARM - along * along, 0));
        double[] pole = {side * 1.0 * t, 0.15 * t, 1 - 0.45 * t};
        double dot = pole[0] * dx + pole[1] * dy + pole[2] * dz;
        pole[0] -= dot * dx;
        pole[1] -= dot * dy;
        pole[2] -= dot * dz;
        normalize(pole);
        double[] elbow = {
            shoulder[0] + dx * along + pole[0] * out, shoulder[1] + dy * along + pole[1] * out, shoulder[2] + dz * along + pole[2] * out};

        // Each bone hangs along its own -y; its x is the axis the elbow bends about.
        double[] upperDown = {elbow[0] - shoulder[0], elbow[1] - shoulder[1], elbow[2] - shoulder[2]};
        double[] foreDown = {wrist[0] - elbow[0], wrist[1] - elbow[1], wrist[2] - elbow[2]};
        normalize(upperDown);
        normalize(foreDown);
        double[] hinge = cross(upperDown, foreDown);
        if (hinge[0] * hinge[0] + hinge[1] * hinge[1] + hinge[2] * hinge[2] < 1.0E-10) {
            hinge = new double[] {1, 0, 0};
        }
        normalize(hinge);
        double[] upper = frame(hinge, upperDown);
        double[] fore = frame(hinge, foreDown);
        double[] hand = slerp(fore, flat, smooth(palmUp));

        // Each bone's turn against the bone it hangs on.
        double[] local = new double[9];
        multiply(transpose(torso), upper, local);
        angles(local, armRot);
        multiply(transpose(upper), fore, local);
        angles(local, forearmRot);
        multiply(transpose(fore), hand, local);
        angles(local, handRot);

        apply(hand, offset, palm);
        for (int i = 0; i < 3; i++) {
            palm[i] += wrist[i];
        }
        for (int i = 0; i < 3; i++) {
            part[LEFT_UPPER_ARM + arm][i] = (shoulder[i] + elbow[i]) / 2;
            part[LEFT_FOREARM + arm][i] = (elbow[i] + wrist[i]) / 2;
        }
        double[] middle = {0, -20, 0};
        apply(hand, middle, part[LEFT_HAND + arm]);
        for (int i = 0; i < 3; i++) {
            part[LEFT_HAND + arm][i] += wrist[i];
        }
    }

    /** {@return the shoulder of this arm (0 = left) at the current pose, into {@code out}} */
    public double[] shoulder(int arm, double[] out) {
        onTorso(out, (arm == 0 ? -1 : 1) * SHOULDER_X, SHOULDER_Y, 0);
        return out;
    }

    /** A point of the upper body (given as it stands at rest) where the pose puts it. */
    private void onTorso(double[] out, double x, double y, double z) {
        double rx = x;
        double ry = y - HIP_Y;
        double rz = z;
        out[0] = torsoAt[0] + torso[0] * rx + torso[1] * ry + torso[2] * rz;
        out[1] = torsoAt[1] + torso[3] * rx + torso[4] * ry + torso[5] * rz;
        out[2] = torsoAt[2] + torso[6] * rx + torso[7] * ry + torso[8] * rz;
    }

    /** {@code from} moved {@code length} down the -y axis of a bone turned by {@code m}. */
    private static void along(double[] out, double[] from, double[] m, double length) {
        out[0] = from[0] - m[1] * length;
        out[1] = from[1] - m[4] * length;
        out[2] = from[2] - m[7] * length;
    }

    // ----- Small 3x3 matrix helpers (row-major) -----

    /** The matrix of a bone turned by these angles: about z, then y, then x (so x is applied to a point first). */
    static void rotation(double[] out, double x, double y, double z) {
        double cx = Math.cos(x);
        double sx = Math.sin(x);
        double cy = Math.cos(y);
        double sy = Math.sin(y);
        double cz = Math.cos(z);
        double sz = Math.sin(z);
        out[0] = cy * cz;
        out[1] = sx * sy * cz - cx * sz;
        out[2] = cx * sy * cz + sx * sz;
        out[3] = cy * sz;
        out[4] = sx * sy * sz + cx * cz;
        out[5] = cx * sy * sz - sx * cz;
        out[6] = -sy;
        out[7] = sx * cy;
        out[8] = cx * cy;
    }

    /** The angles {@link #rotation} would need for this matrix. */
    static void angles(double[] m, double[] out) {
        double sy = -m[6];
        if (sy > 0.99999) {
            out[0] = Math.atan2(m[1], m[4]);
            out[1] = Math.PI / 2;
            out[2] = 0;
        } else if (sy < -0.99999) {
            out[0] = Math.atan2(-m[1], m[4]);
            out[1] = -Math.PI / 2;
            out[2] = 0;
        } else {
            out[0] = Math.atan2(m[7], m[8]);
            out[1] = Math.asin(sy);
            out[2] = Math.atan2(m[3], m[0]);
        }
    }

    static void multiply(double[] a, double[] b, double[] out) {
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                out[row * 3 + col] = a[row * 3] * b[col] + a[row * 3 + 1] * b[3 + col] + a[row * 3 + 2] * b[6 + col];
            }
        }
    }

    static double[] transpose(double[] m) {
        return new double[] {m[0], m[3], m[6], m[1], m[4], m[7], m[2], m[5], m[8]};
    }

    static void apply(double[] m, double[] v, double[] out) {
        double x = m[0] * v[0] + m[1] * v[1] + m[2] * v[2];
        double y = m[3] * v[0] + m[4] * v[1] + m[5] * v[2];
        double z = m[6] * v[0] + m[7] * v[1] + m[8] * v[2];
        out[0] = x;
        out[1] = y;
        out[2] = z;
    }

    private static void columns(double[] out, double[] x, double[] y, double[] z) {
        for (int row = 0; row < 3; row++) {
            out[row * 3] = x[row];
            out[row * 3 + 1] = y[row];
            out[row * 3 + 2] = z[row];
        }
    }

    /** The turn of a bone whose x axis is {@code hinge} and which hangs along {@code down}. */
    private static double[] frame(double[] hinge, double[] down) {
        double[] y = {-down[0], -down[1], -down[2]};
        // Keep x square to y (the two bones of an arm share the hinge, but only nearly, after clamping).
        double dot = hinge[0] * y[0] + hinge[1] * y[1] + hinge[2] * y[2];
        double[] x = {hinge[0] - dot * y[0], hinge[1] - dot * y[1], hinge[2] - dot * y[2]};
        normalize(x);
        double[] out = new double[9];
        columns(out, x, y, cross(x, y));
        return out;
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[] {a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static void normalize(double[] v) {
        double length = Math.sqrt(v[0] * v[0] + v[1] * v[1] + v[2] * v[2]);
        if (length > 1.0E-9) {
            v[0] /= length;
            v[1] /= length;
            v[2] /= length;
        }
    }

    /** Part of the way from one turn to another, along the shortest arc. */
    private static double[] slerp(double[] from, double[] to, double t) {
        if (t <= 0) {
            return from.clone();
        }
        if (t >= 1) {
            return to.clone();
        }
        double[] a = quaternion(from);
        double[] b = quaternion(to);
        double dot = a[0] * b[0] + a[1] * b[1] + a[2] * b[2] + a[3] * b[3];
        if (dot < 0) {
            dot = -dot;
            for (int i = 0; i < 4; i++) {
                b[i] = -b[i];
            }
        }
        double wa;
        double wb;
        if (dot > 0.9995) {
            wa = 1 - t;
            wb = t;
        } else {
            double angle = Math.acos(dot);
            wa = Math.sin((1 - t) * angle) / Math.sin(angle);
            wb = Math.sin(t * angle) / Math.sin(angle);
        }
        double x = a[0] * wa + b[0] * wb;
        double y = a[1] * wa + b[1] * wb;
        double z = a[2] * wa + b[2] * wb;
        double w = a[3] * wa + b[3] * wb;
        double n = Math.sqrt(x * x + y * y + z * z + w * w);
        x /= n;
        y /= n;
        z /= n;
        w /= n;
        return new double[] {
            1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w),
            2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w),
            2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)};
    }

    private static double[] quaternion(double[] m) {
        double trace = m[0] + m[4] + m[8];
        double x;
        double y;
        double z;
        double w;
        if (trace > 0) {
            double s = Math.sqrt(trace + 1) * 2;
            w = s / 4;
            x = (m[7] - m[5]) / s;
            y = (m[2] - m[6]) / s;
            z = (m[3] - m[1]) / s;
        } else if (m[0] > m[4] && m[0] > m[8]) {
            double s = Math.sqrt(1 + m[0] - m[4] - m[8]) * 2;
            w = (m[7] - m[5]) / s;
            x = s / 4;
            y = (m[1] + m[3]) / s;
            z = (m[2] + m[6]) / s;
        } else if (m[4] > m[8]) {
            double s = Math.sqrt(1 + m[4] - m[0] - m[8]) * 2;
            w = (m[2] - m[6]) / s;
            x = (m[1] + m[3]) / s;
            y = s / 4;
            z = (m[5] + m[7]) / s;
        } else {
            double s = Math.sqrt(1 + m[8] - m[0] - m[4]) * 2;
            w = (m[3] - m[1]) / s;
            x = (m[2] + m[6]) / s;
            y = (m[5] + m[7]) / s;
            z = s / 4;
        }
        return new double[] {x, y, z, w};
    }
}
