package com.proximityvoice.client;

import java.nio.file.Path;

import com.proximityvoice.config.ConfigFile;
import net.fabricmc.loader.api.FabricLoader;

/** config/proximityvoice/client.properties  (everything here can also be changed in-game with J) */
public final class ClientConfig {
	public static final int GATE_OFF = -80;

	public volatile boolean micEnabled = true;
	public volatile boolean speakerEnabled = true;
	/** false = always on (default), true = push to talk */
	public volatile boolean pushToTalk = false;
	public volatile int micVolume = 100;      // %
	public volatile int speakerVolume = 100;  // %
	public volatile int noiseGateDb = -50;    // GATE_OFF = always transmit
	public volatile String inputDevice = "";  // "" = system default
	public volatile String outputDevice = ""; // "" = system default

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("proximityvoice").resolve("client.properties");
	}

	public static ClientConfig load() {
		ConfigFile in = ConfigFile.read(path());
		ClientConfig c = new ClientConfig();
		c.micEnabled = in.getBool("microphone-enabled", c.micEnabled);
		c.speakerEnabled = in.getBool("speaker-enabled", c.speakerEnabled);
		c.pushToTalk = in.getString("activation-mode", "always-on").equalsIgnoreCase("push-to-talk");
		c.micVolume = in.getInt("microphone-volume", c.micVolume, 0, 300);
		c.speakerVolume = in.getInt("speaker-volume", c.speakerVolume, 0, 200);
		c.noiseGateDb = in.getInt("noise-gate-db", c.noiseGateDb, GATE_OFF, -10);
		c.inputDevice = in.getString("input-device", c.inputDevice);
		c.outputDevice = in.getString("output-device", c.outputDevice);
		c.save();
		return c;
	}

	public void save() {
		ConfigFile.writer()
				.header("""
						Proximity Voice - client config
						You can change all of this in-game: press J (change the key in Options > Controls > Key Binds).""")
				.put("microphone-enabled", this.micEnabled, "Send your microphone to nearby players.")
				.put("speaker-enabled", this.speakerEnabled, "Hear nearby players.")
				.put("activation-mode", this.pushToTalk ? "push-to-talk" : "always-on",
						"always-on = mic is live whenever you speak (default)\npush-to-talk = only while holding the Push to Talk key (default V)")
				.put("microphone-volume", this.micVolume, "Microphone boost in percent (0-300).")
				.put("speaker-volume", this.speakerVolume, "Volume of other players in percent (0-200).")
				.put("noise-gate-db", this.noiseGateDb,
						"Always-on mode only: sound quieter than this (dBFS) is not sent, so you don't stream silence/background hiss.\n-80 = gate off (mic sends everything). Higher = less sensitive. Range -80 to -10.")
				.put("input-device", this.inputDevice, "Microphone device name. Empty = system default.")
				.put("output-device", this.outputDevice, "Speaker/headphone device name. Empty = system default.")
				.save(path());
	}
}
