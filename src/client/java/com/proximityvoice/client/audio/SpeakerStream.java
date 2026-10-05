package com.proximityvoice.client.audio;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Small jitter buffer for one talking player.
 * Packets arrive in bursts (they come through the game connection and are handled once per frame),
 * so we collect a few frames before starting playback and drop old ones if we fall behind.
 */
final class SpeakerStream {
	private static final int PREFILL_FRAMES = 3;       // 60 ms
	private static final long PREFILL_TIMEOUT_MS = 80; // start anyway if a short burst never fills the buffer
	private static final int MAX_FRAMES = 10;          // 200 ms -> drop back down to PREFILL to keep latency low

	record Frame(byte[] data, float x, float y, float z) {
	}

	private final ConcurrentLinkedQueue<Frame> queue = new ConcurrentLinkedQueue<>();
	private final AtomicInteger size = new AtomicInteger();
	volatile long lastReceived = System.currentTimeMillis();
	private volatile long firstQueuedAt;

	// only touched by the playback thread
	private boolean buffering = true;
	float lastSample;
	float gainLeft = -1f;
	float gainRight = -1f;

	void push(Frame frame) {
		long now = System.currentTimeMillis();
		if (this.size.getAndIncrement() == 0) this.firstQueuedAt = now;
		this.queue.add(frame);
		this.lastReceived = now;
	}

	Frame poll() {
		if (this.buffering) {
			int queued = this.size.get();
			boolean timedOut = queued > 0 && System.currentTimeMillis() - this.firstQueuedAt > PREFILL_TIMEOUT_MS;
			if (queued < PREFILL_FRAMES && !timedOut) return null;
			this.buffering = false;
		}

		if (this.size.get() > MAX_FRAMES) {
			while (this.size.get() > PREFILL_FRAMES && this.queue.poll() != null) {
				this.size.decrementAndGet();
			}
		}

		Frame frame = this.queue.poll();
		if (frame == null) {
			// ran dry: go back to buffering, reset smoothing so the next burst starts clean
			this.buffering = true;
			this.lastSample = 0f;
			this.gainLeft = -1f;
			this.gainRight = -1f;
			return null;
		}
		this.size.decrementAndGet();
		return frame;
	}
}
