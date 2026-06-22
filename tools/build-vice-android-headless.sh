#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
VICE_SRC="${VICE_SRC:-$ROOT/VICE-source/vice-3.10}"
NDK="${ANDROID_NDK_HOME:-${ANDROID_NDK_ROOT:-$HOME/Android/Sdk/ndk/28.2.13676358}}"
API="${ANDROID_API:-26}"
BUILD_DIR="${VICE_BUILD_DIR:-$VICE_SRC/build-android-arm64-headless}"
LLVM_BIN="$NDK/toolchains/llvm/prebuilt/linux-x86_64/bin"

if [ ! -x "$LLVM_BIN/aarch64-linux-android${API}-clang" ]; then
  echo "Missing Android clang: $LLVM_BIN/aarch64-linux-android${API}-clang" >&2
  exit 1
fi

mkdir -p "$BUILD_DIR"

export PATH="$LLVM_BIN:/usr/bin:/bin"
export CC="aarch64-linux-android${API}-clang"
export CXX="aarch64-linux-android${API}-clang++"
export CFLAGS="${CFLAGS:--fPIC}"
export CXXFLAGS="${CXXFLAGS:--fPIC}"
export LDFLAGS="${LDFLAGS:--Wl,-z,max-page-size=16384}"
export AR="llvm-ar"
export RANLIB="llvm-ranlib"
export STRIP="llvm-strip"
export PKG_CONFIG="pkg-config"
export PKG_CONFIG_LIBDIR="${PKG_CONFIG_LIBDIR:-/tmp/empty-pkg-config}"

# The release tarball has generated files already; these checks are stricter
# than this Android headless build needs. Use real tools when installed.
export DOS2UNIX="${DOS2UNIX:-true}"
export XA="${XA:-true}"

cd "$BUILD_DIR"
if [ "${VICE_CLEAN:-0}" = "1" ] && [ -f Makefile ]; then
  make clean
fi

"$VICE_SRC/configure" \
  --host=aarch64-linux-android \
  --enable-headlessui \
  --disable-html-docs \
  --disable-pdf-docs \
  --disable-realdevice \
  --disable-rs232 \
  --disable-ipv6 \
  --disable-openmp \
  --without-alsa \
  --without-pulse \
  --without-sdlsound \
  --without-portaudio \
  --without-png \
  --without-gif \
  --without-flac \
  --without-mpg123 \
  --without-vorbis \
  --without-lame \
  --without-libcurl \
  --with-resid \
  --with-fastsid

make -j"${JOBS:-4}"

echo "Built Android arm64 VICE headless binaries:"
file "$BUILD_DIR/src/x64sc"
