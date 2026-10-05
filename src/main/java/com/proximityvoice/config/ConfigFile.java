package com.proximityvoice.config;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

import com.proximityvoice.ProximityVoice;

/**
 * Tiny helper for human-editable .properties config files.
 * Reading uses java.util.Properties; writing is done by hand so every option keeps its comment.
 */
public final class ConfigFile {
	private final Properties props = new Properties();
	private final StringBuilder out = new StringBuilder();

	private ConfigFile() {
	}

	public static ConfigFile read(Path path) {
		ConfigFile file = new ConfigFile();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				file.props.load(reader);
			} catch (IOException | IllegalArgumentException e) {
				ProximityVoice.LOGGER.warn("Could not read {} - using defaults for broken values", path, e);
			}
		}
		return file;
	}

	public static ConfigFile writer() {
		return new ConfigFile();
	}

	// ---------- reading ----------

	public String getString(String key, String def) {
		String value = this.props.getProperty(key);
		return value == null ? def : value.trim();
	}

	public boolean getBool(String key, boolean def) {
		String value = this.props.getProperty(key);
		if (value == null) return def;
		value = value.trim().toLowerCase();
		if (value.equals("true") || value.equals("on") || value.equals("yes")) return true;
		if (value.equals("false") || value.equals("off") || value.equals("no")) return false;
		return def;
	}

	public int getInt(String key, int def, int min, int max) {
		try {
			return Math.clamp(Integer.parseInt(this.props.getProperty(key, "").trim()), min, max);
		} catch (NumberFormatException e) {
			return def;
		}
	}

	public double getDouble(String key, double def, double min, double max) {
		try {
			return Math.clamp(Double.parseDouble(this.props.getProperty(key, "").trim()), min, max);
		} catch (NumberFormatException e) {
			return def;
		}
	}

	// ---------- writing ----------

	public ConfigFile header(String text) {
		for (String line : text.split("\n")) {
			this.out.append("# ").append(line).append('\n');
		}
		this.out.append('\n');
		return this;
	}

	public ConfigFile put(String key, Object value, String comment) {
		for (String line : comment.split("\n")) {
			this.out.append("# ").append(line).append('\n');
		}
		this.out.append(key).append('=').append(escape(String.valueOf(value))).append("\n\n");
		return this;
	}

	public void save(Path path) {
		try {
			Files.createDirectories(path.getParent());
			Files.writeString(path, this.out.toString(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			ProximityVoice.LOGGER.error("Could not save {}", path, e);
		}
	}

	private static String escape(String value) {
		StringBuilder sb = new StringBuilder(value.length());
		for (int i = 0; i < value.length(); i++) {
			char c = value.charAt(i);
			switch (c) {
				case '\\' -> sb.append("\\\\");
				case '\n' -> sb.append("\\n");
				case '\r' -> sb.append("\\r");
				case '\t' -> sb.append("\\t");
				case ' ' -> sb.append(i == 0 ? "\\ " : " ");
				default -> sb.append(c);
			}
		}
		return sb.toString();
	}
}
