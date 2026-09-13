#!/usr/bin/env sh
# VuraVision CI bootstrap. Uses the official wrapper JAR when present; otherwise a Gradle installed by CI setup-gradle.
set -eu
APP_HOME=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
WRAPPER="$APP_HOME/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WRAPPER" ]; then
  exec java -classpath "$WRAPPER" org.gradle.wrapper.GradleWrapperMain "$@"
fi
if command -v gradle >/dev/null 2>&1; then
  exec gradle "$@"
fi
echo "Gradle wrapper JAR is not present and no Gradle executable is on PATH." >&2
echo "GitHub Actions installs Gradle 8.13 before invoking this script." >&2
exit 1
