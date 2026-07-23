#!/usr/bin/env bash
# Copies the Swift checkout core into ios/DodoCore/ so the pod bundles it.
#
# RN ships iOS native code via CocoaPods, which stops accepting updates in
# Dec 2026, so the Swift source is vendored into this package rather than pulled
# as a remote dependency. At release this runs against the tagged iOS SDK; for
# local development it copies the sibling ../../swift folder.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="${1:-$HERE/../swift/Sources/DodoCheckout}"
DEST="$HERE/ios/DodoCore"

if [ ! -d "$SRC" ]; then
  echo "Swift core not found at: $SRC" >&2
  exit 1
fi

rm -rf "$DEST"
mkdir -p "$DEST"
cp "$SRC"/*.swift "$DEST"/
echo "Copied $(ls "$DEST"/*.swift | wc -l | tr -d ' ') Swift files into ios/DodoCore/"
