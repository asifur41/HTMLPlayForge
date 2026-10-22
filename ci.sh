#!/usr/bin/env bash
# Usage: bash ci.sh "what I changed"
# Commits everything, pushes, waits for the GitHub build, then prints
# BUILD SUCCEEDED or the error lines.
set -u
msg="${1:-Update}"

git add -A
if git diff --cached --quiet; then
  echo "Nothing new to commit. Checking the last build of this commit."
else
  git commit -q -m "$msg" || { echo "COMMIT FAILED"; exit 1; }
fi
git push -q || { echo "PUSH FAILED. Check that you are logged in (gh auth status)."; exit 1; }

sha="$(git rev-parse HEAD)"
echo "Pushed $sha. Waiting for GitHub to start the build..."
id=""
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  id="$(gh run list --workflow build-apk.yml --commit "$sha" --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null)"
  [ -n "$id" ] && [ "$id" != "null" ] && break
  sleep 5
done
if [ -z "$id" ] || [ "$id" = "null" ]; then
  echo "The push did not start a build. Starting one by hand..."
  branch="$(git branch --show-current)"
  gh workflow run build-apk.yml --ref "$branch" || { echo "COULD NOT START THE BUILD. Run: bash scripts/doctor.sh"; exit 1; }
  sleep 10
  id="$(gh run list --workflow build-apk.yml --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null)"
  if [ -z "$id" ] || [ "$id" = "null" ]; then
    echo "Still no build run. Run: bash scripts/doctor.sh"
    exit 1
  fi
fi

if gh run watch "$id" --interval 10 --exit-status >/dev/null 2>&1; then
  echo "BUILD SUCCEEDED"
  exit 0
fi
echo "BUILD FAILED. The important lines:"
gh run view "$id" --log-failed 2>/dev/null | grep -E "(error:|e: |FAILED|::error|What went wrong|Unresolved|Execution failed|AssertionError|Could not resolve)" | sed -E 's/^[^	]*	[^	]*	//' | head -40
echo "Copy the lines above and send them to Claude."
exit 1
