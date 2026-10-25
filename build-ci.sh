#!/bin/sh
# Compila el launcher en Linux (lo usa GitHub Actions). No necesita Gradle ni Android Studio.
# Usa el Android SDK que ya trae el servidor de GitHub (variable ANDROID_HOME).
set -e
cd "$(dirname "$0")"

SDK="${ANDROID_HOME:-${ANDROID_SDK_ROOT:?Falta ANDROID_HOME}}"

# Última versión de build-tools y de plataforma instaladas.
BT_DIR="$SDK/build-tools/$(ls "$SDK/build-tools" | sort -V | tail -1)"
PLATFORM="$(ls "$SDK/platforms" | sed 's/android-//' | grep -E '^[0-9]+$' | sort -n | tail -1)"
JAR="$SDK/platforms/android-$PLATFORM/android.jar"
echo "build-tools: $BT_DIR"
echo "plataforma:  android-$PLATFORM"

rm -rf build && mkdir -p build/gen build/classes build/dex

"$BT_DIR/aapt2" compile --dir res -o build/res.zip
"$BT_DIR/aapt2" link -I "$JAR" --manifest AndroidManifest.xml \
    --min-sdk-version 25 --target-sdk-version 28 \
    --java build/gen -o build/unsigned.apk build/res.zip

javac -nowarn --release 8 -encoding UTF-8 \
    -classpath "$JAR" -d build/classes \
    $(find src build/gen -name '*.java')

"$BT_DIR/d8" --min-api 25 --lib "$JAR" --output build/dex $(find build/classes -name '*.class')

(cd build/dex && jar uf ../unsigned.apk classes.dex)
"$BT_DIR/zipalign" -f 4 build/unsigned.apk build/aligned.apk

# Llave de firma de prueba (cualquier APK instalado por USB debe estar firmado).
KS=build/debug.keystore
keytool -genkeypair -keystore "$KS" -storepass android -keypass android \
    -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US" >/dev/null 2>&1

"$BT_DIR/apksigner" sign --ks "$KS" --ks-pass pass:android \
    --out build/CarLauncher.apk build/aligned.apk
"$BT_DIR/apksigner" verify build/CarLauncher.apk

echo "APK listo: build/CarLauncher.apk"
