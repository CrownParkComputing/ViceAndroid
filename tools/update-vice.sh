#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
ZIBRI="$ROOT/VICE"
ANDROID="$ROOT/VICEAndroid"
VICE_SOURCE_ROOT="${VICE_SOURCE_ROOT:-$ROOT/VICE-source}"
VICE_VERSION="${VICE_VERSION:-3.10}"
TARBALL_URL="${VICE_TARBALL_URL:-https://sourceforge.net/projects/vice-emu/files/releases/vice-${VICE_VERSION}.tar.gz/download}"
NATIVE_DIR="$VICE_SOURCE_ROOT"
DOWNLOAD_DIR="$NATIVE_DIR/downloads"
TARBALL="$DOWNLOAD_DIR/vice-${VICE_VERSION}.tar.gz"
SOURCE_DIR="$NATIVE_DIR/vice-${VICE_VERSION}"

if [ -d "$ZIBRI/.git" ]; then
  git -C "$ZIBRI" fetch --tags origin
  git -C "$ZIBRI" pull --ff-only origin main
  echo "Latest local Zibri tags:"
  git -C "$ZIBRI" tag --sort=-v:refname | awk 'NR <= 12 { print }'
else
  git clone https://github.com/Zibri/VICE/ "$ZIBRI"
fi

mkdir -p "$NATIVE_DIR"

mkdir -p "$DOWNLOAD_DIR"

if [ ! -f "$TARBALL" ]; then
  echo "Downloading official VICE source tarball:"
  echo "  $TARBALL_URL"
  curl -L --fail -o "$TARBALL" "$TARBALL_URL"
else
  echo "Using existing tarball: $TARBALL"
fi

if [ ! -d "$SOURCE_DIR" ]; then
  echo "Extracting $TARBALL into $NATIVE_DIR"
  tar -xzf "$TARBALL" -C "$NATIVE_DIR"
else
  echo "Using existing source directory: $SOURCE_DIR"
fi

{
  echo "version=$VICE_VERSION"
  echo "tarball=$TARBALL"
  echo "url=$TARBALL_URL"
  echo "source=$SOURCE_DIR"
} > "$NATIVE_DIR/current-source.txt"

echo "VICE source ready: $SOURCE_DIR"
