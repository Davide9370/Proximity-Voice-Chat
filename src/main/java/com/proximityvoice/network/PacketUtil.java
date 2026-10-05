package com.proximityvoice.network;

import io.netty.buffer.ByteBuf;

/** Small helpers that only use plain Netty ByteBuf methods (stable across MC versions). */
final class PacketUtil {
	private PacketUtil() {
	}

	static void writeBytes(ByteBuf buf, byte[] data) {
		buf.writeShort(data.length);
		buf.writeBytes(data);
	}

	static byte[] readBytes(ByteBuf buf, int maxLength) {
		int length = buf.readUnsignedShort();
		if (length > maxLength) {
			throw new IllegalStateException("Voice packet too large: " + length + " > " + maxLength);
		}
		byte[] data = new byte[length];
		buf.readBytes(data);
		return data;
	}
}
