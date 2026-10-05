package com.proximityvoice.client.audio;

import javax.sound.sampled.TargetDataLine;

import com.proximityvoice.ProximityVoice;
import com.proximityvoice.client.ClientConfig;
import com.proximityvoice.client.ProximityVoiceClient;
import com.proximityvoice.client.VoiceSession;

/**
 * Reads the microphone in 20 ms chunks, converts 48 kHz -> 16 kHz, applies volume,
 * decides whether to transmit (always-on with noise gate, or push-to-talk),
 * encodes with ADPCM and sends it through the normal game connection.
 */
public final class MicrophoneThread extends Thread {
	public static final int SAMPLES_16K = 320;               // 20 ms @ 16 kHz
	private static final int SAMPLES_48K = SAMPLES_16K * 3;  // 20 ms @ 48 kHz
	private static final int HANG_FRAMES = 20;               // keep sending 400 ms after you stop talking

	/** For the settings screen meter + HUD (written by this thread, read by the render thread). */
	public static volatile float levelDb = -100f;
	public static volatile boolean transmitting = false;

	private volatile boolean running = true;
	private volatile TargetDataLine line;
	private final AdpcmCodec.Encoder encoder = new AdpcmCodec.Encoder();
	private float dcLastIn;
	private float dcLastOut;
	private int hang;

	public MicrophoneThread() {
		super("ProximityVoice-Microphone");
		this.setDaemon(true);
	}

	public void shutdown() {
		this.running = false;
		this.interrupt();
		this.closeLine();
	}

	@Override
	public void run() {
		byte[] raw = new byte[SAMPLES_48K * 2];
		short[] pcm = new short[SAMPLES_16K];

		while (this.running) {
			ClientConfig cfg = ProximityVoiceClient.CONFIG;

			if (!cfg.micEnabled || !VoiceSession.isActive()) {
				// mic switched off: release the device so the OS mic indicator turns off too
				this.closeLine();
				levelDb = -100f;
				transmitting = false;
				sleepQuietly(100);
				continue;
			}

			if (this.line == null && !this.openLine(cfg)) {
				sleepQuietly(3000); // retry later (device busy / unplugged / no permission)
				continue;
			}

			if (!this.readFully(raw)) continue;

			float db = this.process(raw, pcm, cfg.micVolume / 100f);
			levelDb = db;

			boolean send;
			if (cfg.pushToTalk) {
				send = VoiceSession.isPushToTalkHeld();
				this.hang = 0;
			} else if (cfg.noiseGateDb <= ClientConfig.GATE_OFF) {
				send = true;
			} else {
				if (db >= cfg.noiseGateDb) this.hang = HANG_FRAMES;
				else if (this.hang > 0) this.hang--;
				send = this.hang > 0;
			}

			transmitting = send;
			if (send) {
				VoiceSession.sendMicFrame(this.encoder.encode(pcm, SAMPLES_16K));
			}
		}

		this.closeLine();
		transmitting = false;
		levelDb = -100f;
	}

	/** 48 kHz little-endian 16-bit -> 16 kHz, DC removal, gain. Returns the frame level in dBFS. */
	private float process(byte[] raw, short[] pcm, float gain) {
		double sumSquares = 0;
		for (int i = 0; i < SAMPLES_16K; i++) {
			int base = i * 6;
			float sum = 0;
			for (int k = 0; k < 3; k++) {
				int idx = base + k * 2;
				sum += (short) ((raw[idx] & 0xFF) | (raw[idx + 1] << 8));
			}
			float x = sum / 3f; // average of 3 samples = cheap low-pass + decimation

			// DC blocker (some mics have an offset that would break the noise gate)
			float y = x - this.dcLastIn + 0.995f * this.dcLastOut;
			this.dcLastIn = x;
			this.dcLastOut = y;

			float v = Math.clamp(y * gain, -32768f, 32767f);
			pcm[i] = (short) v;
			sumSquares += v * v;
		}
		double rms = Math.sqrt(sumSquares / SAMPLES_16K);
		return rms < 1.0 ? -100f : (float) (20.0 * Math.log10(rms / 32768.0));
	}

	private boolean readFully(byte[] buffer) {
		TargetDataLine l = this.line;
		if (l == null) return false;
		int read = 0;
		try {
			while (read < buffer.length && this.running) {
				int n = l.read(buffer, read, buffer.length - read);
				if (n <= 0) return false; // line closed
				read += n;
			}
		} catch (Exception e) {
			this.closeLine();
			return false;
		}
		return read == buffer.length;
	}

	private boolean openLine(ClientConfig cfg) {
		try {
			this.line = AudioDevices.openMicrophone(cfg.inputDevice, SAMPLES_48K * 2 * 6);
			ProximityVoice.LOGGER.info("Microphone opened: {}", cfg.inputDevice.isEmpty() ? "system default" : cfg.inputDevice);
			return true;
		} catch (Exception e) {
			ProximityVoice.LOGGER.warn("Could not open microphone '{}': {}", cfg.inputDevice, e.toString());
			this.line = null;
			return false;
		}
	}

	private void closeLine() {
		TargetDataLine l = this.line;
		this.line = null;
		if (l != null) {
			try {
				l.stop();
				l.close();
			} catch (Exception ignored) {
			}
		}
	}

	private static void sleepQuietly(long ms) {
		try {
			Thread.sleep(ms);
		} catch (InterruptedException ignored) {
		}
	}
}
