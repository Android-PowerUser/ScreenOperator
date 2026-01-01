# Agent Instructions

After each successfully executed programming instruction, the app must be built and submitted in the following way:

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
