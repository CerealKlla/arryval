package com.github.cerealklla.arryval.tts.client;

import java.io.ByteArrayInputStream;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent;

import com.github.cerealklla.arryval.ArryvalMod;
import com.github.cerealklla.arryval.tts.TutorialVoicePayload;

/**
 * Plays a received tutorial line's WAV bytes locally, entirely outside Minecraft's own sound
 * engine -- a plain {@code javax.sound.sampled.Clip} is simplest and sufficient here (no 3D
 * positioning/attenuation needed: the tutorial Wyrld is private to exactly one player, who's
 * always close enough to the guide to hear it regardless). Standard WAV bytes decode directly via
 * {@code AudioSystem}, no manual PCM parsing needed.
 */
public final class ClientTutorialVoicePlayer {

    private ClientTutorialVoicePlayer() {
    }

    public static void play(TutorialVoicePayload payload) {
        try {
            AudioInputStream stream = AudioSystem.getAudioInputStream(new ByteArrayInputStream(payload.wavBytes()));
            // Real bug found live, 2026-11-06: Clip#open(AudioInputStream) sizes its internal buffer
            // from stream.getFrameLength() * format.getFrameSize() -- for a real Kokoro-produced WAV
            // this came back negative ("Audio data < 0"), not Kokoro itself being unreachable (a
            // direct test call against the exact same request shape worked fine). Reading the whole
            // stream into a byte array first and using Clip's explicit-buffer-size overload sidesteps
            // that miscalculation entirely -- the real length is just data.length, no frame-length
            // arithmetic involved.
            byte[] data = stream.readAllBytes();
            AudioFormat format = stream.getFormat();
            Clip clip = AudioSystem.getClip();
            clip.open(format, data, 0, data.length);
            clip.addLineListener(event -> {
                if (event.getType() == LineEvent.Type.STOP) {
                    clip.close();
                }
            });
            clip.start();
        } catch (Exception e) {
            ArryvalMod.LOGGER.warn("Failed to play tutorial voice line", e);
        }
    }
}
