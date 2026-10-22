# HTMLPlayForge quick notes

The full step-by-step process (with "what you should see", "what you should not see" and "how to fix")
was given to you in the chat. This file is only a short reminder.

1. Unpack the zip into your repo (the hidden `.github` folder must be in the repo root).
2. `bash scripts/verify_repo.sh` -> must print VERIFIED.
3. Make the signing key once (`keytool ...`) and set the four secrets:
   KEYSTORE_BASE64, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD.
4. `bash scripts/doctor.sh` -> checks folder, workflow file, login, secrets and shows the last builds.
5. `bash ci.sh "message"` -> commit, push, wait, print BUILD SUCCEEDED or the error lines.
6. Release: `git tag v1.0.0` then `git push origin v1.0.0` (tag must equal versionName in app/build.gradle.kts).

Links to replace later (utils/LinkOpener.kt): COMMUNITY and REVIEW.
Keep a safe copy of release.jks. Never put it in git or in a chat.
