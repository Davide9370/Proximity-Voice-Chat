package com.proximityvoice.client.audio;

/**
 * IMA-ADPCM: 16-bit PCM -> 4 bits per sample. Pure Java, no native libraries.
 *
 * Frame layout: [predictor hi][predictor lo][step index][packed nibbles, low nibble first]
 * 320 samples (20 ms @ 16 kHz) -> 3 + 160 = 163 bytes.
 *
 * The encoder keeps its state between frames (so quality stays good), but writes that state
 * into each frame header, so every frame can be decoded on its own (lost/dropped frames are fine).
 */
public final class AdpcmCodec {
	private static final int[] INDEX_TABLE = {
			-1, -1, -1, -1, 2, 4, 6, 8,
			-1, -1, -1, -1, 2, 4, 6, 8
	};

	private static final int[] STEP_TABLE = {
			7, 8, 9, 10, 11, 12, 13, 14, 16, 17,
			19, 21, 23, 25, 28, 31, 34, 37, 41, 45,
			50, 55, 60, 66, 73, 80, 88, 97, 107, 118,
			130, 143, 157, 173, 190, 209, 230, 253, 279, 307,
			337, 371, 408, 449, 494, 544, 598, 658, 724, 796,
			876, 963, 1060, 1166, 1282, 1411, 1552, 1707, 1878, 2066,
			2272, 2499, 2749, 3024, 3327, 3660, 4026, 4428, 4871, 5358,
			5894, 6484, 7132, 7845, 8630, 9493, 10442, 11487, 12635, 13899,
			15289, 16818, 18500, 20350, 22385, 24623, 27086, 29794, 32767
	};

	public static final int HEADER_SIZE = 3;

	private AdpcmCodec() {
	}

	/** Stateful encoder: one per microphone stream. */
	public static final class Encoder {
		private int predictor;
		private int index;

		public byte[] encode(short[] pcm, int count) {
			int samples = count & ~1; // even number of samples
			byte[] out = new byte[HEADER_SIZE + samples / 2];
			out[0] = (byte) (this.predictor >> 8);
			out[1] = (byte) this.predictor;
			out[2] = (byte) this.index;

			for (int i = 0; i < samples; i += 2) {
				int lo = encodeSample(pcm[i]);
				int hi = encodeSample(pcm[i + 1]);
				out[HEADER_SIZE + i / 2] = (byte) (lo | (hi << 4));
			}
			return out;
		}

		private int encodeSample(int sample) {
			int step = STEP_TABLE[this.index];
			int diff = sample - this.predictor;
			int code = 0;
			if (diff < 0) {
				code = 8;
				diff = -diff;
			}

			int delta = step >> 3;
			if (diff >= step) {
				code |= 4;
				diff -= step;
				delta += step;
			}
			step >>= 1;
			if (diff >= step) {
				code |= 2;
				diff -= step;
				delta += step;
			}
			step >>= 1;
			if (diff >= step) {
				code |= 1;
				delta += step;
			}

			this.predictor += (code & 8) != 0 ? -delta : delta;
			this.predictor = Math.clamp(this.predictor, Short.MIN_VALUE, Short.MAX_VALUE);
			this.index = Math.clamp(this.index + INDEX_TABLE[code], 0, STEP_TABLE.length - 1);
			return code;
		}
	}

	/**
	 * Decodes one frame into {@code out}. Returns the number of samples written (0 if the frame is invalid).
	 * Safe against garbage input: everything is clamped.
	 */
	public static int decode(byte[] frame, short[] out) {
		if (frame.length <= HEADER_SIZE) return 0;
		int predictor = (short) (((frame[0] & 0xFF) << 8) | (frame[1] & 0xFF));
		int index = Math.clamp(frame[2] & 0xFF, 0, STEP_TABLE.length - 1);

		int samples = Math.min((frame.length - HEADER_SIZE) * 2, out.length);
		for (int i = 0; i < samples; i++) {
			int b = frame[HEADER_SIZE + i / 2] & 0xFF;
			int code = (i & 1) == 0 ? (b & 0x0F) : (b >> 4);

			int step = STEP_TABLE[index];
			int delta = step >> 3;
			if ((code & 4) != 0) delta += step;
			if ((code & 2) != 0) delta += step >> 1;
			if ((code & 1) != 0) delta += step >> 2;

			predictor += (code & 8) != 0 ? -delta : delta;
			predictor = Math.clamp(predictor, Short.MIN_VALUE, Short.MAX_VALUE);
			index = Math.clamp(index + INDEX_TABLE[code], 0, STEP_TABLE.length - 1);
			out[i] = (short) predictor;
		}
		return samples;
	}
}
