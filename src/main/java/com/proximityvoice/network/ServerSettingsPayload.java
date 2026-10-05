package com.proximityvoice.network;

import com.proximityvoice.ProximityVoice;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Server -> client: "this server has Proximity Voice, here are its settings". */
public record ServerSettingsPayload(int protocol, boolean enabled, float maxDistance, float fadeStartDistance) implements CustomPacketPayload {
	public static final CustomPacketPayload.Type<ServerSettingsPayload> TYPE = new CustomPacketPayload.Type<>(ProximityVoice.id("settings"));
	public static final StreamCodec<RegistryFriendlyByteBuf, ServerSettingsPayload> CODEC = CustomPacketPayload.codec(ServerSettingsPayload::write, ServerSettingsPayload::new);

	public ServerSettingsPayload(RegistryFriendlyByteBuf buf) {
		this(buf.readInt(), buf.readBoolean(), buf.readFloat(), buf.readFloat());
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeInt(this.protocol);
		buf.writeBoolean(this.enabled);
		buf.writeFloat(this.maxDistance);
		buf.writeFloat(this.fadeStartDistance);
	}

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return TYPE;
	}
}
