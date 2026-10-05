Proximity voice chat similar to Simple Voice Chat, with one big difference:
**the voice audio travels inside the normal Minecraft connection** (custom payload packets),
so the server does **not** need a second UDP port. If players can join your server, voice works.

## Features

- Press **J** to open the voice settings (mic on/off, speaker on/off, mode, volumes, noise gate, devices)
- Default mode is **Always On**; switch to **Push to Talk** in the J menu (PTT key default **V**)
- Both keys can be changed in *Options → Controls → Key Binds → Proximity Voice*
- The server decides who hears whom (same dimension, within range), so nobody can eavesdrop from far away
- Voices fade with distance and are panned left/right depending on where you look
- No native libraries: pure Java audio, works on Windows, macOS and Linux

## Requirements

**Fabric API is required**, on the server and on every client.

## Who needs to install what

| Player / side | What they need | What happens |
|---|---|---|
| Server | Fabric Loader + Fabric API + this mod | Routes voice between players |
| Java player who wants voice | Fabric Loader + Fabric API + this mod | Can talk and listen |
| Java player without the mod | Nothing | Can join and play normally, no voice, gets a chat hint |
| Bedrock player via Geyser | Nothing | Can join and play normally, no voice, gets a chat hint |

A vanilla Minecraft client has no way to record a microphone or play a live audio stream, so players who want
voice must install the mod (Simple Voice Chat works the same way). Bedrock clients can't load Fabric mods, so
Bedrock players can't use the voice chat itself, but Geyser/Floodgate players can join the server without issues:
the server never sends them voice packets and shows them a friendly message instead.
Make sure you use a Geyser build that supports Java 26.3.

## Installing

1. Download this mod and Fabric API.
2. Put both files in the server's `mods/` folder.
3. Every Java player who wants voice puts the same two files in their `.minecraft/mods/` folder.
4. Start the server once; the config is created at `config/proximityvoice/server.properties`.

Server and clients must run the same mod version.

## Server config

`config/proximityvoice/server.properties` (reload with `/voice reload`, needs op level 2 or the
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

## Client config

`config/proximityvoice/client.properties` (everything is also in the J menu):
`microphone-enabled`, `speaker-enabled`, `activation-mode` (`always-on` / `push-to-talk`),
`microphone-volume`, `speaker-volume`, `noise-gate-db`, `input-device`, `output-device`.

**Noise gate:** in Always On mode the mic stays live, but sounds quieter than the gate
(default -50 dB) are not sent, so you don't stream silence or fan noise to everyone.
Set it to OFF (-80) for a truly open mic. Raise it if background noise gets through.

## Commands

- `/voice status` – shows who has voice chat and who doesn't
- `/voice reload` – reloads the server config

## Troubleshooting

- **Nobody hears me:** press J, check the *Level* meter turns green when you talk. If it never moves,
  pick another input device (*In:* button). If it moves but stays grey, lower the noise gate.
- **macOS:** the launcher/Java must have microphone permission
  (System Settings → Privacy & Security → Microphone).
- **"This server doesn't have Proximity Voice":** the server is missing the mod or Fabric API.
- Logs: search `latest.log` for `proximityvoice`.

## Known limitations

- Audio goes over TCP together with game traffic, so on a very laggy connection voice lags as well
  (the jitter buffer drops old audio to keep delay low).
- Audio uses ADPCM, which is simpler than Opus: quality is like a good phone call, at higher bandwidth than Opus.
- No groups, whisper or per-player mute yet.
- Behind Velocity/BungeeCord it should work, since proxies normally pass plugin-message channels through, but this is not tested yet.
