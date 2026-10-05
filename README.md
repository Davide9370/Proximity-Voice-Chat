# Proximity Voice (Fabric, Minecraft 26.3)

> **Versions:** this branch (`main`) is the build for **Minecraft 26.3**. The build for **Minecraft 1.21.11** is on the [`mc-1.21.11` branch](https://github.com/Davide9370/Proximity-Voice-Chat/tree/mc-1.21.11). Server and clients must use the build for the same Minecraft version.

Proximity voice chat similar to Simple Voice Chat, with one big difference:
**the voice audio travels inside the normal Minecraft connection** (custom payload packets),
so the server does **not** need a second UDP port. If players can join your server, voice works.

- Default key **J** opens the voice settings (mic on/off, speaker on/off, mode, volumes, noise gate, devices)
- Default mode is **Always On**; switch to **Push to Talk** in the J menu (PTT key default **V**)
- Both keys can be changed in *Options → Controls → Key Binds → Proximity Voice*
- The server decides who hears whom (same dimension, within range), so nobody can eavesdrop from far away
- Voices fade with distance and are panned left/right depending on where you look
- No native libraries: pure Java audio, works on Windows, macOS and Linux

## Who needs to install what

| Player / side | What they need | What happens |
|---|---|---|
| Server | Fabric Loader + Fabric API + this mod | Routes voice between players |
| Java player who wants voice | Fabric Loader + Fabric API + this mod | Can talk and listen |
| Java player without the mod | Nothing | Can join and play normally, no voice, gets a chat hint |
| Bedrock player via Geyser | Nothing | Can join and play normally, no voice, gets a chat hint |

Why players need the mod: a vanilla Minecraft client has no way to record a microphone or play a live
audio stream, so some client-side code is always required (Simple Voice Chat works the same way).
Bedrock clients can't load Fabric mods, so Bedrock players can't use the voice chat itself, but
**Geyser/Floodgate players are fully compatible**: the server never sends them voice packets, and they
are detected (via the Floodgate or Geyser API, or the Floodgate UUID format) to show a friendly message.
Make sure you use a Geyser build that supports Minecraft 26.3.

## Building the .jar

You need **JDK 25** (for example Microsoft Build of OpenJDK 25 or Eclipse Temurin 25).

```
# Windows
gradlew.bat build

# macOS / Linux
./gradlew build
```

The mod ends up in `build/libs/proximityvoice-1.0.0.jar` (ignore the `-sources` jar).

**No Java on your PC?** Upload this folder to a GitHub repository. The included
`.github/workflows/build.yml` builds it automatically; download the jar from the *Actions* tab → *Artifacts*.

Versions used (from the official Fabric example mod for 26.3): Minecraft 26.3, Fabric Loader 0.19.5,
Fabric API 0.161.0+26.3, Loom 1.18-SNAPSHOT, Gradle 9.7.1.

## Installing

1. Put `proximityvoice-1.0.0.jar` **and** Fabric API in the server's `mods/` folder.
2. Every Java player who wants voice puts the same two jars in their `.minecraft/mods/` folder.
3. Start the server once; the config is created at `config/proximityvoice/server.properties`.

## Config files

Example files with all defaults are in `config-examples/proximityvoice/`.

**Server** – `config/proximityvoice/server.properties` (reload with `/voice reload`, needs op level 2 or the
permission `proximityvoice:command.reload`):

| Option | Default | Meaning |
|---|---|---|
| `enabled` | `true` | Voice on/off for the whole server |
| `max-distance` | `48.0` | Range in blocks; players further away receive nothing |
| `fade-start-distance` | `8.0` | Full volume up to here, then fades until max-distance |
| `spectators-heard-by-everyone` | `false` | If false, spectators are only heard by other spectators |
| `max-packets-per-second` | `75` | Anti-spam (talking = 50 packets/s) |
| `notify-players-without-mod` / `no-mod-message` | `true` | Chat hint for Java players without the mod (`&` color codes) |
| `notify-bedrock-players` / `bedrock-message` | `true` | Chat hint for Geyser/Bedrock players |

**Client** – `config/proximityvoice/client.properties` (everything is also in the J menu):
`microphone-enabled`, `speaker-enabled`, `activation-mode` (`always-on` / `push-to-talk`),
`microphone-volume`, `speaker-volume`, `noise-gate-db`, `input-device`, `output-device`.

About the noise gate: in Always On mode the mic stays live, but sounds quieter than the gate
(default -50 dB) are not sent, so you don't stream silence or fan noise to everyone.
Set it to OFF (-80) for a truly open mic. Raise it if background noise gets through.

## Commands

- `/voice status` – shows who has voice chat and who doesn't
- `/voice reload` – reloads the server config

## How it works (technical)

- Client captures 48 kHz mono → downsampled to 16 kHz → 20 ms frames → IMA-ADPCM (4 bit/sample).
  One frame = 163 bytes, so a talking player uses about 65 kbit/s, and silence is not sent at all.
- Frames are sent as a Fabric custom payload (`proximityvoice:mic`). The server checks range,
  dimension, spectator rules and rate limits, then forwards `proximityvoice:speaker` packets only to
  nearby players that have the mod and their speaker on. Packets stay under the default
  network-compression threshold (256 bytes), so the server doesn't waste CPU compressing audio.
- Clients use a small jitter buffer (60 ms) per speaker, mix everything to 48 kHz stereo and play it.
- Should also work behind Velocity/BungeeCord, since proxies normally pass plugin-message channels
  through (not tested yet).

## Troubleshooting

- **Nobody hears me:** press J, check the *Level* meter turns green when you talk. If it never moves,
  pick another input device (*In:* button). If it moves but stays grey, lower the noise gate.
- **macOS:** the launcher/Java must have microphone permission
  (System Settings → Privacy & Security → Microphone).
- **"This server doesn't have Proximity Voice":** the server is missing the mod or Fabric API.
- **Version mismatch:** server and client must run the same mod version.
- Logs: search `latest.log` for `proximityvoice`.

## Known limitations

- Audio goes over TCP together with game traffic, so on a very laggy connection voice lags as well
  (the jitter buffer drops old audio to keep delay low).
- ADPCM is simpler than Opus: quality is like a good phone call, at higher bandwidth than Opus.
- No groups, whisper or per-player mute yet.
