# RezSwitcher

Automatic frame-rate matching ("AFR") for the **NVIDIA Shield TV (2019) on Android 11**.

The Shield's current firmware has no "match content frame rate" option, and most streaming
apps never change the display's refresh rate, so something always judders: 24 fps films on a
60 Hz display, 25/50 fps European TV on 60 Hz, or 30 fps video on 50 Hz. RezSwitcher watches what
the hardware video decoder is playing and switches the TV to the matching refresh rate, in any app.

| Content | Display |
|---|---|
| 23.976 / 24 fps (films, most series) | 23.976 / 24 Hz |
| 25 / 50 fps (European broadcast) | 50 Hz |
| 29.97 / 59.94 fps | 59.94 Hz |
| 30 / 60 fps (much of YouTube) | 60 Hz |

When the content ends, or you leave the app, the Shield goes back to its own default mode.

## How it works

- **Detects** the frame rate from the Shield's video decoder log (`NVMEDIA: FrameRate…`), which needs log access granted over adb.
- **Switches** by placing an invisible 1×1 overlay window that requests the matching display mode. Android follows the top-most window's request, and removing the window returns control to the system.
- **Pauses and resumes playback around each switch.** A refresh-rate change makes the Shield briefly reconnect HDMI audio, and some apps (Netflix, for one) stop playback when that happens. RezSwitcher pauses whatever is playing, switches, waits for HDMI audio to come back, then resumes. Expect a 2.5–5 s pause and a blank screen about 5 s into a title.
- **Skips the switch** when the content already matches the current refresh rate, so there's no pause at all.

Full design notes: [`docs/DESIGN.md`](docs/DESIGN.md).

## Requirements

- NVIDIA Shield TV on Shield Experience 9.x (Android 11). Tested on the 2019 "tube" model. The 2019 Pro runs the same firmware and should work, but hasn't been tested. Other Android TV devices don't produce the decoder log line this relies on.
- **Network debugging** enabled on the Shield (Settings → Device Preferences → Developer options), and the Shield's IP address.
- On your computer: JDK 17, `adb`, and the Android SDK with **platform 34** and **build-tools 34.0.0**. The build scripts look for the SDK in `$ANDROID_HOME`, then `$ANDROID_SDK_ROOT`, then `~/Library/Android/sdk`.

No Gradle; the build is a short shell script.

## Install

```bash
cp .env.example .env          # then set SHIELD=<your-shield-ip>:5555
./test.sh                     # unit tests (no device needed)
./build.sh                    # builds build/rezswitcher.apk
./install.sh                  # installs, grants permissions over adb, starts the service
```

The first time, accept the "allow USB debugging" prompt on the TV. `install.sh` grants:

| Permission | Why |
|---|---|
| `READ_LOGS` | read the decoder's frame-rate log line |
| Display over other apps | the 1×1 overlay that requests the display mode |
| Usage access | know which app is in front |
| Notification listener | find media sessions so playback can be paused and resumed |

RezSwitcher starts automatically after a reboot. It has no settings screen.

## Watching it work

```bash
adb -s "$SHIELD" logcat -s RezSwitcher
```

Typical output when a film starts:

```
fps 23.976043 -> mode 2 at 3840x2160
paused com.netflix.ninja
hdmi audio added
resumed com.netflix.ninja
```

`tools/framepace.sh <package>` measures frame pacing of an app's video layer. One dominant interval
(for example 42 ms at 24 Hz) means smooth playback.

To force a mode for testing (`0` releases it), use `adb -s "$SHIELD" shell am start-foreground-service -n app.rezswitcher/.AfrService --ei force_mode <id>`. Mode ids come from `dumpsys display`, and the Shield renumbers them after HDMI changes.

## Known limitations

- **A short pause and blank on each switch** is unavoidable: the TV has to re-sync HDMI.
- **If RezSwitcher is killed or reinstalled mid-film,** the display drops back to the default without the pause/resume treatment, and the playing app may stop.
- **On the 2 GB models,** Android sometimes kills apps around a mode switch. The playing app may restart; RezSwitcher re-syncs once it's back.
- **Any playing media is paused around a switch,** including background music. It's resumed afterwards.
- **If an app's media session disappears during the switch,** RezSwitcher resumes it with a media "play" key, but only when no other app has an active media session.
- **It relies on an NVIDIA debug log line,** which a firmware update could remove. If that happens, RezSwitcher simply stops switching.

## Uninstall

```bash
adb -s "$SHIELD" uninstall app.rezswitcher
```

## Development

- `app/src/app/rezswitcher/core/` is pure Java (no Android imports) and holds all the decision logic. It's covered by `./test.sh` (JUnit 4, downloaded on first run).
- The Android glue lives in `app/src/app/rezswitcher/`.
- Testing on a device switches the TV's display mode, so do it with someone watching the screen.

## Licence

MIT. See [`LICENSE`](LICENSE).
