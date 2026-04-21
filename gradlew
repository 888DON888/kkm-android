#!/bin/sh
# Gradle wrapper script — auto-download Gradle on first run
GRADLE_WRAPPER_JAR="$(dirname "$0")/gradle/wrapper/gradle-wrapper.jar"
exec java -jar "$GRADLE_WRAPPER_JAR" "$@"
