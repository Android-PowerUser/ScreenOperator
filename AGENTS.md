# Agent Instructions

This document provides instructions for setting up the environment and building the Android application.

## Automated Setup Script

The following script will set up the Android SDK, install the necessary packages, and build the application. It is designed to be run in a fresh environment.

```bash
#!/bin/bash

# Exit on any error
set -e

# 1. Set up the Android SDK directory
echo "Setting up Android SDK directory..."
mkdir -p android_sdk
echo "sdk.dir=android_sdk" > local.properties

# Add android_sdk to .gitignore if it's not already there
if ! grep -q "android_sdk/" .gitignore; then
  echo "android_sdk/" >> .gitignore
fi

# 2. Download and unzip the Android SDK command-line tools
echo "Downloading and unzipping Android SDK..."
wget -q https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip
unzip -q commandlinetools-linux-13114758_latest.zip -d android_sdk
rm commandlinetools-linux-13114758_latest.zip

# 3. Restructure cmdline-tools for sdkmanager
echo "Restructuring cmdline-tools..."
mkdir -p android_sdk/cmdline-tools/latest
mv android_sdk/cmdline-tools/* android_sdk/cmdline-tools/latest 2>/dev/null || true

# 4. Install required SDK packages
echo "Installing SDK packages..."
yes | android_sdk/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null
android_sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"
echo "Setup complete."
```

## Manual Setup Instructions

If you prefer to set up the environment manually, follow these steps:

1.  **Create `android_sdk` directory:** `mkdir android_sdk`
2.  **Create `local.properties`:** `echo "sdk.dir=android_sdk" > local.properties`
3.  **Download and Unzip SDK:**
    *   Download from `https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip`
    *   Unzip into `android_sdk`
4.  **Restructure `cmdline-tools`:**
    *   `mkdir -p android_sdk/cmdline-tools/latest`
    *   `mv android_sdk/cmdline-tools/* android_sdk/cmdline-tools/latest`
5.  **Install SDK packages:**
    *   `yes | android_sdk/cmdline-tools/latest/bin/sdkmanager --licenses > /dev/null`
    *   `android_sdk/cmdline-tools/latest/bin/sdkmanager "platforms;android-35" "build-tools;35.0.0" "platform-tools"`

## Gradle Performance

The `gradle.properties` file has been optimized for performance to avoid timeouts. The following settings have been applied:

```properties
org.gradle.jvmargs=-Xmx4g
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true
```

## Delivering the APK

After building the application, you can deliver the signed APK file by committing it to a new branch.

**Note:** Committing binary files to a Git repository is generally discouraged as it can significantly increase the repository size. This method should be considered a workaround. For a more standard and sustainable approach, consider using [GitHub Releases](https://docs.github.com/en/repositories/releasing-projects-on-github/managing-releases-in-a-repository) to distribute application binaries.

The following steps outline how to build, sign, and deliver the APK via a Git branch.

### 1. Build the Unsigned APK

echo "Building the application..."
./gradlew assembleRelease
The final artifact will be located at `app/build/outputs/apk/release/app-release-unsigned.apk`.

### 2. Generate a Test Signing Key
Create a new debug keystore and key to sign the application.
```bash
keytool -genkey -v \
  -keystore debug.keystore \
  -storepass android \
  -alias androiddebugkey \
  -keypass android \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```
This will create a `debug.keystore` file in the root directory.

### 3. Sign the APK
Use the `apksigner` tool from the Android SDK to sign the unsigned APK with the key you just created.
```bash
android_sdk/build-tools/35.0.0/apksigner sign \
  --ks debug.keystore \
  --ks-pass pass:android \
  --out app-release-signed.apk \
  app/build/outputs/apk/release/app-release-unsigned.apk
```
This command creates a new signed APK named `app-release-signed.apk`.

### 4. Commit the Signed APK to a New Branch

Create a new branch for the APK delivery
git checkout -b apk-delivery

Add the APK to the new branch, forcing if it's in .gitignore
git add -f app-release.apk

Commit the APK
git commit -m "feat: Add built APK"

The user can then fetch and checkout this branch to get the file.
```
