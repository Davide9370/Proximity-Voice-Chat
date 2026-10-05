package com.proximityvoice.network;

import com.proximityvoice.ProximityVoice;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client -> server: "I have the mod, and this is my mic/speaker state".
 * If the speaker is off the server stops sending us audio (saves bandwidth).
 */
public record ClientStatePayload(int protocol, boolean micEnabled, boolean speakerEnabled) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ClientStatePayload> TYPE = new CustomPacketPayload.Type<>(ProximityVoice.id("state"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ClientStatePayload> CODEC = CustomPacketPayload.codec(ClientStatePayload::write, ClientStatePayload::new);

	public ClientStatePayload(RegistryFriendlyByteBuf buf) {
		this(buf.readInt(), buf.readBoolean(), buf.readBoolean());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeInt(this.protocol);
		buf.writeBoolean(this.micEnabled);
		buf.writeBoolean(this.speakerEnabled);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
