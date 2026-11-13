package com.example.thepale.finale;

import net.minecraft.Util;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;

/**
 * The finale as this client last heard of it from the server ({@link FinalePayload}). No client-only classes here, so
 * the payload handler can be registered on a dedicated server too; the drawing is in finale.client.FinaleClient.
 */
public final class FinaleView {
    private static final FinalePayload NOTHING = new FinalePayload(0, 0, false, 0, 0, false, 0, 1200);

    private static volatile FinalePayload last = NOTHING;
    private static volatile long receivedAt;

    private FinaleView() {}

    public static void onRegisterPayloads(RegisterPayloadHandlersEvent event) {
        // Optional: a client without the mod can still join.
        event.registrar("1").optional().playToClient(FinalePayload.TYPE, FinalePayload.STREAM_CODEC, (payload, context) -> accept(payload));
    }

    static void accept(FinalePayload payload) {
        receivedAt = Util.getMillis();
        last = payload;
    }

    /** A server without the mod sends nothing: don't carry the last world's finale over. */
    public static void reset() {
        last = NOTHING;
    }

    public static FinalePayload last() {
        return last;
    }

    /**
     * {@return how much of the Pale is left to see, 1 to 0}: 1 until the Pale is cleansed, then easing down to 0 over
     * the fade the server set. Everything the client draws of the Pale (the wall, the fog, the grey water, the sea of
     * clouds, the ash) is scaled by this. Safe to call from any thread.
     */
    public static float paleStrength() {
        FinalePayload payload = last;
        if (!payload.cleansed()) {
            return 1f;
        }
        double ticks = payload.cleansedTicks() + (Util.getMillis() - receivedAt) / 50.0;
        float t = (float) Mth.clamp(ticks / Math.max(payload.fadeTicks(), 1), 0, 1);
        return 1f - t * t * (3f - 2f * t);
    }
}
