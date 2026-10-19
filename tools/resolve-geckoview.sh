#!/usr/bin/env bash
# Resolves the newest GeckoView release from the official Mozilla Maven and writes it into
# gradle.properties (wyrix.geckoview.version / wyrix.geckoview.artifact).
# Requires network. Usage: tools/resolve-geckoview.sh [--force] [--print]
set -euo pipefail
cd "$(dirname "$0")/.."
FORCE=0; PRINT=0
for a in "$@"; do case "$a" in --force) FORCE=1;; --print) PRINT=1;; esac; done

current="$(grep -E '^wyrix\.geckoview\.version=' gradle.properties | cut -d= -f2- | tr -d '[:space:]')"
if [ -n "$current" ] && [ "$FORCE" -eq 0 ] && [ "$PRINT" -eq 0 ]; then
  echo "GeckoView version already pinned to $current (use --force to re-resolve)."; exit 0
fi

BASE="https://maven.mozilla.org/maven2/org/mozilla/geckoview"
found_artifact=""; found_version=""
for artifact in geckoview-omni geckoview; do
  if xml="$(curl -fsSL --retry 3 --max-time 60 "$BASE/$artifact/maven-metadata.xml" 2>/dev/null)"; then
    v="$(printf '%s' "$xml" | grep -o '<version>[^<]*</version>' | sed 's/<[^>]*>//g' \
         | grep -E '^[0-9]+\.[0-9]+\.[0-9]{8,}$' | sort -V | tail -n1 || true)"
    if [ -n "$v" ]; then found_artifact="$artifact"; found_version="$v"; break; fi
  fi
done
if [ -z "$found_version" ]; then
  echo "ERROR: could not resolve a GeckoView release from $BASE (network blocked or layout changed)." >&2
  echo "Set wyrix.geckoview.version manually; browse $BASE/ to find artifact names and versions." >&2
  exit 1
fi
echo "Resolved $found_artifact:$found_version"
[ "$PRINT" -eq 1 ] && exit 0
sed -i "s|^wyrix\.geckoview\.artifact=.*|wyrix.geckoview.artifact=$found_artifact|" gradle.properties
sed -i "s|^wyrix\.geckoview\.version=.*|wyrix.geckoview.version=$found_version|" gradle.properties
echo "Updated gradle.properties"
