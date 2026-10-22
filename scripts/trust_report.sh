#!/usr/bin/env bash
# Usage: bash scripts/trust_report.sh <apk> <output-folder>
# Writes TRUST_REPORT.md and exits 1 if the APK asks for permissions, is debuggable,
# contains a tracker/ads/analytics library. Unreadable aapt2 output is reported as INCONCLUSIVE with a warning.
# Set AAPT2=/path/to/aapt2 to use a specific aapt2 (used by the self-test).
set -u
APK="${1:-}"
OUT="${2:-.}"
REPO="${REPO_ROOT:-.}"
if [ -z "$APK" ] || [ ! -f "$APK" ]; then
  echo "Usage: bash scripts/trust_report.sh <apk> <output-folder>"
  exit 2
fi
mkdir -p "$OUT"
REPORT="$OUT/TRUST_REPORT.md"

AAPT="${AAPT2:-}"
if [ -z "$AAPT" ]; then
  if command -v aapt2 >/dev/null 2>&1; then
    AAPT="$(command -v aapt2)"
  elif [ -n "${ANDROID_HOME:-}" ] && [ -d "$ANDROID_HOME/build-tools" ]; then
    latest="$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)"
    AAPT="$ANDROID_HOME/build-tools/$latest/aapt2"
  fi
fi

FAIL=0
INCONCLUSIVE=0
SHA="$(sha256sum "$APK" | cut -d' ' -f1)"

PERM_RAW=""
BADGING_RAW=""
XML_RAW=""
if [ -n "$AAPT" ] && [ -x "$AAPT" ]; then
  PERM_RAW="$("$AAPT" dump permissions "$APK" 2>&1)"
  BADGING_RAW="$("$AAPT" dump badging "$APK" 2>&1)"
  XML_RAW="$("$AAPT" dump xmltree --file AndroidManifest.xml "$APK" 2>&1)"
fi

# --- permissions ---
PERMS=""
if ! printf '%s\n' "$PERM_RAW" | grep -q '^package:'; then
  INCONCLUSIVE=1
else
  PERMS="$(printf '%s\n' "$PERM_RAW" | grep -E "^uses-permission" | sed -E "s/.*name='([^']*)'.*/\1/" | sort -u)"
fi
BAD_PERMS=""
if [ -n "$PERMS" ]; then
  BAD_PERMS="$(printf '%s\n' "$PERMS" | grep -v 'DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION$')"
fi
[ -n "$BAD_PERMS" ] && FAIL=1

# --- debuggable ---
DEBUGGABLE="no"
if [ -z "$XML_RAW" ] && [ -z "$BADGING_RAW" ]; then
  INCONCLUSIVE=1
else
  if printf '%s\n' "$BADGING_RAW" | grep -q 'application-debuggable'; then DEBUGGABLE="YES"; fi
  if printf '%s\n' "$XML_RAW" | grep -Ei 'debuggable.*(=true|0xffffffff)' >/dev/null; then DEBUGGABLE="YES"; fi
fi
[ "$DEBUGGABLE" = "YES" ] && FAIL=1

# --- dex strings: trackers and web addresses ---
DEX="$(mktemp)"
unzip -p "$APK" 'classes*.dex' > "$DEX" 2>/dev/null
TRACKER_PATTERN='com/google/firebase|com/google/android/gms|com/facebook|appsflyer|io/sentry|mixpanel|amplitude|crashlytics|applovin|unity3d/ads|com/unity3d/services|com/adjust/sdk|com/flurry|io/branch|onesignal|bugsnag|mopub|ironsource|vungle|chartboost|com/google/android/ads|datadog|segment/analytics'
TRACKERS="$(grep -a -o -i -E "$TRACKER_PATTERN" "$DEX" | sort | uniq -c)"
[ -n "$TRACKERS" ] && FAIL=1
URLS="$(grep -a -o -E "https?://[A-Za-z0-9./?=&_%:#@+~-]+" "$DEX" | sort -u | head -60)"
rm -f "$DEX"

# --- native files ---
NATIVE="$(unzip -l "$APK" | grep -E '\.so$|/lib/|^ *[0-9]+ .* lib/' | awk '{print $NF}')"

# --- dependencies ---
DEPS=""
if [ -f "$REPO/app/build.gradle.kts" ]; then
  DEPS="$(grep -oE '(implementation|api)\("[^"]+"\)' "$REPO/app/build.gradle.kts" | sed -E 's/.*\("([^"]+)"\)/\1/')"
fi

if [ "$FAIL" = "1" ]; then STATUS="FAILED"
elif [ "$INCONCLUSIVE" = "1" ]; then STATUS="INCONCLUSIVE"
else STATUS="PASSED"; fi

{
  echo "# HTMLPlayForge trust report"
  echo
  echo "Result: **$STATUS**"
  echo
  echo "These are facts you can check yourself. They are not a promise that the app is perfect."
  echo
  echo "## File"
  echo "- Name: $(basename "$APK")"
  echo "- SHA-256: \`$SHA\`"
  echo
  echo "## Permissions"
  if [ -z "$PERMS" ]; then echo "- None requested."; else printf '%s\n' "$PERMS" | sed 's/^/- /'; fi
  [ -n "$BAD_PERMS" ] && { echo; echo "NOT ALLOWED (build fails):"; printf '%s\n' "$BAD_PERMS" | sed 's/^/- /'; }
  echo
  echo "## Debuggable"
  echo "- $DEBUGGABLE"
  echo
  echo "## Tracker, ads and analytics libraries found in the code"
  if [ -z "$TRACKERS" ]; then echo "- None."; else printf '%s\n' "$TRACKERS" | sed 's/^/- /'; fi
  echo
  echo "## Web addresses found in the code"
  if [ -z "$URLS" ]; then echo "- None."; else printf '%s\n' "$URLS" | sed 's/^/- /'; fi
  echo
  echo "## Native (compiled C/C++) files"
  if [ -z "$NATIVE" ]; then echo "- None."; else printf '%s\n' "$NATIVE" | sed 's/^/- /'; fi
  echo
  echo "## Libraries the app uses"
  if [ -z "$DEPS" ]; then echo "- (build file not found)"; else printf '%s\n' "$DEPS" | sed 's/^/- /'; fi
  if [ "$INCONCLUSIVE" = "1" ]; then
    echo
    echo "## INCONCLUSIVE: raw tool output"
    echo "aapt2 path: ${AAPT:-not found}"
    echo '```'
    printf '%s\n' "$PERM_RAW" | head -40
    printf '%s\n' "$BADGING_RAW" | head -20
    printf '%s\n' "$XML_RAW" | head -20
    echo '```'
  fi
} > "$REPORT"

echo "Trust report: $STATUS ($REPORT)"
if [ "$FAIL" = "1" ]; then
  echo "::error::Trust check $STATUS. Read $REPORT."
  exit 1
fi
if [ "$STATUS" = "INCONCLUSIVE" ]; then
  echo "::warning::Trust check is INCONCLUSIVE (aapt2 output could not be read). The build continues. Read $REPORT."
fi
exit 0
