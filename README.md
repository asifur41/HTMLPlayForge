# HTMLPlayForge

An offline HTML, CSS and JavaScript editor with live preview for Android.

- Package: `com.htmlplayforge.app`, version 1.0.1, Android 8.0 and newer
- No permissions, no internet access, no trackers, no ads
- Signed release APK, not minified (the code is readable)
- Built only by GitHub Actions (`.github/workflows/build-apk.yml`)

Read `GUIDE.md` first. It tells you, step by step, how to build and publish the app from your phone.

## What you can check yourself
Every release has four files: the APK, its `.sha256`, `TRUST_REPORT.md` and `SIGNING_CERT.txt`.
`TRUST_REPORT.md` lists permissions (none), tracker libraries found (none), web addresses in the code,
native files (none) and the libraries used. The build fails if any of these rules is broken.
These are facts, not a promise that the app has no bugs.

## Folders
- `app/src/main/java/com/htmlplayforge/app` - the Kotlin code
  - `editor/` pure logic (colouring, formatter, lint, Emmet, undo, find). No Android classes, so it has unit tests.
  - `preview/` preview rules (path handler, security rules, console, hints)
  - `files/` projects, ZIP, import and export
  - `ui/` screens and views, `settings/` themes, fonts, settings, `utils/` helpers
- `app/src/main/assets/samples` - 15 sample projects
- `scripts/` - `fetch_fonts.sh`, `trust_report.sh`, `verify_repo.sh`, `make_hashes.sh`
- `ci.sh` - commit, push, wait for the build
