#!/usr/bin/env sh
set -eu

ROOT=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
SDK="${ANDROID_HOME:-$HOME/Android/Sdk}"
BUILD_TOOLS="${BUILD_TOOLS:-$SDK/build-tools/36.1.0}"
PLATFORM="${PLATFORM:-$(find "$SDK/platforms" -maxdepth 2 -name android.jar | sort -V | tail -n 1)}"
BUILD="$ROOT/build"

mkdir -p "$BUILD/classes" "$BUILD/dex"

"$BUILD_TOOLS/aapt2" compile --dir "$ROOT/res" -o "$BUILD/resources.zip"
"$BUILD_TOOLS/aapt2" link \
  -I "$PLATFORM" \
  --manifest "$ROOT/AndroidManifest.xml" \
  -o "$BUILD/agent-code-buddy-unsigned.apk" \
  "$BUILD/resources.zip" \
  --java "$BUILD/generated"

javac \
  -source 8 -target 8 \
  -bootclasspath "$PLATFORM" \
  -d "$BUILD/classes" \
  $(find "$BUILD/generated" "$ROOT/src" -name '*.java' | sort)

"$BUILD_TOOLS/d8" \
  --lib "$PLATFORM" \
  --output "$BUILD/dex" \
  $(find "$BUILD/classes" -name '*.class' | sort)

cp "$BUILD/agent-code-buddy-unsigned.apk" "$BUILD/agent-code-buddy-dex.apk"
cd "$BUILD/dex"
zip -q -u "$BUILD/agent-code-buddy-dex.apk" classes.dex

cd "$ROOT"
"$BUILD_TOOLS/zipalign" -f 4 "$BUILD/agent-code-buddy-dex.apk" "$BUILD/agent-code-buddy.apk"

if [ ! -f "$BUILD/debug.keystore" ]; then
  keytool -genkeypair \
    -keystore "$BUILD/debug.keystore" \
    -storepass android \
    -keypass android \
    -alias androiddebugkey \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

"$BUILD_TOOLS/apksigner" sign \
  --ks "$BUILD/debug.keystore" \
  --ks-pass pass:android \
  --key-pass pass:android \
  --out "$BUILD/agent-code-buddy-signed.apk" \
  "$BUILD/agent-code-buddy.apk"

echo "$BUILD/agent-code-buddy-signed.apk"
