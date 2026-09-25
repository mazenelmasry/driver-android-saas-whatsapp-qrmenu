#!/usr/bin/env bash
#
# build-release.sh — builds the Play-ready, UPLOAD-KEY-signed App Bundles for
# both brands (and matching APKs for direct install / testing).
#
# The signing secrets never live in this repo. They are read from a file
# outside it (default: ~/driver-signing/signing.env) that exports
# RELEASE_STORE_FILE / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS /
# RELEASE_KEY_PASSWORD. They are passed per invocation — NOT put in
# ~/.gradle/gradle.properties, because the POS app reads the same names.
#
# Usage: scripts/build-release.sh [meniura|taaj|all]   (default: all)

set -euo pipefail
cd "$(dirname "$0")/.."

SIGNING_ENV="${DRIVER_SIGNING_ENV:-$HOME/driver-signing/signing.env}"
if [[ ! -f "$SIGNING_ENV" ]]; then
  echo "❌ Signing file not found: $SIGNING_ENV" >&2
  exit 1
fi
# shellcheck disable=SC1090
source "$SIGNING_ENV"

: "${JAVA_HOME:=/c/Program Files/Android/Android Studio/jbr}"
export JAVA_HOME

case "${1:-all}" in
  meniura) FLAVORS=(Meniura) ;;
  taaj)    FLAVORS=(Taaj) ;;
  all)     FLAVORS=(Meniura Taaj) ;;
  *) echo "usage: $0 [meniura|taaj|all]" >&2; exit 2 ;;
esac

TASKS=()
for f in "${FLAVORS[@]}"; do
  TASKS+=(":app:bundle${f}Release" ":app:assemble${f}Release")
done

./gradlew "${TASKS[@]}" --no-build-cache

echo
echo "=== Artifacts ==="
find app/build/outputs/bundle app/build/outputs/apk -path '*release*' \( -name '*.aab' -o -name '*.apk' \) -print
