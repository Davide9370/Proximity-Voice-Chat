package com.proximityvoice.client;

import com.proximityvoice.ProximityVoice;
import com.proximityvoice.client.audio.MicrophoneThread;
import com.proximityvoice.client.audio.PlaybackThread;
import com.proximityvoice.network.ClientStatePayload;
import com.proximityvoice.network.MicAudioPayload;
import com.proximityvoice.network.ServerSettingsPayload;
import com.proximityvoice.network.SpeakerAudioPayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/**
 * Client-side state for the current server connection.
 * Voice only starts when the server tells us it has Proximity Voice installed.
 */
public final class VoiceSession {
	public enum Status { NOT_CONNECTED, SERVER_HAS_NO_MOD, DISABLED_BY_SERVER, VERSION_MISMATCH, CONNECTED }

	private static volatile boolean active;
	private static volatile Status status = Status.NOT_CONNECTED;
	private static volatile boolean pushToTalkHeld;

	public static volatile float maxDistance = 48f;
	public static volatile float fadeStartDistance = 8f;

	// local player's ears, updated every client tick
	public static volatile double listenerX;
	public static volatile double listenerY;
	public static volatile double listenerZ;
	public static volatile float listenerYaw;

	private static MicrophoneThread microphone;
	private static PlaybackThread playback;

	private VoiceSession() {
	}

	public static boolean isActive() {
		return active;
	}

	public static Status status() {
		return status;
	}

	public static boolean isPushToTalkHeld() {
		return pushToTalkHeld;
	}

	static void setPushToTalkHeld(boolean held) {
		pushToTalkHeld = held;
	}

	static void updateListener(double x, double y, double z, float yaw) {
		listenerX = x;
		listenerY = y;
		listenerZ = z;
		listenerYaw = yaw;
	}

	/** Render thread: the world finished loading. If the server never sends settings, it doesn't have the mod. */
	static void onJoinedWorld() {
		if (!active && !ClientPlayNetworking.canSend(ClientStatePayload.TYPE)) {
			status = Status.SERVER_HAS_NO_MOD;
		}
	}

	/** Render thread: the server has the mod and told us its settings. */
	static void onServerSettings(ServerSettingsPayload settings) {
		if (settings.protocol() != ProximityVoice.PROTOCOL_VERSION) {
			status = Status.VERSION_MISMATCH;
			stopAudio();
			return;
		}

		maxDistance = Math.max(1f, settings.maxDistance());
		fadeStartDistance = Math.max(0f, settings.fadeStartDistance());

		if (!settings.enabled()) {
			status = Status.DISABLED_BY_SERVER;
			stopAudio();
			sendState();
			return;
		}

		status = Status.CONNECTED;
		if (!active) {
			active = true;
			startAudio();
		}
		sendState();
	}

	/** Render thread. */
	static void onSpeakerAudio(SpeakerAudioPayload packet) {
		PlaybackThread p = playback;
		if (active && p != null && ProximityVoiceClient.CONFIG.speakerEnabled) {
			p.enqueue(packet);
		}
	}

	/** Render thread: left the server. */
	static void onDisconnect() {
		status = Status.NOT_CONNECTED;
		stopAudio();
	}

	/** Called from the microphone thread. */
	public static void sendMicFrame(byte[] frame) {
		if (!active) return;
		try {
			ClientPlayNetworking.send(new MicAudioPayload(frame));
		} catch (Exception ignored) {
			// connection closing, ignore
		}
	}

	/** Tell the server our mic/speaker switches (it stops sending audio if our speaker is off). */
	public static void sendState() {
		try {
			if (ClientPlayNetworking.canSend(ClientStatePayload.TYPE)) {
				ClientConfig cfg = ProximityVoiceClient.CONFIG;
				ClientPlayNetworking.send(new ClientStatePayload(ProximityVoice.PROTOCOL_VERSION, cfg.micEnabled, cfg.speakerEnabled));
			}
		} catch (Exception ignored) {
			// not in game
		}
	}

	/** After changing input/output device. */
	public static synchronized void restartAudio() {
		if (!active) return;
		stopThreads();
		startThreads();
	}

	public static void clearIncoming() {
		PlaybackThread p = playback;
		if (p != null) p.clear();
	}

	private static synchronized void startAudio() {
		stopThreads();
		startThreads();
	}

	private static synchronized void stopAudio() {
		active = false;
		stopThreads();
	}

	private static void startThreads() {
		microphone = new MicrophoneThread();
		playback = new PlaybackThread();
		microphone.start();
		playback.start();
	}

	private static void stopThreads() {
		if (microphone != null) microphone.shutdown();
		if (playback != null) playback.shutdown();
		microphone = null;
		playback = null;
	}
}
