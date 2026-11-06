package com.github.cerealklla.arryval.tts;

import io.netty.buffer.ByteBuf;

import com.github.cerealklla.arryval.ArryvalMod;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/**
 * Server-to-client: raw WAV bytes (from {@code api.Nuclyus#speak}) for the receiving player alone
 * to play locally. Deliberately NOT routed through Audyble/Simple Voice Chat's positional audio
 * pipeline -- the tutorial Wyrld is private to exactly one player at a time, so there's no real
 * multi-listener positional benefit to earn back the cost of a new Audyble/voicechat-api dependency
 * here. See {@code client.ClientTutorialVoicePlayer} for playback.
 */
public record TutorialVoicePayload(byte[] wavBytes) implements CustomPacketPayload {

    public static final Type<TutorialVoicePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(ArryvalMod.MODID, "tutorial_voice"));

    // 2 MiB ceiling -- a single tutorial line at Kokoro's native 24kHz mono 16-bit output is well
    // under 1 MiB even for a long sentence (~20s); generous headroom, not a tuned limit.
    public static final StreamCodec<ByteBuf, TutorialVoicePayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.byteArray(2_000_000), TutorialVoicePayload::wavBytes,
            TutorialVoicePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
