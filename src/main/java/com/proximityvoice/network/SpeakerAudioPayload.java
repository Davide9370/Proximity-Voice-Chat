package com.proximityvoice.network;

import java.util.UUID;

import com.proximityvoice.ProximityVoice;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Server -> client: a frame of someone else's voice, plus where they were standing
 * so the client can do distance fade + left/right panning.
 */
public record SpeakerAudioPayload(UUID sender, float x, float y, float z, byte[] data) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<SpeakerAudioPayload> TYPE = new CustomPacketPayload.Type<>(ProximityVoice.id("speaker"));
	public static final StreamCodec<RegistryFriendlyByteBuf, SpeakerAudioPayload> CODEC = CustomPacketPayload.codec(SpeakerAudioPayload::write, SpeakerAudioPayload::new);

	public SpeakerAudioPayload(RegistryFriendlyByteBuf buf) {
		this(new UUID(buf.readLong(), buf.readLong()), buf.readFloat(), buf.readFloat(), buf.readFloat(),
				PacketUtil.readBytes(buf, MicAudioPayload.MAX_DATA));
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeLong(this.sender.getMostSignificantBits());
		buf.writeLong(this.sender.getLeastSignificantBits());
		buf.writeFloat(this.x);
		buf.writeFloat(this.y);
		buf.writeFloat(this.z);
		PacketUtil.writeBytes(buf, this.data);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
