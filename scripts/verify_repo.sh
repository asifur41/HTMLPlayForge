#!/usr/bin/env bash
# Checks that your repo is exactly the files that were delivered and reviewed.
# Run from the project folder:  bash scripts/verify_repo.sh
set -u
cd "$(dirname "$0")/.." || exit 2
LIST="scripts/expected-hashes.txt"
if [ ! -f "$LIST" ]; then echo "Missing $LIST"; exit 2; fi

bad=0
if ! sha256sum -c --quiet "$LIST"; then
  echo "DIFFERENT: some files above are missing or changed."
  bad=1
fi

# files that are not in the list (build folders, fonts and the list itself are ignored)
extra=0
while IFS= read -r f; do
  f="${f#./}"
  if ! grep -q -F "  $f" "$LIST"; then
    echo "EXTRA file (not in the delivered list): $f"
    extra=1
  fi
done < <(find . -type f \
  -not -path './.git/*' -not -path '*/build/*' -not -path './.gradle/*' -not -path './.idea/*' \
  -not -path './release-out/*' -not -name '*.ttf' -not -name 'expected-hashes.txt' -not -name 'local.properties' \
  -not -name '*.jks' | sort)

if [ "$bad" = "0" ] && [ "$extra" = "0" ]; then
  echo "VERIFIED: your repo is exactly what was delivered."
  exit 0
fi
[ "$bad" = "0" ] && echo "Files match, but there are extra files (see above). Extra files are fine if you added them on purpose."
exit 1
