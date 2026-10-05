package com.proximityvoice.server;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.proximityvoice.ProximityVoice;
import com.proximityvoice.config.ServerConfig;
import com.proximityvoice.network.ClientStatePayload;
import com.proximityvoice.network.MicAudioPayload;
import com.proximityvoice.network.ServerSettingsPayload;
import com.proximityvoice.network.SpeakerAudioPayload;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The server is the one that decides who hears who:
 * a mic frame from player A is only forwarded to players with the mod who are
 * in the same dimension and within max-distance. Everyone else never receives it.
 */
public final class VoiceServer {
	private static final Map<UUID, VoicePlayer> PLAYERS = new ConcurrentHashMap<>();
	private static volatile ServerConfig config = new ServerConfig();

	private VoiceServer() {
	}

	/** Per-player state, only touched on the server thread. */
	static final class VoicePlayer {
		boolean ready;            // has the mod with a matching protocol
		boolean micEnabled = true;
		boolean speakerEnabled = true;
		long rateWindowStart;
		int rateCount;
	}

	public static void init() {
		config = ServerConfig.load();

		ServerPlayNetworking.registerGlobalReceiver(ClientStatePayload.TYPE, (payload, context) -> onClientState(payload, context.player()));
		ServerPlayNetworking.registerGlobalReceiver(MicAudioPayload.TYPE, (payload, context) -> onMicAudio(payload, context.player(), context.server()));

		ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> onJoin(handler.player));
		ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> PLAYERS.remove(handler.player.getUUID()));
		ServerLifecycleEvents.SERVER_STOPPED.register(server -> PLAYERS.clear());

		CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> VoiceCommands.register(dispatcher));
	}

	public static ServerConfig config() {
		return config;
	}

	public static void reload(MinecraftServer server) {
		config = ServerConfig.load();
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			if (PLAYERS.containsKey(player.getUUID())) {
				ServerPlayNetworking.send(player, settingsPacket());
			}
		}
	}

	public static boolean hasVoice(ServerPlayer player) {
		VoicePlayer vp = PLAYERS.get(player.getUUID());
		return vp != null && vp.ready;
	}

	private static ServerSettingsPayload settingsPacket() {
		ServerConfig c = config;
		return new ServerSettingsPayload(ProximityVoice.PROTOCOL_VERSION, c.enabled, (float) c.maxDistance, (float) c.fadeStartDistance);
	}

	private static void onJoin(ServerPlayer player) {
		ServerConfig c = config;

		// Only players whose client registered our channel get voice packets.
		// Vanilla Java clients and Bedrock players (through Geyser) never register it,
		// so they are never sent anything they can't understand.
		if (ServerPlayNetworking.canSend(player, ServerSettingsPayload.TYPE)) {
			PLAYERS.put(player.getUUID(), new VoicePlayer());
			ServerPlayNetworking.send(player, settingsPacket());
			return;
		}

		if (!c.enabled) return;
		if (GeyserCompat.isBedrockPlayer(player.getUUID())) {
			if (c.notifyBedrockPlayers) player.sendSystemMessage(colored(c.bedrockMessage));
		} else if (c.notifyPlayersWithoutMod) {
			player.sendSystemMessage(colored(c.noModMessage));
		}
	}

	private static void onClientState(ClientStatePayload payload, ServerPlayer player) {
		VoicePlayer vp = PLAYERS.computeIfAbsent(player.getUUID(), uuid -> new VoicePlayer());
		boolean wasReady = vp.ready;
		vp.ready = payload.protocol() == ProximityVoice.PROTOCOL_VERSION;
		vp.micEnabled = payload.micEnabled();
		vp.speakerEnabled = payload.speakerEnabled();

		if (!vp.ready && !wasReady) {
			player.sendSystemMessage(colored("&cProximity Voice version mismatch (you: " + payload.protocol()
					+ ", server: " + ProximityVoice.PROTOCOL_VERSION + "). Update the mod to use voice chat."));
		}
	}

	private static void onMicAudio(MicAudioPayload payload, ServerPlayer speaker, MinecraftServer server) {
		ServerConfig c = config;
		if (!c.enabled) return;

		VoicePlayer from = PLAYERS.get(speaker.getUUID());
		if (from == null || !from.ready || !from.micEnabled) return;

		byte[] data = payload.data();
		if (data.length < 4 || data.length > MicAudioPayload.MAX_DATA) return;

		// simple anti-spam: max N packets per second per player
		long now = System.currentTimeMillis();
		if (now - from.rateWindowStart >= 1000) {
			from.rateWindowStart = now;
			from.rateCount = 0;
		}
		if (++from.rateCount > c.maxPacketsPerSecond) return;

		double sx = speaker.getX();
		double sy = speaker.getEyeY();
		double sz = speaker.getZ();
		double maxSq = c.maxDistance * c.maxDistance;
		boolean speakerIsSpectator = speaker.isSpectator();

		SpeakerAudioPayload out = null;
		for (ServerPlayer listener : server.getPlayerList().getPlayers()) {
			if (listener == speaker) continue;
			if (listener.level() != speaker.level()) continue;

			VoicePlayer to = PLAYERS.get(listener.getUUID());
			if (to == null || !to.ready || !to.speakerEnabled) continue;

			if (speakerIsSpectator && !c.spectatorsHeardByEveryone && !listener.isSpectator()) continue;

			double dx = listener.getX() - sx;
			double dy = listener.getEyeY() - sy;
			double dz = listener.getZ() - sz;
			if (dx * dx + dy * dy + dz * dz > maxSq) continue;

			if (out == null) {
				out = new SpeakerAudioPayload(speaker.getUUID(), (float) sx, (float) sy, (float) sz, data);
			}
			ServerPlayNetworking.send(listener, out);
		}
	}

	static Component colored(String text) {
		return Component.literal(text.replace('&', '\u00a7'));
	}
}
