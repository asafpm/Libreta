#!/usr/bin/env bash
# Full verification used locally and by CI: tests, lint, purity guard, debug APK.
# Extra arguments are passed to Gradle (e.g. scripts/ci.sh --no-daemon).
set -euo pipefail

cd "$(dirname "$0")/.."

java -version 2>&1 | head -1
./gradlew --version | grep -E '^Gradle '

./gradlew check ktlintCheck detekt assembleDebug "$@"
