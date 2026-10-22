#!/usr/bin/env bash
# Checks the usual reasons why a build does not start or fails.
# Run from the project folder:  bash scripts/doctor.sh
problems=0
ok()  { echo "OK       $1"; }
bad() { echo "PROBLEM  $1"; echo "         -> $2"; problems=$((problems+1)); }

[ -f settings.gradle.kts ] && ok "project files are in this folder" \
  || bad "settings.gradle.kts is not here" "You are in the wrong folder, or the files sit inside a sub-folder. Type: ls   then: cd FOLDERNAME  (the folder that shows settings.gradle.kts)"
[ -f .github/workflows/build-apk.yml ] && ok "workflow file is in the right place" \
  || bad ".github/workflows/build-apk.yml is missing" "The hidden .github folder was not copied. Run: cp -r /tmp/hpf/HTMLPlayForge/. .   (see the guide, Part 1)"
[ -f app/build.gradle.kts ] && ok "app/build.gradle.kts found" || bad "app/build.gradle.kts is missing" "The zip was not unpacked completely. Unpack it again."

if git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  ok "this folder is a git repo (branch: $(git branch --show-current))"
  remote="$(git remote get-url origin 2>/dev/null)"
  case "$remote" in
    *asifur41/HTMLPlayForge*) ok "remote is asifur41/HTMLPlayForge" ;;
    *) bad "remote is '$remote'" "Expected a repo named asifur41/HTMLPlayForge." ;;
  esac
  if git ls-files | grep -qE '\.jks$|\.keystore$'; then
    bad "a key file (.jks) is inside git" "Run: git rm --cached release.jks   then: bash ci.sh \"remove key\". Then make a NEW key (the old one is no longer private)."
  else
    ok "no key file inside git"
  fi
else
  bad "this folder is not a git repo" "Open your repo in Codespaces and put the files there."
fi

if gh auth status >/dev/null 2>&1; then
  ok "gh is logged in"
  secrets="$(gh secret list 2>/dev/null | awk '{print $1}')"
  for s in KEYSTORE_BASE64 KEYSTORE_PASSWORD KEY_ALIAS KEY_PASSWORD; do
    echo "$secrets" | grep -qx "$s" && ok "secret $s exists" \
      || bad "secret $s is missing" "See the guide, Part 4."
  done
  echo
  echo "Last builds:"
  gh run list --limit 3 2>/dev/null || echo "(no builds yet)"
else
  bad "gh is not logged in" "Type: gh auth login   and answer the questions."
fi

[ -f release.jks ] && ok "release.jks is here (keep a copy on your phone!)" \
  || echo "NOTE     release.jks is not in this folder. That is fine if you made it already and the secrets are set."

echo
if [ "$problems" = "0" ]; then echo "No problems found."; else echo "$problems problem(s) found. Fix the lines marked PROBLEM, then run: bash ci.sh \"retry\""; fi
exit 0
