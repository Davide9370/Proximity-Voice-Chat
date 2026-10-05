package com.proximityvoice.server;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Detects Bedrock players without a hard dependency on Geyser/Floodgate.
 * Works with Floodgate, Geyser-Fabric, or a standalone Geyser proxy + Floodgate.
 */
final class GeyserCompat {
	private GeyserCompat() {
	}

	static boolean isBedrockPlayer(UUID uuid) {
		// 1) Floodgate API (most common Geyser setup)
		try {
			Class<?> api = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
			Object instance = api.getMethod("getInstance").invoke(null);
			Method check = api.getMethod("isFloodgatePlayer", UUID.class);
			return (boolean) check.invoke(instance, uuid);
		} catch (Throwable ignored) {
			// Floodgate not installed
		}

		// 2) Geyser API (Geyser running as a mod/plugin on this server)
		try {
			Class<?> api = Class.forName("org.geysermc.geyser.api.GeyserApi");
			Object instance = api.getMethod("api").invoke(null);
			Method check = api.getMethod("isBedrockPlayer", UUID.class);
			return (boolean) check.invoke(instance, uuid);
		} catch (Throwable ignored) {
			// Geyser not installed here
		}

		// 3) Fallback: Floodgate UUIDs look like 00000000-0000-0000-xxxx-xxxxxxxxxxxx
		return uuid.getMostSignificantBits() == 0L;
	}
}
