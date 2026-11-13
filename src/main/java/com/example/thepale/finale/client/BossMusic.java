package com.example.thepale.finale.client;

import com.example.thepale.finale.FinaleSounds;
import com.example.thepale.finale.boss.PatchworkAbomination;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import org.jetbrains.annotations.Nullable;

/** Loops the boss's music (the sound event thepale:boss.music) while it is alive within earshot. */
public final class BossMusic {
    private static final double RANGE = 96;

    @Nullable
    private static Loop playing;

    private BossMusic() {}

    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            playing = null;
            return;
        }
        if (mc.player.tickCount % 20 != 0 || (playing != null && !playing.isStopped() && mc.getSoundManager().isActive(playing))) {
            return;
        }
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity instanceof PatchworkAbomination boss && boss.isAlive() && boss.distanceToSqr(mc.player) < RANGE * RANGE) {
                mc.getMusicManager().stopPlaying();
                playing = new Loop(boss);
                mc.getSoundManager().play(playing);
                return;
            }
        }
    }

    private static final class Loop extends AbstractTickableSoundInstance {
        private final PatchworkAbomination boss;

        Loop(PatchworkAbomination boss) {
            super(FinaleSounds.BOSS_MUSIC.get(), SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.boss = boss;
            this.looping = true;
            this.delay = 0;
            this.volume = 1f;
            this.relative = true;
            this.attenuation = Attenuation.NONE;
        }

        @Override
        public void tick() {
            Minecraft mc = Minecraft.getInstance();
            if (boss.isRemoved() || !boss.isAlive() || mc.player == null || boss.distanceToSqr(mc.player) > RANGE * RANGE * 1.5) {
                // Fade out rather than cut.
                volume -= 0.02f;
                if (volume <= 0) {
                    stop();
                }
            }
        }
    }
}
