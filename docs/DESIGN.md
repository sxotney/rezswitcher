# RezSwitcher: design

## Problem

The NVIDIA Shield TV (2019, Android 11 / API 30, Shield Experience 9.x) has no "match content
frame rate" option, and most streaming apps (Netflix, Prime Video, Apple TV, YouTube, HBO Max)
never change the display refresh rate. With one fixed output mode, something always judders:

| Fixed mode | 24 fps films | 25/50 fps European TV | 30/60 fps video |
|---|---|---|---|
| 4K 50 Hz | hitch ~2× per second | perfect | mild judder |
| 4K 59.94 Hz | 3:2 judder | judder | perfect |

These were measured on the device with SurfaceFlinger frame timing (`dumpsys SurfaceFlinger --latency`).

## Goal

A small sideloaded Android TV app that switches the output refresh rate to match whatever video
is playing, in any app, and returns to the system default when playback ends.

## Non-goals

- No resolution changes. Only the refresh rate changes, at the panel's maximum resolution.
- No settings UI. Behaviour is fixed by constants.
- No Play Store distribution. Sideload only, with permissions granted over adb.

## Mechanism

### 1. Detecting the content frame rate

The Shield's hardware decoder (`media.codec`) logs, at tag `NvOsDebugPrintf`, roughly every 120
decoded frames:

```
NVMEDIA: FrameRate(for last 120 frames) = 23.976043, AvgFrameRate = 23.976043
```

This line appears for Netflix (Widevine), Prime Video, Apple TV, YouTube, HBO Max and
Chromium-based broadcaster apps alike. The app reads logcat filtered to that tag
(`NvOsDebugPrintf:D *:S`). `READ_LOGS` has protection level `signature|privileged|development`, so
it can be granted with `adb shell pm grant`. The "for last 120 frames" value is used rather than the
average, because the average lags after a content change.

### 2. Switching the mode

Android 11 takes the display mode from the **top-most visible window that sets
`WindowManager.LayoutParams.preferredDisplayModeId`**. The app adds an invisible, non-focusable,
non-touchable 1×1 `TYPE_APPLICATION_OVERLAY` window with that field set, and removes it to give
control back. `SYSTEM_ALERT_WINDOW` is granted with `adb shell appops set`.

### 3. Mode ids are not stable

After an HDMI renegotiation the Shield **renumbers** its display modes (for example, an id that
meant 4K 25 Hz later meant 4K 24 Hz). The service re-reads `Display.getSupportedModes()` every time
it resolves a frame rate, and never caches ids.

### 4. Pause → switch → resume

Every refresh-rate change makes the Shield re-plug its HDMI audio device (~0.2–0.3 s), which can
come 1.5–4 s after the switch. Netflix, for example, tears down its player and stays stopped if
the re-plug happens while it's playing. If it's paused first and sent `play` afterwards, it resumes
at the right position.

So every switch (request **and** release) is wrapped:

1. Pause every media session that is `STATE_PLAYING`, and remember their packages.
2. Wait `PAUSE_LEAD_MS` = 500 ms, then apply the mode change.
3. Wait for the HDMI audio output to be **re-added** (`AudioDeviceCallback`, a `TYPE_HDMI` sink), then wait `REPLUG_GRACE_MS` = 500 ms and resume the remembered sessions. If no re-add arrives within `MAX_SETTLE_MS` = 8000 ms, resume anyway. Re-add events arriving when no switch is waiting are ignored.
4. If another switch arrives during steps 2–3, the pending switch is replaced (latest wins) and the wait restarts. Remembered sessions are resumed once, at the end.
5. If nothing was playing, the switch happens immediately and nothing is resumed.
6. A **request** that wouldn't change the display (same size and refresh rate as now) is applied immediately, with no pause. If a pause is already held from an applied switch, keep waiting for its re-plug; if the earlier switch was cancelled before it was applied, resume after `REPLUG_GRACE_MS`. Comparisons use size and refresh rate, never ids. A **release** of an active override is always treated as a change, because removing the overlay can expose an underlying app's own mode request. Releases usually happen with nothing playing, so they still apply at once.
7. **Service teardown** (an orderly stop) cancels pending work, releases the overlay and resumes held sessions on the service thread, bounded to 1 s.

**How resuming works.** For each remembered package that still has an active session,
RezSwitcher sends `TransportControls.play()` if it isn't playing. Some apps (Netflix) mark their
session inactive when they stop, so it disappears from `getActiveSessions()`. For those,
RezSwitcher sends an untargeted `KEYCODE_MEDIA_PLAY` through `AudioManager.dispatchMediaKeyEvent`,
which Android routes to the media-button session. It only does this when no *other* app has an
active session, so the key can't start unrelated playback.

Session access uses `MediaSessionManager.getActiveSessions(listener)`, which needs an enabled
notification listener (an empty `NotificationListenerService`), enabled over adb with
`cmd notification allow_listener`. Without it, pause/resume is skipped (logged) and switching
still works.

### 5. Knowing which app is in front

`UsageStatsManager.queryEvents`, polled every 2 s for `ACTIVITY_RESUMED` events. This needs the
`GET_USAGE_STATS` appop, granted over adb.

## Frame rate → mode table

Only modes at the panel's maximum resolution are considered. Refresh rates match within ±0.02 Hz,
since the Shield reports values such as 23.976025 and 59.94006.

| Content fps (inclusive ranges) | Target refresh |
|---|---|
| 23.95 – <23.99 | 23.976 |
| 23.99 – 24.05 | 24 |
| 24.9 – 25.1 and 49.8 – 50.2 | 50 |
| 29.95 – <29.99 and 59.88 – <59.97 | 59.94 |
| 29.99 – 30.05 and 59.97 – 60.05 | 60 |
| anything else | no change |

25 fps maps to 50 Hz rather than 25 Hz, so menus stay responsive and each frame is shown exactly
twice.

## Behaviour rules

1. **The first reading switches immediately.** That's about 5 s into playback, because the decoder logs every 120 frames.
2. **Changing to a different mode needs two consecutive agreeing readings.** This avoids flapping between trailers or ads and the main content. A reading that matches the active mode resets the pending change.
3. **Switching apps releases the override.**
4. **No reading for 60 s** (stopped, or a long pause) releases the override. Short pauses keep the mode, which avoids extra HDMI re-syncs.
5. **Excluded packages** are ignored. The controller supports a list; it's empty by default.
6. **Unknown frame rates** change nothing.
7. **Debug override:** `am start-foreground-service -n app.rezswitcher/.AfrService --ei force_mode N` forces mode N, and `0` clears it. While forced, automatic decisions are suspended.
8. **All switches** go through pause → switch → resume (§4), including releases and debug overrides.

## Lifecycle

- A foreground service (`AfrService`) runs the logcat reader, the foreground watcher and the controller, all on one single-threaded executor.
- `BootReceiver` starts the service on `BOOT_COMPLETED`.
- `MainActivity` (launcher entry, no UI) starts the service and closes.

## Code layout

| Class | Role |
|---|---|
| `core/FrameRateParser` | decoder log line → fps |
| `core/ModeSelector`, `core/ModeInfo` | fps → target refresh → mode id |
| `core/AfrController` | readings + foreground app + idle → mode requests (rules 1–7) |
| `core/PauseResumeSink` | pause → switch → resume wrapper (§4) |
| `core/ResumePolicy` | when the untargeted play key is allowed |
| `OverlayModeSink` | the 1×1 overlay window |
| `DisplayChangeCheck` | would a request or release change the display? |
| `MediaSessionControl` | pause/resume media sessions |
| `LogcatFrameRateSource`, `ForegroundWatcher` | inputs |
| `AfrService`, `BootReceiver`, `MainActivity`, `NotificationListener` | Android lifecycle |

Everything in `core/` is pure Java with unit tests (`./test.sh`).

## Known risks

1. **Firmware changes:** if NVIDIA changes or removes the decoder log line, the parser stops matching and RezSwitcher stops switching. It fails safe.
2. **Two decoders at once** (picture-in-picture): the last reading wins.
3. **Trailers or ads at a different frame rate** cause at most one extra switch, because of rule 2.
4. **Apps that ignore both `play` and the media key** after a switch need a manual press of play. Netflix, YouTube and HBO Max resume correctly; other apps are untested.
5. **Unrelated playing sessions** (background music) are paused too, and resumed after the switch.
6. **Process kills skip teardown.** A reinstall or an external kill SIGKILLs the process, so `onDestroy` never runs. The overlay disappears, the display drops to the default mid-playback without a pause, and the playing app may stop. An orderly stop resumes held sessions immediately rather than waiting for the re-plug, because waiting would block the main thread.
7. **The play-key fallback can't verify its recipient.** Android 11 has no public API naming the media-button owner. Another app could receive the key only if it started *and* stopped playback during the few seconds of a switch.
8. **Memory pressure on 2 GB models:** an HDMI mode change can coincide with the low-memory killer clearing apps, sometimes including the playing app. RezSwitcher re-switches and resumes once the app is back, but the app restarts.
