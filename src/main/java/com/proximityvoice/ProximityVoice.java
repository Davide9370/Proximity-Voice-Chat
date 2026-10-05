package com.proximityvoice;

import com.proximityvoice.network.ClientStatePayload;
import com.proximityvoice.network.MicAudioPayload;
import com.proximityvoice.network.ServerSettingsPayload;
import com.proximityvoice.network.SpeakerAudioPayload;
import com.proximityvoice.server.VoiceServer;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Proximity Voice: voice chat that rides on the normal Minecraft connection
 * (custom payload packets), so the server does not need a second port.
 *
 * Audio format on the wire: 16 kHz mono, 20 ms frames, IMA-ADPCM (4 bits/sample)
 * = 163 bytes per frame, about 65 kbit/s per person while they are talking.
 */
public class ProximityVoice implements ModInitializer {
	public static final String MOD_ID = "proximityvoice";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Bump this whenever the packet format changes. */
	public static final int PROTOCOL_VERSION = 1;

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}

	@Override
	public void onInitialize() {
		// client -> server
		PayloadTypeRegistry.playC2S().register(MicAudioPayload.TYPE, MicAudioPayload.CODEC);
		PayloadTypeRegistry.playC2S().register(ClientStatePayload.TYPE, ClientStatePayload.CODEC);
		// server -> client
		PayloadTypeRegistry.playS2C().register(SpeakerAudioPayload.TYPE, SpeakerAudioPayload.CODEC);
		PayloadTypeRegistry.playS2C().register(ServerSettingsPayload.TYPE, ServerSettingsPayload.CODEC);

		VoiceServer.init();
		LOGGER.info("Proximity Voice loaded (protocol {})", PROTOCOL_VERSION);
	}
}
