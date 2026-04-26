package org.test.magicmod.audio;

import net.minecraft.resources.Identifier;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractSoundInstance;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.client.sounds.WeighedSoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Util;
import net.minecraft.util.valueproviders.ConstantFloat;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import org.test.magicmod.Magicmod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public final class AudioController {
    private static final List<TimedSound> TIMED_SOUNDS = new ArrayList<>();
    private static final String MUSIC_ROOT = "music/";
    private static final String LEGACY_SOUNDS_ROOT = "sounds/";

    private AudioController() {
    }

    public static void register() {
        TickEvent.ClientTickEvent.Post.BUS.addListener(AudioController::onClientTick);
        ClientPlayerNetworkEvent.LoggingOut.BUS.addListener(event -> stopAllMusic());
    }

    public static void playMusic(String soundPath, float volume, boolean loop, int stopAtMs) {
        ResolvedSound resolved = resolveSound(soundPath);
        if (resolved == null) {
            return;
        }
        float safeVolume = clamp(volume, 0.0f, 1.0f);
        Sound sound = new DirectSound(resolved.fileLocation(), true);
        SoundInstance instance = new DirectSoundInstance(resolved.eventId(), sound, safeVolume, loop);
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        soundManager.play(instance);
        if (!loop && stopAtMs > 0) {
            TIMED_SOUNDS.add(new TimedSound(instance, resolved.eventId(), Util.getMillis() + stopAtMs));
        }
    }

    public static void stopMusic(String soundPath) {
        ResolvedSound resolved = resolveSound(soundPath);
        if (resolved == null) {
            return;
        }
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        soundManager.stop(resolved.eventId(), SoundSource.MUSIC);
        removeTimedSounds(resolved.eventId());
    }

    public static void stopAllMusic() {
        Minecraft.getInstance().getSoundManager().stop(null, SoundSource.MUSIC);
        TIMED_SOUNDS.clear();
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post event) {
        if (TIMED_SOUNDS.isEmpty()) {
            return;
        }
        long now = Util.getMillis();
        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        Iterator<TimedSound> iterator = TIMED_SOUNDS.iterator();
        while (iterator.hasNext()) {
            TimedSound entry = iterator.next();
            if (now >= entry.stopAtMs) {
                soundManager.stop(entry.instance);
                iterator.remove();
                continue;
            }
            if (!soundManager.isActive(entry.instance)) {
                iterator.remove();
            }
        }
    }

    private static void removeTimedSounds(Identifier eventId) {
        Iterator<TimedSound> iterator = TIMED_SOUNDS.iterator();
        while (iterator.hasNext()) {
            TimedSound entry = iterator.next();
            if (eventId.equals(entry.eventId)) {
                iterator.remove();
            }
        }
    }

    private static ResolvedSound resolveSound(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String value = raw.trim().replace('\\', '/');
        String namespace = Magicmod.MODID;
        String path = value;
        if (value.contains(":")) {
            try {
                Identifier parsed = Identifier.parse(value);
                namespace = parsed.getNamespace();
                path = parsed.getPath();
            } catch (Exception ignored) {
                return null;
            }
        }
        if (path.startsWith(LEGACY_SOUNDS_ROOT)) {
            path = path.substring(LEGACY_SOUNDS_ROOT.length());
        }
        if (path.endsWith(".ogg")) {
            path = path.substring(0, path.length() - 4);
        }
        if (path.startsWith(MUSIC_ROOT)) {
            path = path.substring(MUSIC_ROOT.length());
        }
        if (path.isBlank()) {
            return null;
        }
        String normalized = MUSIC_ROOT + path;
        try {
            Identifier eventId = Identifier.fromNamespaceAndPath(namespace, normalized);
            Identifier fileLocation = Identifier.fromNamespaceAndPath(namespace, normalized + ".ogg");
            return new ResolvedSound(eventId, fileLocation);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    private record ResolvedSound(Identifier eventId, Identifier fileLocation) {
    }

    private record TimedSound(SoundInstance instance, Identifier eventId, long stopAtMs) {
    }

    private static final class DirectSoundInstance extends AbstractSoundInstance {
        private final WeighedSoundEvents events;

        private DirectSoundInstance(Identifier eventId, Sound sound, float volume, boolean loop) {
            super(eventId, SoundSource.MUSIC, SoundInstance.createUnseededRandom());
            this.sound = sound;
            this.events = new WeighedSoundEvents(eventId, null);
            this.events.addSound(sound);
            this.volume = volume;
            this.looping = loop;
            this.attenuation = SoundInstance.Attenuation.NONE;
            this.relative = true;
        }

        @Override
        public WeighedSoundEvents resolve(SoundManager soundManager) {
            return this.events;
        }

        @Override
        public Sound getSound() {
            return this.sound;
        }
    }

    private static final class DirectSound extends Sound {
        private final Identifier filePath;

        private DirectSound(Identifier filePath, boolean stream) {
            super(filePath, ConstantFloat.of(1.0F), ConstantFloat.of(1.0F), 1, Sound.Type.FILE, stream, false, 16);
            this.filePath = filePath;
        }

        @Override
        public Identifier getPath() {
            return this.filePath;
        }
    }
}
