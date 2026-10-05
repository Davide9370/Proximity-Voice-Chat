package com.proximityvoice.client.audio;

import java.util.ArrayList;
import java.util.List;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.SourceDataLine;
import javax.sound.sampled.TargetDataLine;

/**
 * Uses Java's built-in javax.sound (works on Windows, macOS and Linux, no native libs to ship).
 * 48 kHz is used on the device side because practically every sound card supports it.
 */
public final class AudioDevices {
	public static final AudioFormat MIC_FORMAT = new AudioFormat(48000f, 16, 1, true, false);
	public static final AudioFormat SPEAKER_FORMAT = new AudioFormat(48000f, 16, 2, true, false);

	private AudioDevices() {
	}

	public static List<String> inputDevices() {
		return list(new DataLine.Info(TargetDataLine.class, MIC_FORMAT));
	}

	public static List<String> outputDevices() {
		return list(new DataLine.Info(SourceDataLine.class, SPEAKER_FORMAT));
	}

	private static List<String> list(DataLine.Info info) {
		List<String> names = new ArrayList<>();
		try {
			for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
				try {
					if (AudioSystem.getMixer(mixerInfo).isLineSupported(info) && !names.contains(mixerInfo.getName())) {
						names.add(mixerInfo.getName());
					}
				} catch (Exception ignored) {
					// broken driver, skip it
				}
			}
		} catch (Exception ignored) {
			// no audio system at all
		}
		return names;
	}

	private static Mixer.Info find(String name) {
		if (name == null || name.isEmpty()) return null;
		for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
			if (mixerInfo.getName().equals(name)) return mixerInfo;
		}
		return null;
	}

	public static TargetDataLine openMicrophone(String deviceName, int bufferBytes) throws LineUnavailableException {
		DataLine.Info info = new DataLine.Info(TargetDataLine.class, MIC_FORMAT);
		Mixer.Info mixer = find(deviceName);
		TargetDataLine line = (TargetDataLine) (mixer != null ? AudioSystem.getMixer(mixer).getLine(info) : AudioSystem.getLine(info));
		line.open(MIC_FORMAT, bufferBytes);
		line.start();
		return line;
	}

	public static SourceDataLine openSpeaker(String deviceName, int bufferBytes) throws LineUnavailableException {
		DataLine.Info info = new DataLine.Info(SourceDataLine.class, SPEAKER_FORMAT);
		Mixer.Info mixer = find(deviceName);
		SourceDataLine line = (SourceDataLine) (mixer != null ? AudioSystem.getMixer(mixer).getLine(info) : AudioSystem.getLine(info));
		line.open(SPEAKER_FORMAT, bufferBytes);
		line.start();
		return line;
	}
}
