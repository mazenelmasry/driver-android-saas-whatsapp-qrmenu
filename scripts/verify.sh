#!/usr/bin/env bash
#
# verify.sh — runs the automatable half of the CLAUDE.md verification protocol
# ("✅ بروتوكول التحقق بعد كل تعديل"): environment sanity, builds, unit tests.
#
# What this script CANNOT verify (and will say so loudly at the end):
#   - real-device smoke test (Samsung S25) — an emulator proves nothing here
#   - background location behaviour (needs the phone in a pocket for 30-60 min)
#   - FCM wake-up / notification delivery
#   - Firebase Phone Auth OTP on a sideloaded APK
#
# This script never commits, pushes, tags, or writes to any database.
# It only builds and tests.

set -euo pipefail

# ---------------------------------------------------------------------------
# 0) argument parsing
# ---------------------------------------------------------------------------
FAST=0
for arg in "$@"; do
  case "$arg" in
    --fast)
      FAST=1
      ;;
    --help|-h)
      cat <<'EOF'
Usage: scripts/verify.sh [--fast]

Runs the automatable steps of the CLAUDE.md verification protocol:
  0) environment sanity (JAVA_HOME, adb presence)
  1) build (both flavors, or just meniura with --fast)
  2) unit tests (app + core:database migration tests + core:network contract mirror)

Flags:
  --fast    Build/test only the meniura flavor, for a quick local iteration loop.
            Do NOT rely on --fast before a commit — the full protocol requires
            both flavors (CLAUDE.md §"بروتوكول التحقق").
  --help    Show this message.

This script does NOT and CANNOT verify:
  - the real-device smoke test (Samsung S25, per CLAUDE.md step 4)
  - background location / battery behaviour
  - FCM push delivery
  - Firebase Phone Auth on a sideloaded build

It never commits, pushes, or touches any database.
EOF
      exit 0
      ;;
    *)
      echo "unknown flag: $arg (see --help)" >&2
      exit 1
      ;;
  esac
done

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

echo "=== 0) environment ==="

# JAVA_HOME: no JDK on PATH on this machine (documented gotcha in CLAUDE.md).
# Without it Gradle exits with no "BUILD" line at all, which is easy to
# misread as success.
DEFAULT_JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"
if [ -z "${JAVA_HOME:-}" ]; then
  if [ -d "$DEFAULT_JAVA_HOME" ]; then
    export JAVA_HOME="$DEFAULT_JAVA_HOME"
    echo "JAVA_HOME not set — defaulting to: $JAVA_HOME"
  else
    echo "❌ JAVA_HOME is not set and the default JDK path does not exist:" >&2
    echo "   $DEFAULT_JAVA_HOME" >&2
    echo "   Set JAVA_HOME to a valid JDK (Android Studio's bundled JBR works)." >&2
    exit 1
  fi
else
  echo "JAVA_HOME already set: $JAVA_HOME"
fi

if [ ! -x "$JAVA_HOME/bin/java" ] && [ ! -x "$JAVA_HOME/bin/java.exe" ]; then
  echo "❌ JAVA_HOME does not point at a usable JDK: $JAVA_HOME" >&2
  exit 1
fi

# adb is optional for this script (no device steps run here), but warn if
# it's missing since CLAUDE.md's step 4 will need it right after this script.
ADB_PATH="${LOCALAPPDATA:-}/Android/Sdk/platform-tools/adb.exe"
if [ -x "$ADB_PATH" ]; then
  echo "adb found: $ADB_PATH (not used by this script — device steps are manual)"
else
  echo "⚠️  adb not found at expected path ($ADB_PATH)."
  echo "   Fine for this script, but you will need it for the real-device step next."
fi

echo
echo "=== 1) build ==="

if [ "$FAST" -eq 1 ]; then
  echo "--fast: building meniura only"
  ./gradlew :app:assembleMeniuraDebug
else
  ./gradlew :app:assembleMeniuraDebug :app:assembleTaajDebug
fi

# Catches Hilt/Dagger wiring errors that don't always surface from
# `assemble` alone (documented gotcha).
if [ "$FAST" -eq 1 ]; then
  ./gradlew :app:hiltJavaCompileMeniuraDebug
else
  ./gradlew :app:hiltJavaCompileMeniuraDebug :app:hiltJavaCompileTaajDebug
fi

echo
echo "=== 2) unit tests ==="

if [ "$FAST" -eq 1 ]; then
  ./gradlew testMeniuraDebugUnitTest
else
  ./gradlew testMeniuraDebugUnitTest testTaajDebugUnitTest
fi

# Room migrations are verified by *running* them against the previous
# schema, not by reading the migration code (documented gotcha) — this
# module's tests do that.
if ./gradlew :core:database:tasks --all 2>/dev/null | grep -q "testMeniuraDebugUnitTest"; then
  ./gradlew :core:database:testMeniuraDebugUnitTest
else
  echo "ℹ️  :core:database has no testMeniuraDebugUnitTest task yet — skipping (module not built yet)."
fi

# Contract mirror test: keeps core/network's copy of openapi/driver.v1.yaml
# in sync. If you touched the OpenAPI spec, regenerate the mirror first
# (see CLAUDE.md step 2) or this will correctly fail.
if ./gradlew :core:network:tasks --all 2>/dev/null | grep -q "testMeniuraDebugUnitTest"; then
  ./gradlew :core:network:testMeniuraDebugUnitTest
else
  echo "ℹ️  :core:network has no testMeniuraDebugUnitTest task yet — skipping (module not built yet)."
fi

echo
echo "=== ✅ automatable checks passed ==="
echo
echo "This script proves NOTHING about the following — they are NOT optional,"
echo "they are just not automatable, and CLAUDE.md forbids committing without them:"
echo
echo "  ❗ Real-device smoke test on the Samsung S25 (an emulator proves nothing here)."
echo "     See CLAUDE.md step 4: install, launch, logcat for FATAL/ANR, screenshot"
echo "     in both light and dark mode, and — for the current milestone — a full"
echo "     login → online → real order → offer → accept → picked-up → delivered run."
echo "  ❗ Background location behaviour: device in a pocket, app 'online', for"
echo "     30–60 minutes, then check last_seen_at. No shortcut exists for this."
echo "  ❗ FCM push delivery and full-screen intent behaviour on a real device."
echo "  ❗ Firebase Phone Auth OTP on a sideloaded (non-Play) build."
echo "  ❗ If you touched the backend: 'php artisan test --compact --filter=Driver'"
echo "     and 'vendor/bin/pint app tests database routes lang config --dirty'"
echo "     in the backend repo (this script does not run PHP)."
echo
echo "Do not report this run as 'everything verified' — only the build and unit"
echo "tests were verified. Do not commit, push, or update CLAUDE.md until the"
echo "device steps above are also done."
