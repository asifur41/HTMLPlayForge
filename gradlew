#!/bin/sh
# HTMLPlayForge launcher.
# The binary gradle-wrapper.jar cannot be created offline, so this script
# uses it when present and otherwise falls back to the "gradle" command
# (the GitHub Actions workflow installs Gradle 8.5).
DIR="$(cd "$(dirname "$0")" && pwd)"
JAR="$DIR/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$JAR" ]; then
  exec java -classpath "$JAR" org.gradle.wrapper.GradleWrapperMain "$@"
fi
exec gradle "$@"
