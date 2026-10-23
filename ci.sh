#!/usr/bin/env bash
# Usage: bash ci.sh "what I changed"
# Commits everything, pushes, waits for the GitHub build, then prints
# BUILD SUCCEEDED or the error lines. It prints every stage, so it is never silent.
msg="${1:-Update}"

echo "[1/4] Adding your files..."
git add -A

echo "[2/4] Committing..."
if git diff --cached --quiet; then
  echo "      Nothing new to commit. Using the last commit."
else
  git commit -q -m "$msg" || { echo "COMMIT FAILED. If it says 'Author identity unknown', set your name and email (see the guide)."; exit 1; }
  echo "      Committed: $msg"
fi

echo "[3/4] Pushing to GitHub..."
git push -u origin HEAD || { echo "PUSH FAILED. Read the lines above. Try: unset GITHUB_TOKEN ; gh auth login -s repo,workflow"; exit 1; }
sha="$(git rev-parse HEAD)"
echo "      Pushed $sha"

echo "[4/4] Waiting for GitHub to start the build..."
id=""
for i in 1 2 3 4 5 6 7 8 9 10 11 12; do
  id="$(gh run list --workflow build-apk.yml --commit "$sha" --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null)"
  [ -n "$id" ] && [ "$id" != "null" ] && break
  sleep 5
done
if [ -z "$id" ] || [ "$id" = "null" ]; then
  echo "      The push did not start a build. Starting one by hand..."
  branch="$(git branch --show-current)"
  gh workflow run build-apk.yml --ref "$branch" || { echo "COULD NOT START THE BUILD. Run: bash scripts/doctor.sh"; exit 1; }
  sleep 10
  id="$(gh run list --workflow build-apk.yml --limit 1 --json databaseId --jq '.[0].databaseId' 2>/dev/null)"
  if [ -z "$id" ] || [ "$id" = "null" ]; then
    echo "Still no build run. Run: bash scripts/doctor.sh"
    exit 1
  fi
fi
echo "      Build started (run $id). This takes about 5 to 12 minutes. Please wait..."

if gh run watch "$id" --interval 10 --exit-status >/dev/null 2>&1; then
  echo "BUILD SUCCEEDED"
  exit 0
fi
echo "BUILD FAILED. The important lines:"
gh run view "$id" --log-failed 2>/dev/null | grep -E "(error:|e: |FAILED|::error|What went wrong|Unresolved|Execution failed|AssertionError|Could not resolve)" | sed -E 's/^[^	]*	[^	]*	//' | head -40
echo "Copy the lines above and send them to Claude."
exit 1
