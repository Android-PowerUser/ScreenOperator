# Agent Instructions

Rules and Notes

1. Do not perform any pre-commit steps except request code review and record learning content.
2. The code review must always happen before building the application.
3. The project does not contain a test suite in app/src.
4. Complete tasks using a heuristic approach
5. Always plan how you will do everything in one command. Bunching commands (using &&) and issuing multiple tool calls per response to reduce round-trips.
6. When executing long-running tasks like builds, provide progress updates every two minutes via `ntfy.sh/Jules` including an estimated percentage completion for both the specific operation and the overall task.
7. If I report "Joules encountered an error while working on a task. How did this happen?", your first reaction should be to write down in the memories what you did last, i.e., what must have caused it. Also write it down in the chat and confirm to me that you have done so. Then avoid triggering the error.
8. Don't just insert sample code. The app should be ready for production. Research the things you need to find out to make this happen.
9. Write your pull request name on ntfy so I can verify it as yours.
10. Stop checking the files in a loop when Joules encounters an error, but clean up the old pull request so that the new one works.
11. Always run builds and other heavy operations in the background (via tmux) to remain operational and continue executing other commands during the build process.
12. The 'Jules encountered an error' message was triggered by a stalled workflow where the assistant entered an infinite loop of redundant verification steps and tool calls during the final submission phase.
13. Renaming the release apk for the pull request is not necessary. Just put it in.
14. Memories is not a log, but is for critical information that you will need again later.
15. Never stop working or take a break unless you have a question. Otherwise, keep working until the task is successfully completed.



After successful programming, the app must be built and submitted in the following way:

If you are not Jules, then you will need to install the Android SDK first:

```
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
```

### 1. Build the Unsigned APK

To bypass shell timeouts (~400-700s) and prevent background process termination during long-running tasks like Android builds, use `tmux send-keys -t default 'command' Enter` in the persistent 'default' session; monitor with `tmux capture-pane -t default -p`.

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

### 4. Delivering the APK

After building the application, you must deliver the signed APK file by committing it to a new branch.

Since there is probably already an APK in the root directory, you need to make sure that you update it with the pull request.

Create a new branch for the APK delivery
git checkout -b apk-delivery

Add the APK to the new branch, forcing if it's in .gitignore
git add -f app-release.apk

Commit the APK
git commit -m "feat: Add built APK"

The user can then fetch and checkout this branch to get the file.
