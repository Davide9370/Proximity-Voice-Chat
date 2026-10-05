package com.proximityvoice.client;

import org.lwjgl.glfw.GLFW;

import com.proximityvoice.ProximityVoice;
import com.proximityvoice.client.audio.MicrophoneThread;
import com.proximityvoice.client.gui.VoiceSettingsScreen;
import com.proximityvoice.network.ServerSettingsPayload;
import com.proximityvoice.network.SpeakerAudioPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;

public class ProximityVoiceClient implements ClientModInitializer {
	public static final String PTT_KEY_NAME = "key.proximityvoice.push_to_talk";

	public static ClientConfig CONFIG = new ClientConfig();
	public static KeyMapping SETTINGS_KEY;
	public static KeyMapping PUSH_TO_TALK_KEY;

	@Override
	public void onInitializeClient() {
		CONFIG = ClientConfig.load();

		KeyMapping.Category category = KeyMapping.Category.register(ProximityVoice.id("main"));
		SETTINGS_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping("key.proximityvoice.settings", GLFW.GLFW_KEY_J, category));
		PUSH_TO_TALK_KEY = KeyBindingHelper.registerKeyBinding(new KeyMapping(PTT_KEY_NAME, GLFW.GLFW_KEY_V, category));

		ClientPlayNetworking.registerGlobalReceiver(ServerSettingsPayload.TYPE, (payload, context) -> VoiceSession.onServerSettings(payload));
		ClientPlayNetworking.registerGlobalReceiver(SpeakerAudioPayload.TYPE, (payload, context) -> VoiceSession.onSpeakerAudio(payload));

		ClientPlayConnectionEvents.JOIN.register((listener, sender, client) -> VoiceSession.onJoinedWorld());
		ClientPlayConnectionEvents.DISCONNECT.register((listener, client) -> VoiceSession.onDisconnect());

		ClientTickEvents.END_CLIENT_TICK.register(ProximityVoiceClient::tick);

		HudElementRegistry.addLast(ProximityVoice.id("voice_indicator"), (graphics, deltaTracker) -> renderHud(graphics));
	}

	private static void tick(Minecraft client) {
		while (SETTINGS_KEY.consumeClick()) {
			client.setScreen(new VoiceSettingsScreen(null));
		}

		VoiceSession.setPushToTalkHeld(PUSH_TO_TALK_KEY.isDown());

		LocalPlayer player = client.player;
		if (player != null) {
			VoiceSession.updateListener(player.getX(), player.getEyeY(), player.getZ(), player.getYRot());
		}

		if (client.screen instanceof VoiceSettingsScreen screen) {
			screen.refreshLiveInfo();
		}
	}

	/** Small status text next to the hotbar: "Talking", "Mic off", "Speaker off". */
	private static void renderHud(GuiGraphics graphics) {
		if (!VoiceSession.isActive()) return;
		Minecraft mc = Minecraft.getInstance();
		if (mc.player == null) return;

		String text;
		int color;
		if (!CONFIG.micEnabled) {
			text = "\u25CF Mic off";
			color = 0xFFFF5555;
		} else if (MicrophoneThread.transmitting) {
			text = "\u25CF Talking";
			color = 0xFF55FF55;
		} else if (!CONFIG.speakerEnabled) {
			text = "\u25CF Speaker off";
			color = 0xFFFFAA00;
		} else {
			return;
		}
		if (CONFIG.micEnabled && !CONFIG.speakerEnabled && MicrophoneThread.transmitting) {
			text += " (speaker off)";
		}

		int x = graphics.guiWidth() / 2 + 98;
		int y = graphics.guiHeight() - 14;
		graphics.drawString(mc.font, text, x, y, color);
	}
}
