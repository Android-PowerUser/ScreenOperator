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

# 5. Build the application
echo "Building the application..."
./gradlew assembleRelease

echo "Setup complete. The application has been built successfully."
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
6.  **Build the app:** `./gradlew assembleRelease`

## Gradle Performance

The `gradle.properties` file has been optimized for performance to avoid timeouts. The following settings have been applied:

```properties
org.gradle.jvmargs=-Xmx4g
org.gradle.parallel=true
org.gradle.caching=true
org.gradle.configureondemand=true
```
