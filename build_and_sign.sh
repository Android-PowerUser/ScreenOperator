#!/bin/bash
set -e
echo "INFO: Starting build and sign process."
# Android SDK is already there

# 1. Build the Unsigned APK
echo "INFO: Building the application..."
./gradlew assembleRelease

# 2. Generate a Test Signing Key
if [ -f debug.keystore ]; then
  echo "INFO: debug.keystore already exists. Skipping generation."
else
  echo "INFO: Generating test signing key..."
  keytool -genkey -v \
    -keystore debug.keystore \
    -storepass android \
    -alias androiddebugkey \
    -keypass android \
    -keyalg RSA \
    -keysize 2048 \
    -validity 10000 \
    -dname "CN=Android Debug,O=Android,C=US"
fi

# 3. Sign the APK
echo "INFO: Signing the APK..."
android_sdk/build-tools/35.0.0/apksigner sign \
  --ks debug.keystore \
  --ks-pass pass:android \
  --out app-release-signed.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk

echo "INFO: Build and sign process complete."
