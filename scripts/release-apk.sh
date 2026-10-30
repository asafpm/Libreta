#!/usr/bin/env bash
# SPDX-License-Identifier: GPL-2.0-or-later
#
# Builds the signed release APK of a tag, for the GitHub release that F-Droid checks its
# own build against (reproducible builds: the recipe's Binaries and AllowedAPKSigningKeys).
#
#   scripts/release-apk.sh v0.1.0      ->  ~/Downloads/libreta-0.1.0.apk
#
# It builds from a fresh clone of the tag, as F-Droid does, so local changes can't get in.
# The key is read from $LIBRETA_KEYSTORE (default ~/.local/share/libreta-signing/
# keystore.properties: storeFile, storePassword, keyAlias, keyPassword).
set -euo pipefail

tag="${1:?usage: scripts/release-apk.sh vX.Y.Z}"
version="${tag#v}"
repo="$(cd "$(dirname "$0")/.." && pwd)"
keystore="${LIBRETA_KEYSTORE:-$HOME/.local/share/libreta-signing/keystore.properties}"
out="${LIBRETA_RELEASE_DIR:-$HOME/Downloads}/libreta-$version.apk"
sdk="${ANDROID_HOME:-$(sed -n 's/^sdk.dir=//p' "$repo/local.properties" 2>/dev/null)}"
[[ -n "$sdk" ]] || { echo "set ANDROID_HOME" >&2; exit 2; }
[[ -r "$keystore" ]] || { echo "no keystore properties at $keystore" >&2; exit 2; }

property() { sed -n "s/^$1=//p" "$keystore"; }

work="$(mktemp -d)"
trap 'rm -rf "$work"' EXIT
git clone -q --depth 1 --branch "$tag" "file://$repo" "$work/libreta" 2>/dev/null
cd "$work/libreta"
# No configuration cache: it would keep the passwords on disk.
ANDROID_HOME="$sdk" ./gradlew -q --no-configuration-cache :android:app:assembleRelease \
    -Pandroid.injected.signing.store.file="$(property storeFile)" \
    -Pandroid.injected.signing.store.password="$(property storePassword)" \
    -Pandroid.injected.signing.key.alias="$(property keyAlias)" \
    -Pandroid.injected.signing.key.password="$(property keyPassword)"

apk=android/app/build/outputs/apk/release/app-release.apk
apksigner="$(ls -d "$sdk"/build-tools/* | sort -V | tail -1)/apksigner"
"$apksigner" verify --print-certs "$apk" | grep -E "certificate SHA-256"
mkdir -p "$(dirname "$out")"
cp "$apk" "$out"
echo "$out"
sha256sum "$out"
