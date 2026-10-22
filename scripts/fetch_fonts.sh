#!/usr/bin/env bash
# Downloads the four code fonts into app/src/main/res/font before the build.
# A failed download only prints a ::warning:: line. The app then uses the system monospace font.
set -u
DEST="app/src/main/res/font"
RAW="https://raw.githubusercontent.com/google/fonts/main/ofl"
mkdir -p "$DEST"

fetch() {
  name="$1"
  url="$2"
  if [ -s "$DEST/$name.ttf" ]; then
    echo "Font $name is already there."
    return 0
  fi
  if curl -fsSL --retry 3 --max-time 120 -o "$DEST/$name.tmp" "$url" && [ -s "$DEST/$name.tmp" ]; then
    magic="$(head -c 4 "$DEST/$name.tmp" | od -An -tx1 | tr -d ' \n')"
    if [ "$magic" = "00010000" ] || [ "$magic" = "74727565" ] || [ "$magic" = "4f54544f" ]; then
      mv "$DEST/$name.tmp" "$DEST/$name.ttf"
      echo "Font $name downloaded."
      return 0
    fi
  fi
  rm -f "$DEST/$name.tmp"
  echo "::warning::Could not download the font $name from $url. The app will use the system monospace font for it."
  return 0
}

fetch roboto_mono "$RAW/robotomono/RobotoMono%5Bwght%5D.ttf"
fetch fira_code "$RAW/firacode/FiraCode%5Bwght%5D.ttf"
fetch jetbrains_mono "$RAW/jetbrainsmono/JetBrainsMono%5Bwght%5D.ttf"
fetch courier_prime "$RAW/courierprime/CourierPrime-Regular.ttf"
exit 0
