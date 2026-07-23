#!/usr/bin/env bash
# Copies the Swift checkout core into ios/DodoCheckoutCore/ so the pod bundles it.
#
# CocoaPods doesn't reliably resolve source_files globs that escape the
# podspec's own directory through the plugin symlink Flutter installs
# (../../swift/Sources/DodoCheckout/*.swift silently matched zero files) —
# vendoring a local copy, same as react-native/scripts/sync-ios-core.sh, is
# the fix. At release this runs against the tagged iOS SDK; for local
# development it copies the sibling ../../swift folder.
set -euo pipefail

HERE="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SRC="${1:-$HERE/../swift/Sources/DodoCheckout}"
DEST="$HERE/ios/DodoCheckoutCore"

if [ ! -d "$SRC" ]; then
  echo "Swift core not found at: $SRC" >&2
  exit 1
fi

rm -rf "$DEST"
mkdir -p "$DEST"
cp "$SRC"/*.swift "$DEST"/
echo "Copied $(ls "$DEST"/*.swift | wc -l | tr -d ' ') Swift files into ios/DodoCheckoutCore/"
