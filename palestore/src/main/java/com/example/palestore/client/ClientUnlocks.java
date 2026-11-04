package com.example.palestore.client;

import java.util.Collection;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;

/**
 * What the client knows about this player's purchases. Plain Java on purpose (no Minecraft client
 * classes), so it is safe to reference from common code. JEI registers a listener to react to changes.
 */
public final class ClientUnlocks {
    private static Set<ResourceLocation> unlocked = Set.of();
    private static Runnable listener = () -> {};

    public static void set(Collection<ResourceLocation> ids) {
        unlocked = Set.copyOf(ids);
        listener.run();
    }

    public static boolean has(ResourceLocation id) {
        return unlocked.contains(id);
    }

    public static void setListener(Runnable newListener) {
        listener = newListener;
    }

    private ClientUnlocks() {}
}
