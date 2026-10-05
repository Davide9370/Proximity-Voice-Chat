package com.proximityvoice.config;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;

/** config/proximityvoice/server.properties */
public final class ServerConfig {
	public boolean enabled = true;
	public double maxDistance = 48.0;
	public double fadeStartDistance = 8.0;
	public boolean spectatorsHeardByEveryone = false;
	public int maxPacketsPerSecond = 75;
	public boolean notifyPlayersWithoutMod = true;
	public String noModMessage = "&7This server has &aproximity voice chat&7! Install the &fProximity Voice&7 Fabric mod to talk and listen (press &fJ&7 for settings).";
	public boolean notifyBedrockPlayers = true;
	public String bedrockMessage = "&7This server has proximity voice chat, but it only works for Java Edition players with the mod. Bedrock can't send or play custom voice audio.";

	public static Path path() {
		return FabricLoader.getInstance().getConfigDir().resolve("proximityvoice").resolve("server.properties");
	}

	public static ServerConfig load() {
		ConfigFile in = ConfigFile.read(path());
		ServerConfig c = new ServerConfig();
		c.enabled = in.getBool("enabled", c.enabled);
		c.maxDistance = in.getDouble("max-distance", c.maxDistance, 1.0, 512.0);
		c.fadeStartDistance = in.getDouble("fade-start-distance", c.fadeStartDistance, 0.0, c.maxDistance);
		c.spectatorsHeardByEveryone = in.getBool("spectators-heard-by-everyone", c.spectatorsHeardByEveryone);
		c.maxPacketsPerSecond = in.getInt("max-packets-per-second", c.maxPacketsPerSecond, 10, 500);
		c.notifyPlayersWithoutMod = in.getBool("notify-players-without-mod", c.notifyPlayersWithoutMod);
		c.noModMessage = in.getString("no-mod-message", c.noModMessage);
		c.notifyBedrockPlayers = in.getBool("notify-bedrock-players", c.notifyBedrockPlayers);
		c.bedrockMessage = in.getString("bedrock-message", c.bedrockMessage);
		c.save(); // writes any missing options back with their comments
		return c;
	}

	public void save() {
		ConfigFile.writer()
				.header("""
						Proximity Voice - server config
						Voice audio travels inside the normal Minecraft connection, so there is NO extra port to open.
						Use /voice reload after editing this file.""")
				.put("enabled", this.enabled, "Turn voice chat on/off for the whole server.")
				.put("max-distance", this.maxDistance, "How far (in blocks) a player's voice can be heard. Players further away get nothing sent to them.")
				.put("fade-start-distance", this.fadeStartDistance, "Voices are full volume up to this distance, then fade out until max-distance.")
				.put("spectators-heard-by-everyone", this.spectatorsHeardByEveryone, "If false, players in spectator mode can only be heard by other spectators.")
				.put("max-packets-per-second", this.maxPacketsPerSecond, "Anti-spam limit for voice packets per player (a talking player sends 50 per second).")
				.put("notify-players-without-mod", this.notifyPlayersWithoutMod, "Tell Java players who join without the mod that voice chat exists.")
				.put("no-mod-message", this.noModMessage, "Message for Java players without the mod. Use & color codes.")
				.put("notify-bedrock-players", this.notifyBedrockPlayers, "Tell Bedrock (Geyser/Floodgate) players that voice is Java-only.")
				.put("bedrock-message", this.bedrockMessage, "Message for Bedrock players. Use & color codes.")
				.save(path());
	}
}
