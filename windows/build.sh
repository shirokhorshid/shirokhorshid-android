#!/usr/bin/env bash
#
# Build the Shir o Khorshid Windows client.
#
# Everything is cross-compiled from Linux/macOS with the Zig toolchain, which
# ships both a C compiler that targets Windows and a resource compiler, so no
# Windows machine or MSVC install is needed.
#
#   ./build.sh              build everything into windows/build/
#   ./build.sh --clean      remove build artefacts first
#
# Environment (see tools/gen_values.py):
#   PSIPHON_PROPAGATION_CHANNEL_ID   (required)
#   PSIPHON_SPONSOR_ID               (required)
#   PSIPHON_EMBEDDED_SERVER_LIST_FILE / ..._LIST
#
set -euo pipefail

cd "$(dirname "$0")"
ROOT="$(pwd)"
BUILD="$ROOT/build"
STAGE="$BUILD/stage"
TARGET="x86_64-windows-gnu"

ZIG="${ZIG:-zig}"
PYTHON="${PYTHON:-python3}"

say() { printf '\033[1;36m==> %s\033[0m\n' "$*"; }
die() { printf '\033[1;31merror: %s\033[0m\n' "$*" >&2; exit 1; }

if [[ "${1:-}" == "--clean" ]]; then
    say "cleaning build directory"
    rm -rf "$BUILD"
fi

mkdir -p "$BUILD" "$STAGE"

# ---------------------------------------------------------------- zig
if ! command -v "$ZIG" >/dev/null 2>&1; then
    say "installing the Zig toolchain (cross-compiler) from PyPI"
    "$PYTHON" -m pip install --quiet --break-system-packages ziglang \
        || "$PYTHON" -m pip install --quiet ziglang \
        || "$PYTHON" -m pip install --quiet --break-system-packages --user ziglang \
        || true
    ZIG="$("$PYTHON" -c 'import ziglang,os;print(os.path.join(os.path.dirname(ziglang.__file__),"zig"))')"
fi
if ! "$ZIG" version >/dev/null 2>&1; then
    die "the Zig toolchain is unavailable - install it with: $PYTHON -m pip install ziglang"
fi
say "using $("$ZIG" version)"

# ---------------------------------------------------------------- icons
if [[ ! -f res/app.ico ]]; then
    say "generating application icon"
    "$PYTHON" tools/make_icon.py ../app/src/ic_launcher-playstore.png res/app.ico
fi
"$PYTHON" tools/make_tray_icons.py res

# ------------------------------------------------------------- values.json
say "generating values.json"
GEN_ARGS=""
if [[ -n "${SHIR_ALLOW_EMPTY:-}" ]]; then GEN_ARGS="--allow-empty"; fi
if ! "$PYTHON" tools/gen_values.py "$STAGE/values.json" $GEN_ARGS; then
    die "set PSIPHON_PROPAGATION_CHANNEL_ID and PSIPHON_SPONSOR_ID (same values the APK build uses)"
fi

# ------------------------------------------------------------- tunnel core
say "obtaining psiphon-tunnel-core.exe"
if ! "$PYTHON" tools/fetch_core.py "$STAGE"; then
    die "could not obtain psiphon-tunnel-core.exe"
fi

# ---------------------------------------------------------------- compile
LIBS="-luser32 -lgdi32 -lcomctl32 -lshell32 -lole32 -loleaut32 -luuid \
-ladvapi32 -lwininet -lshlwapi -lcomdlg32 -lws2_32 -liphlpapi -lgdiplus"
CFLAGS="-std=c11 -O2 -municode -Wall -Wno-unused-parameter "

say "compiling ShirOKhorshid.exe"
"$ZIG" cc -target $TARGET -municode $CFLAGS \
    -Wl,/subsystem:windows \
    -o "$STAGE/ShirOKhorshid.exe" \
    src/json.c src/core.c src/tunnel.c src/i18n.c src/ui.c src/win_main.c \
    res/app.rc \
    $LIBS

say "compiling the installer"
"$ZIG" cc -target $TARGET -municode $CFLAGS \
    -Wl,/subsystem:windows \
    -o "$BUILD/installer-base.exe" \
    src/installer.c res/app.rc \
    $LIBS

# --------------------------------------------------------------- packaging
say "packaging"
rm -f "$STAGE"/*.pdb
cp res/app.ico "$STAGE/app.ico"
cp ../LICENSE "$STAGE/LICENSE.txt" 2>/dev/null || true
cat > "$STAGE/README.txt" <<'EOF'
Shir o Khorshid for Windows
===========================

An unofficial, independently maintained build of the Psiphon open-source
client.  NOT developed, endorsed, sponsored, or affiliated with Psiphon Inc.
in any way.  For the official Psiphon application, visit https://psiphon.ca

Licensed under the GNU General Public License v3.0 (see LICENSE.txt).

Files
-----
  ShirOKhorshid.exe         the application
  psiphon-tunnel-core.exe   the Psiphon tunnel engine (ConsoleClient)
  values.json               build-time configuration (channel/sponsor ids)
  app.ico                   application icon

Uninstalling
------------
  Use "Apps & features" in Windows Settings, or run:
      ShirOKhorshid.exe --uninstall

THIS SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND.
EOF

"$PYTHON" tools/make_installer.py "$BUILD/installer-base.exe" "$STAGE" \
    "$BUILD/ShirOKhorshid-Setup.exe"

say "done"
ls -la "$BUILD"/ShirOKhorshid-Setup.exe "$STAGE"/ShirOKhorshid.exe \
       "$STAGE"/psiphon-tunnel-core.exe
