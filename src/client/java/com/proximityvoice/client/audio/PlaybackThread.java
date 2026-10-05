package com.proximityvoice.client.audio;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import javax.sound.sampled.SourceDataLine;

import com.proximityvoice.ProximityVoice;
import com.proximityvoice.client.ClientConfig;
import com.proximityvoice.client.ProximityVoiceClient;
import com.proximityvoice.client.VoiceSession;
import com.proximityvoice.network.SpeakerAudioPayload;

/**
 * Mixes every nearby talking player into one stereo 48 kHz output, 20 ms at a time.
 * Volume fades with distance and voices are panned left/right based on where you look.
 */
public final class PlaybackThread extends Thread {
	private static final int OUT_FRAMES = 960;                    // 20 ms @ 48 kHz
	private static final int LINE_BUFFER_BYTES = OUT_FRAMES * 4 * 4; // ~80 ms output buffer
	private static final long STREAM_TIMEOUT_MS = 3000;

	private final Map<UUID, SpeakerStream> streams = new ConcurrentHashMap<>();
	private volatile boolean running = true;
	private volatile SourceDataLine line;

	public PlaybackThread() {
		super("ProximityVoice-Playback");
		this.setDaemon(true);
	}

	public void enqueue(SpeakerAudioPayload packet) {
		this.streams.computeIfAbsent(packet.sender(), uuid -> new SpeakerStream())
				.push(new SpeakerStream.Frame(packet.data(), packet.x(), packet.y(), packet.z()));
	}

	public void clear() {
		this.streams.clear();
	}

	public void shutdown() {
		this.running = false;
		this.interrupt();
		SourceDataLine l = this.line;
		this.line = null;
		if (l != null) {
			try {
				l.stop();
				l.close();
			} catch (Exception ignored) {
			}
		}
	}

	@Override
	public void run() {
		float[] mixLeft = new float[OUT_FRAMES];
		float[] mixRight = new float[OUT_FRAMES];
		byte[] out = new byte[OUT_FRAMES * 4];
		short[] pcm = new short[2048];

		while (this.running) {
			SourceDataLine l = this.line;
			if (l == null) {
				l = this.openLine();
				if (l == null) {
					sleepQuietly(3000);
					continue;
				}
			}

			ClientConfig cfg = ProximityVoiceClient.CONFIG;
			Arrays.fill(mixLeft, 0f);
			Arrays.fill(mixRight, 0f);

			if (!cfg.speakerEnabled) {
				this.streams.clear();
			}
			float master = cfg.speakerVolume / 100f;
			long now = System.currentTimeMillis();

			Iterator<SpeakerStream> it = this.streams.values().iterator();
			while (it.hasNext()) {
				SpeakerStream stream = it.next();
				if (now - stream.lastReceived > STREAM_TIMEOUT_MS) {
					it.remove();
					continue;
				}
				SpeakerStream.Frame frame = stream.poll();
				if (frame == null) continue;

				int samples = AdpcmCodec.decode(frame.data(), pcm);
				if (samples < 2) continue;

				float[] gains = spatialGains(frame.x(), frame.y(), frame.z());
				mixInto(stream, pcm, samples, gains[0] * master, gains[1] * master, mixLeft, mixRight);
			}

			for (int i = 0; i < OUT_FRAMES; i++) {
				int left = (int) Math.clamp(mixLeft[i], -32768f, 32767f);
				int right = (int) Math.clamp(mixRight[i], -32768f, 32767f);
				int o = i * 4;
				out[o] = (byte) left;
				out[o + 1] = (byte) (left >> 8);
				out[o + 2] = (byte) right;
				out[o + 3] = (byte) (right >> 8);
			}

			try {
				l.write(out, 0, out.length); // blocks while the buffer is full -> paces this loop in real time
			} catch (Exception e) {
				this.line = null;
			}
		}
	}

	/** Linear-interpolation upsample (16 kHz -> 48 kHz) + smooth gain change across the frame. */
	private static void mixInto(SpeakerStream s, short[] pcm, int samples, float targetLeft, float targetRight, float[] mixLeft, float[] mixRight) {
		if (s.gainLeft < 0f) {
			s.gainLeft = targetLeft;
			s.gainRight = targetRight;
		}
		float startLeft = s.gainLeft;
		float startRight = s.gainRight;
		float ratio = (float) samples / OUT_FRAMES;

		for (int i = 0; i < OUT_FRAMES; i++) {
			float pos = (i + 1) * ratio - 1f; // -1 .. samples-1 (position -1 = last sample of previous frame)
			int idx = (int) Math.floor(pos);
			float frac = pos - idx;
			float a = idx < 0 ? s.lastSample : pcm[idx];
			float b = pcm[Math.min(idx + 1, samples - 1)];
			float v = a + (b - a) * frac;

			float t = (float) i / OUT_FRAMES;
			mixLeft[i] += v * (startLeft + (targetLeft - startLeft) * t);
			mixRight[i] += v * (startRight + (targetRight - startRight) * t);
		}
		s.lastSample = pcm[samples - 1];
		s.gainLeft = targetLeft;
		s.gainRight = targetRight;
	}

	/** Distance fade + left/right pan relative to where the local player is looking. */
	private static float[] spatialGains(float sx, float sy, float sz) {
		double dx = sx - VoiceSession.listenerX;
		double dy = sy - VoiceSession.listenerY;
		double dz = sz - VoiceSession.listenerZ;
		double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

		double max = VoiceSession.maxDistance;
		double fadeStart = Math.min(VoiceSession.fadeStartDistance, max - 0.01);
		float volume;
		if (distance <= fadeStart) {
			volume = 1f;
		} else if (distance >= max) {
			volume = 0f;
		} else {
			double t = 1.0 - (distance - fadeStart) / (max - fadeStart);
			volume = (float) (t * t); // quadratic fade sounds more natural than linear
		}

		// Minecraft yaw: 0 = facing south (+Z). Right-hand direction = (-cos yaw, -sin yaw).
		double yaw = Math.toRadians(VoiceSession.listenerYaw);
		double rightX = -Math.cos(yaw);
		double rightZ = -Math.sin(yaw);
		double horizontal = Math.sqrt(dx * dx + dz * dz);
		float pan = 0f;
		if (horizontal > 0.001) {
			pan = (float) ((dx * rightX + dz * rightZ) / horizontal); // -1 = left, +1 = right
			pan *= (float) Math.min(1.0, horizontal / 2.0);         // very close = centred
		}

		float left = volume * (pan > 0 ? 1f - 0.65f * pan : 1f);
		float right = volume * (pan < 0 ? 1f + 0.65f * pan : 1f);
		return new float[] {left, right};
	}

	private SourceDataLine openLine() {
		String device = ProximityVoiceClient.CONFIG.outputDevice;
		try {
			SourceDataLine l = AudioDevices.openSpeaker(device, LINE_BUFFER_BYTES);
			this.line = l;
			ProximityVoice.LOGGER.info("Voice output opened: {}", device.isEmpty() ? "system default" : device);
			return l;
		} catch (Exception e) {
			ProximityVoice.LOGGER.warn("Could not open voice output '{}': {}", device, e.toString());
			return null;
		}
	}

	private static void sleepQuietly(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
		}
	}
}
