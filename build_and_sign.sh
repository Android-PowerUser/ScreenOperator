#!/bin/bash
set -e
echo "INFO: Starting build and sign process."

# 1. Set up the Android SDK directory
echo "INFO: Setting up Android SDK directory..."
mkdir -p android_sdk
echo "sdk.dir=android_sdk" > local.properties

# Add android_sdk to .gitignore if it's not already there
if ! grep -q "android_sdk/" .gitignore; then
  echo "INFO: Adding android_sdk/ to .gitignore."
  echo "android_sdk/" >> .gitignore
fi

# 2. Download and unzip the Android SDK command-line tools
echo "INFO: Downloading Android SDK..."
wget -q https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q commandlinetools-linux-13114758_latest.zip -d android_sdk
rm commandlinetools-linux-13114758_latest.zip

# 3. Restructure cmdline-tools for sdkmanager
echo "INFO: Restructuring cmdline-tools..."
mkdir -p android_sdk/cmdline-tools/latest
mv android_sdk/cmdline-tools/* android_sdk/cmdline-tools/latest/ 2>/dev/null || true

# 4. Install required SDK packages
echo "INFO: Installing SDK packages..."
yes | android_sdk/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null
android_sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools" > /dev/null

# 5. Build the Unsigned APK
echo "INFO: Building the application..."
./gradlew assembleRelease

# 6. Generate a Test Signing Key
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

# 7. Sign the APK
echo "INFO: Signing the APK..."
android_sdk/build-tools/35.0.0/apksigner sign \
  --ks debug.keystore \
  --ks-pass pass:android \
  --out app-release-signed.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk

echo "INFO: Build and sign process complete."
