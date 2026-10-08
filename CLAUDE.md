# rezswitcher

## Project
Sideloaded Android TV app for the NVIDIA Shield TV (2019, Android 11) that matches the display
refresh rate to the playing video, pausing and resuming media around each switch. User docs in
`README.md`, design in `docs/DESIGN.md`.

Invariants:
- Display mode ids are renumbered by the Shield after HDMI changes, so never cache or hard-code them.
- `core/` must not import `android.*`. All decision logic goes there, with unit tests.
- Device tests switch the TV's display mode (the screen blanks), so run them with a person watching.

## Stack
Java 8 language level, Android SDK platform 34 jar (min/target SDK 30), build-tools 34.0.0, JDK 17,
JUnit 4. Shell build scripts, no Gradle.

## Commands
- `./test.sh`: unit tests for the pure-Java `core` package
- `./build.sh`: builds `build/rezswitcher.apk`
- `./install.sh`: installs on the Shield in `$SHIELD` (or `.env`), grants permissions, starts the service
- `tools/framepace.sh <pkg>`: measures frame pacing of an app's video layer
- `adb -s "$SHIELD" logcat -s RezSwitcher`: live decisions

## Conventions
- Plans are written to `PLAN.md` and reviewed via claudex-loop before implementation.
- Keep `PLAN-REVIEW-LOG.md` committed alongside the plan it covers.
