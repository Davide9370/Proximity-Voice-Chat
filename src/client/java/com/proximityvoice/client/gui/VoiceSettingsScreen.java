package com.proximityvoice.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import com.proximityvoice.client.ClientConfig;
import com.proximityvoice.client.ProximityVoiceClient;
import com.proximityvoice.client.VoiceSession;
import com.proximityvoice.client.audio.AudioDevices;
import com.proximityvoice.client.audio.MicrophoneThread;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Opened with J (rebindable in Options > Controls > Key Binds > Proximity Voice).
 *
 *   [Microphone: ON ]   [Speaker: ON   ]
 *   [Mode: Always On]   [PTT Key: V    ]
 *   [-][Mic Vol 100%][+] [-][Speaker 100%][+]
 *   [-][Gate -50 dB ][+] [Level ||||....  ]
 *   [Input: Default ]   [Output: Default]
 *   [            status line              ]
 *                 [ Done ]
 */
public class VoiceSettingsScreen extends Screen {
	private final Screen parent;

	private Button micButton;
	private Button speakerButton;
	private Button modeButton;
	private Button micVolumeLabel;
	private Button speakerVolumeLabel;
	private Button gateLabel;
	private Button meterLabel;
	private Button inputButton;
	private Button outputButton;
	private Button statusLabel;

	private List<String> inputDevices = new ArrayList<>();
	private List<String> outputDevices = new ArrayList<>();

	public VoiceSettingsScreen(Screen parent) {
		super(Component.literal("Proximity Voice"));
		this.parent = parent;
	}

	@Override
	protected void init() {
		HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this);
		layout.addTitleHeader(this.title, this.font);
		layout.addToFooter(Button.builder(Component.literal("Done"), b -> this.onClose()).width(200).build());
		layout.visitWidgets(this::addRenderableWidget);
		layout.arrangeElements();

		this.inputDevices = new ArrayList<>(List.of(""));
		this.inputDevices.addAll(AudioDevices.inputDevices());
		this.outputDevices = new ArrayList<>(List.of(""));
		this.outputDevices.addAll(AudioDevices.outputDevices());

		ClientConfig cfg = ProximityVoiceClient.CONFIG;
		int left = this.width / 2 - 155;
		int right = this.width / 2 + 5;
		int y = layout.getHeaderHeight() + 4;
		int row = 24;

		// row 1: mic / speaker on-off
		this.micButton = this.button(left, y, 150, Component.empty(), b -> {
			cfg.micEnabled = !cfg.micEnabled;
			this.changed(true);
		}, "Turn your microphone on/off. When off, the mic device is fully released.");
		this.speakerButton = this.button(right, y, 150, Component.empty(), b -> {
			cfg.speakerEnabled = !cfg.speakerEnabled;
			if (!cfg.speakerEnabled) VoiceSession.clearIncoming();
			this.changed(true);
		}, "Turn hearing other players on/off.");
		y += row;

		// row 2: mode + push-to-talk key
		this.modeButton = this.button(left, y, 150, Component.empty(), b -> {
			cfg.pushToTalk = !cfg.pushToTalk;
			this.changed(false);
		}, "Always On: your mic sends whenever you speak (louder than the noise gate).\nPush to Talk: only while holding the key.");
		Button pttKey = this.button(right, y, 150,
				Component.literal("PTT Key: ").append(Component.keybind(ProximityVoiceClient.PTT_KEY_NAME)),
				b -> { }, "Change it in Options > Controls > Key Binds > Proximity Voice.");
		pttKey.active = false;
		y += row;

		// row 3: volumes
		this.micVolumeLabel = this.stepper(left, y, b -> {
			cfg.micVolume = Math.max(0, cfg.micVolume - 10);
			this.changed(false);
		}, b -> {
			cfg.micVolume = Math.min(300, cfg.micVolume + 10);
			this.changed(false);
		}, "How loud you are to others (0-300%).");
		this.speakerVolumeLabel = this.stepper(right, y, b -> {
			cfg.speakerVolume = Math.max(0, cfg.speakerVolume - 10);
			this.changed(false);
		}, b -> {
			cfg.speakerVolume = Math.min(200, cfg.speakerVolume + 10);
			this.changed(false);
		}, "How loud other players are (0-200%).");
		y += row;

		// row 4: noise gate + live level meter
		this.gateLabel = this.stepper(left, y, b -> {
			cfg.noiseGateDb = Math.max(ClientConfig.GATE_OFF, cfg.noiseGateDb - 5);
			this.changed(false);
		}, b -> {
			cfg.noiseGateDb = Math.min(-10, cfg.noiseGateDb + 5);
			this.changed(false);
		}, "Always On mode: sounds quieter than this aren't sent.\nRaise it if keyboard/fan noise gets through, lower it if your voice gets cut.\nOFF = always send.");
		this.meterLabel = this.button(right, y, 150, Component.empty(), b -> { },
				"Your microphone level. Green = you are being sent to nearby players.");
		this.meterLabel.active = false;
		y += row;

		// row 5: devices
		this.inputButton = this.button(left, y, 150, Component.empty(), b -> {
			cfg.inputDevice = next(this.inputDevices, cfg.inputDevice);
			this.changed(false);
			VoiceSession.restartAudio();
		}, "Click to cycle through microphones.");
		this.outputButton = this.button(right, y, 150, Component.empty(), b -> {
			cfg.outputDevice = next(this.outputDevices, cfg.outputDevice);
			this.changed(false);
			VoiceSession.restartAudio();
		}, "Click to cycle through speakers/headphones.");
		y += row;

		// row 6: status
		this.statusLabel = this.button(left, y, 310, Component.empty(), b -> { }, null);
		this.statusLabel.active = false;

		this.refreshLabels();
		this.refreshLiveInfo();
	}

	/** Called every client tick while the screen is open. */
	public void refreshLiveInfo() {
		if (this.meterLabel == null) return;

		MutableComponent meter = Component.literal("Level ");
		if (!ProximityVoiceClient.CONFIG.micEnabled || !VoiceSession.isActive()) {
			meter.append(Component.literal("-").withStyle(ChatFormatting.DARK_GRAY));
		} else {
			// -60 dB .. 0 dB -> 0 .. 20 bars
			int bars = Math.clamp(Math.round((MicrophoneThread.levelDb + 60f) / 3f), 0, 20);
			ChatFormatting on = MicrophoneThread.transmitting ? ChatFormatting.GREEN : ChatFormatting.GRAY;
			meter.append(Component.literal("|".repeat(bars)).withStyle(on));
			meter.append(Component.literal("|".repeat(20 - bars)).withStyle(ChatFormatting.DARK_GRAY));
		}
		this.meterLabel.setMessage(meter);

		String status = switch (VoiceSession.status()) {
			case CONNECTED -> "\u00a7aConnected \u00a77- range " + Math.round(VoiceSession.maxDistance) + " blocks";
			case SERVER_HAS_NO_MOD -> "\u00a7cThis server doesn't have Proximity Voice";
			case DISABLED_BY_SERVER -> "\u00a76Voice chat is disabled on this server";
			case VERSION_MISMATCH -> "\u00a7cMod version doesn't match the server";
			case NOT_CONNECTED -> "\u00a77Not connected to a voice server";
		};
		this.statusLabel.setMessage(Component.literal(status));
	}

	private void refreshLabels() {
		ClientConfig cfg = ProximityVoiceClient.CONFIG;
		this.micButton.setMessage(Component.literal("Microphone: ").append(onOff(cfg.micEnabled)));
		this.speakerButton.setMessage(Component.literal("Speaker: ").append(onOff(cfg.speakerEnabled)));
		this.modeButton.setMessage(Component.literal("Mode: " + (cfg.pushToTalk ? "Push to Talk" : "Always On")));
		this.micVolumeLabel.setMessage(Component.literal("Mic " + cfg.micVolume + "%"));
		this.speakerVolumeLabel.setMessage(Component.literal("Speaker " + cfg.speakerVolume + "%"));
		this.gateLabel.setMessage(Component.literal(cfg.noiseGateDb <= ClientConfig.GATE_OFF ? "Gate OFF" : "Gate " + cfg.noiseGateDb + " dB"));
		this.inputButton.setMessage(Component.literal("In: " + shortName(cfg.inputDevice)));
		this.outputButton.setMessage(Component.literal("Out: " + shortName(cfg.outputDevice)));
	}

	private void changed(boolean sendStateToServer) {
		ProximityVoiceClient.CONFIG.save();
		if (sendStateToServer) VoiceSession.sendState();
		this.refreshLabels();
	}

	private Button button(int x, int y, int width, Component text, Consumer<Button> action, String tooltip) {
		var builder = Button.builder(text, b -> action.accept(b)).pos(x, y).size(width, 20);
		if (tooltip != null) {
			builder = builder.tooltip(Tooltip.create(Component.literal(tooltip)));
		}
		return this.addRenderableWidget(builder.build());
	}

	/** [-][ label ][+] in a 150px wide slot; returns the label button. */
	private Button stepper(int x, int y, Consumer<Button> minus, Consumer<Button> plus, String tooltip) {
		this.button(x, y, 20, Component.literal("-"), minus, null);
		Button label = this.button(x + 22, y, 106, Component.empty(), b -> { }, tooltip);
		label.active = false;
		this.button(x + 130, y, 20, Component.literal("+"), plus, null);
		return label;
	}

	private static Component onOff(boolean on) {
		return on ? Component.literal("ON").withStyle(ChatFormatting.GREEN) : Component.literal("OFF").withStyle(ChatFormatting.RED);
	}

	private static String next(List<String> options, String current) {
		int index = options.indexOf(current);
		return options.get((index + 1) % options.size());
	}

	private static String shortName(String device) {
		if (device == null || device.isEmpty()) return "Default";
		return device.length() > 17 ? device.substring(0, 16) + "\u2026" : device;
	}

	@Override
	public void onClose() {
		this.minecraft.gui.setScreen(this.parent);
	}
}
