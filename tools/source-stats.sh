#!/usr/bin/env bash
# Accurate source statistics. Counts physical lines (wc -l), non-generated files only.
set -euo pipefail
cd "$(dirname "$0")/.."
count() { # $1 label, rest: find args
  label="$1"; shift
  files=$(find "$@" -type f 2>/dev/null | grep -Ev '/(build|\.gradle|\.git)/' || true)
  if [ -z "$files" ]; then printf '%-28s files=%4d lines=%6d\n' "$label" 0 0; return; fi
  n=$(printf '%s\n' "$files" | wc -l | tr -d ' ')
  l=$(printf '%s\n' "$files" | xargs cat | wc -l | tr -d ' ')
  printf '%-28s files=%4d lines=%6d\n' "$label" "$n" "$l"
}
count "Kotlin (main)"       app/src/main -name '*.kt'
count "Kotlin (unit tests)" app/src/test -name '*.kt'
count "Kotlin (instrumented)" app/src/androidTest -name '*.kt'
count "Android XML resources" app/src/main/res app/src/main/AndroidManifest.xml -name '*.xml'
count "Gradle/properties/toml" . -maxdepth 3 \( -name '*.gradle.kts' -o -name 'gradle.properties' -o -name '*.toml' -o -name 'gradle-wrapper.properties' \)
count "CI workflows"        .github -name '*.yml'
count "Shell tooling"       tools -name '*.sh'
count "Documentation"       . -maxdepth 2 -name '*.md'
