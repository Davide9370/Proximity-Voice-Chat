package com.proximityvoice.server;

import static net.minecraft.commands.Commands.literal;

import java.util.ArrayList;
import java.util.List;

import com.mojang.brigadier.CommandDispatcher;
import com.proximityvoice.ProximityVoice;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * /voice status  - who has voice chat
 * /voice reload  - reload config/proximityvoice/server.properties (ops / permission "proximityvoice:command.reload")
 */
final class VoiceCommands {
	private VoiceCommands() {
	}

	static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(literal("voice")
				.then(literal("status").executes(ctx -> {
					CommandSourceStack source = ctx.getSource();
					List<String> withVoice = new ArrayList<>();
					List<String> without = new ArrayList<>();
					for (ServerPlayer player : source.getServer().getPlayerList().getPlayers()) {
						(VoiceServer.hasVoice(player) ? withVoice : without).add(player.getName().getString());
					}
					source.sendSuccess(() -> VoiceServer.colored("&aProximity Voice &7(" + (VoiceServer.config().enabled ? "&aenabled" : "&cdisabled")
							+ "&7, range " + VoiceServer.config().maxDistance + " blocks)"), false);
					source.sendSuccess(() -> VoiceServer.colored("&7With voice (" + withVoice.size() + "): &f" + String.join(", ", withVoice)), false);
					source.sendSuccess(() -> VoiceServer.colored("&7Without (" + without.size() + "): &8" + String.join(", ", without)), false);
					return 1;
				}))
				.then(literal("reload")
						.requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
						.executes(ctx -> {
							VoiceServer.reload(ctx.getSource().getServer());
							ctx.getSource().sendSuccess(() -> Component.literal("Proximity Voice config reloaded."), true);
							return 1;
						})));
	}
}
