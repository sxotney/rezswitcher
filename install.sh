#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"

# Shield adb address, e.g. 192.168.1.50:5555. Set SHIELD in the environment or in .env (see .env.example).
[ -f .env ] && . ./.env
DEV="${SHIELD:?Set SHIELD to your Shield's adb address, e.g. export SHIELD=192.168.1.50:5555 (or copy .env.example to .env)}"
PKG=app.rezswitcher

adb connect "$DEV" >/dev/null
adb -s "$DEV" install -r build/rezswitcher.apk
adb -s "$DEV" shell appops set "$PKG" SYSTEM_ALERT_WINDOW allow
adb -s "$DEV" shell appops set "$PKG" GET_USAGE_STATS allow
adb -s "$DEV" shell pm grant "$PKG" android.permission.READ_LOGS
adb -s "$DEV" shell cmd notification allow_listener "$PKG/$PKG.NotificationListener"
adb -s "$DEV" shell am start-foreground-service -n "$PKG/.AfrService"
echo "Installed and started on $DEV"
