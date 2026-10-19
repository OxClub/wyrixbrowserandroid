#!/usr/bin/env bash
# The Gradle wrapper JAR could not be generated/verified offline when this project was authored.
# With any Gradle >= 8.9 installed, run this once, then commit gradle/wrapper/gradle-wrapper.jar
# and the gradlew scripts. Verify the JAR checksum against https://gradle.org/release-checksums/.
set -euo pipefail
cd "$(dirname "$0")/.."
command -v gradle >/dev/null || { echo "Install Gradle first (e.g. via SDKMAN or your package manager)."; exit 1; }
gradle wrapper --gradle-version 8.9 --distribution-type bin
echo "Done. Verify checksum of gradle/wrapper/gradle-wrapper.jar before committing."
