#!/usr/bin/env bash
# Usage: tools/framepace.sh <package-substring>   — prints frame intervals for that app's video layers over 6 s
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
[ -f "$ROOT/.env" ] && . "$ROOT/.env"
D="${SHIELD:?Set SHIELD to your Shield's adb address, e.g. export SHIELD=192.168.1.50:5555 (or copy .env.example to .env)}"
PKG="$1"
adb -s "$D" shell dumpsys SurfaceFlinger --list </dev/null | tr -d '\r' \
  | grep -E "$PKG" | grep -vE "Background|ActivityRecord|Bounds|^[0-9a-f]+ " > /tmp/framepace.$$
while IFS= read -r l <&3; do
  adb -s "$D" shell "dumpsys SurfaceFlinger --latency-clear '$l'" </dev/null >/dev/null
  sleep 6
  printf '%s: ' "$l"
  adb -s "$D" shell "dumpsys SurfaceFlinger --latency '$l'" </dev/null | python3 -c '
import sys, collections
ls = [l.split() for l in sys.stdin if l.strip()]
t = [int(x[1]) for x in ls[1:] if len(x) == 3 and x[1] != "0" and int(x[1]) < 9e18]
if len(t) < 3: print("no frames"); sys.exit()
d = [(b - a) / 1e6 for a, b in zip(t, t[1:])]
print("vsync %.2fms fps %.1f intervals %s" % (int(ls[0][0]) / 1e6, len(t) / ((t[-1] - t[0]) / 1e9), collections.Counter(round(x) for x in d).most_common(6)))'
done 3< /tmp/framepace.$$
rm -f /tmp/framepace.$$
