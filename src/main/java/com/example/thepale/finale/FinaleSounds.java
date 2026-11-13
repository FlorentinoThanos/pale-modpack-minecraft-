package com.example.thepale.finale;

import com.example.thepale.ThePale;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The finale's sounds. Each is its own sound event, so a resource pack (or a later version) can give it a real
 * recording; for now assets/thepale/sounds.json maps them to vanilla sounds at another pitch.
 */
public final class FinaleSounds {
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ThePale.MOD_ID);

    /** Day 1: something woke in the Pale. */
    public static final DeferredHolder<SoundEvent, SoundEvent> AWAKEN = sound("finale.awaken");
    /** Its silhouette shows on the horizon. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HORIZON = sound("finale.horizon");
    /** It has reached the shore. */
    public static final DeferredHolder<SoundEvent, SoundEvent> ARRIVE = sound("finale.arrive");
    /** The Pale is cleansed. */
    public static final DeferredHolder<SoundEvent, SoundEvent> CLEANSE = sound("finale.cleanse");

    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_AMBIENT = sound("boss.ambient");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_HURT = sound("boss.hurt");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_ARMORED = sound("boss.armored");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_DEATH = sound("boss.death");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_WINDUP = sound("boss.windup");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_SLAM = sound("boss.slam");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_SEVER = sound("boss.sever");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_SCREAM = sound("boss.scream");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_SUMMON = sound("boss.summon");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_GRAB = sound("boss.grab");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_EYE_OPEN = sound("boss.eye_open");
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_FALL = sound("boss.fall");
    /** Looped by the client while the boss is near. */
    public static final DeferredHolder<SoundEvent, SoundEvent> BOSS_MUSIC = sound("boss.music");

    private FinaleSounds() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ThePale.id(name)));
    }

    public static void register(IEventBus modBus) {
        SOUNDS.register(modBus);
    }
}
