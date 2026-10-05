package com.proximityvoice.network;

import com.proximityvoice.ProximityVoice;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client -> server: one encoded 20 ms frame of the player's microphone. */
public record MicAudioPayload(byte[] data) implements CustomPacketPayload {
	public static final int MAX_DATA = 1024;
	public static final CustomPacketPayload.Type<MicAudioPayload> TYPE = new CustomPacketPayload.Type<>(ProximityVoice.id("mic"));
	public static final StreamCodec<RegistryFriendlyByteBuf, MicAudioPayload> CODEC = CustomPacketPayload.codec(MicAudioPayload::write, MicAudioPayload::new);

	public MicAudioPayload(RegistryFriendlyByteBuf buf) {
		this(PacketUtil.readBytes(buf, MAX_DATA));
	}

	public void write(RegistryFriendlyByteBuf buf) {
		PacketUtil.writeBytes(buf, this.data);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
