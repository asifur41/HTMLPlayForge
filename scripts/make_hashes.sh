#!/usr/bin/env bash
# Rebuilds scripts/expected-hashes.txt (developer tool; run after you change files on purpose).
set -u
cd "$(dirname "$0")/.." || exit 2
find . -type f \
  -not -path './.git/*' -not -path '*/build/*' -not -path './.gradle/*' -not -path './.idea/*' \
  -not -path './release-out/*' -not -name '*.ttf' -not -name 'expected-hashes.txt' -not -name 'local.properties' \
  -not -name '*.jks' | sort | sed 's#^\./##' | xargs sha256sum > scripts/expected-hashes.txt
echo "Wrote scripts/expected-hashes.txt with $(wc -l < scripts/expected-hashes.txt) files."
