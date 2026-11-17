package com.example.thepale.colossus;

import com.example.thepale.ThePale;
import net.minecraft.core.registries.Registries;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The Frost Colossus's entity type and sounds. Each sound is its own sound event, so a resource pack can give it a
 * real recording; assets/thepale/sounds.json maps them to vanilla sounds at another pitch.
 */
public final class ColossusRegistry {
    private static final DeferredRegister<EntityType<?>> ENTITIES = DeferredRegister.create(Registries.ENTITY_TYPE, ThePale.MOD_ID);
    private static final DeferredRegister<SoundEvent> SOUNDS = DeferredRegister.create(Registries.SOUND_EVENT, ThePale.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<FrostColossus>> FROST_COLOSSUS = ENTITIES.register(
            "frost_colossus",
            () -> EntityType.Builder.<FrostColossus>of(FrostColossus::new, MobCategory.CREATURE)
                    // Only a small box at its feet; the rest of it is its parts (see FrostColossus).
                    .sized(2.5f, 2.5f)
                    .fireImmune()
                    // Seen from as far as chunks are sent, and moved every tick so that a rider's seat is smooth.
                    .clientTrackingRange(16)
                    .updateInterval(1)
                    .build("frost_colossus"));

    /** It was struck and has noticed. */
    public static final DeferredHolder<SoundEvent, SoundEvent> HUM = sound("colossus.hum");
    public static final DeferredHolder<SoundEvent, SoundEvent> STEP = sound("colossus.step");
    public static final DeferredHolder<SoundEvent, SoundEvent> SIGH = sound("colossus.sigh");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHAKE_OFF = sound("colossus.shake_off");

    private ColossusRegistry() {}

    private static DeferredHolder<SoundEvent, SoundEvent> sound(String name) {
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(ThePale.id(name)));
    }

    public static void register(IEventBus modBus) {
        ENTITIES.register(modBus);
        SOUNDS.register(modBus);
        modBus.addListener((EntityAttributeCreationEvent event) ->
                event.put(FROST_COLOSSUS.get(), FrostColossus.createAttributes().build()));
    }
}
