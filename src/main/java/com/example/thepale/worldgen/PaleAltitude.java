package com.example.thepale.worldgen;

/**
 * High above the Pale the air is clear: from {@link #CLEARS_FROM} up the fog and the poison thin out, and from
 * {@link #CLEAR_AT} there are none. Below lies the sea of clouds ({@code PaleCloudSea}). The only way to cross between
 * the islands without the poison is up here, on elytra.
 */
public final class PaleAltitude {
    public static final double CLEARS_FROM = 450;
    public static final double CLEAR_AT = 500;

    private PaleAltitude() {}

    /** {@return how much of the Pale's fog and poison there is at this height: 1 below CLEARS_FROM, 0 from CLEAR_AT} */
    public static float strength(double y) {
        double t = (CLEAR_AT - y) / (CLEAR_AT - CLEARS_FROM);
        if (t >= 1) {
            return 1;
        }
        if (t <= 0) {
            return 0;
        }
        return (float) (t * t * (3 - 2 * t));
    }
}
