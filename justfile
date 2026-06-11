# Install and launch the app
run: install
    adb -s $(adb-device) shell am start -n xyz.chambaz.odyssey/.MainActivity

# Compile a debug APK
build:
    ./gradlew assembleDebug ${AAPT2:+-Pandroid.aapt2FromMavenOverride=$AAPT2}

# Compile a release APK
release:
    ./gradlew assembleRelease ${AAPT2:+-Pandroid.aapt2FromMavenOverride=$AAPT2}

# Install and launch a release build
deploy: release
    adb -s $(adb-device) install -r app/build/outputs/apk/release/app-release.apk
    adb -s $(adb-device) shell am start -n xyz.chambaz.odyssey/.MainActivity

# Push debug APK to a connected device
install:
    ./gradlew installDebug ${AAPT2:+-Pandroid.aapt2FromMavenOverride=$AAPT2}

# Uninstall the app from a connected device
uninstall:
    adb -s $(adb-device) uninstall xyz.chambaz.odyssey

# Delete all build outputs
clean:
    ./gradlew clean

# Stream device logs filtered to this app
log:
    adb -s $(adb-device) logcat | grep xyz.chambaz.odyssey

# List connected devices
devices:
    adb devices

# Pair wirelessly via QR — phone: Wireless Debugging → Pair with QR code
pair:
    adb-pair

# Connect to saved device
connect:
    adb-device

# Run unit tests
test:
    ./gradlew testDebugUnitTest ${AAPT2:+-Pandroid.aapt2FromMavenOverride=$AAPT2}

# Capture a screenshot directly from device
screenshot dest:
    adb -s $(adb-device) exec-out screencap -p > {{dest}}

# Bump version across all files (versionCode derived from semver)
bump version:
    #!/usr/bin/env bash
    set -euo pipefail
    current_code=$(grep 'versionCode = ' app/build.gradle.kts | grep -o '[0-9]\+')
    new_code=$((current_code + 1))
    sed -i 's/versionName = "[0-9]\+\.[0-9]\+\.[0-9]\+"/versionName = "{{version}}"/' app/build.gradle.kts
    sed -i "s/versionCode = [0-9]\+/versionCode = $new_code/" app/build.gradle.kts
    sed -i "s/versionName: [0-9]\+\.[0-9]\+\.[0-9]\+/versionName: {{version}}/" xyz.chambaz.odyssey.yml
    sed -i "s/versionCode: [0-9]\+/versionCode: $new_code/" xyz.chambaz.odyssey.yml
    sed -i "s/commit: v[0-9]\+\.[0-9]\+\.[0-9]\+/commit: v{{version}}/" xyz.chambaz.odyssey.yml
    sed -i "s/CurrentVersion: [0-9]\+\.[0-9]\+\.[0-9]\+/CurrentVersion: {{version}}/" xyz.chambaz.odyssey.yml
    sed -i "s/CurrentVersionCode: [0-9]\+/CurrentVersionCode: $new_code/" xyz.chambaz.odyssey.yml

# Tag a release and publish APK to GitHub
publish tag notes: release
  git add app/build.gradle.kts xyz.chambaz.odyssey.yml
  git commit -m "chore: bump version to {{tag}}"
  git push origin master
  git tag {{tag}}
  git push origin {{tag}}
  cp app/build/outputs/apk/release/app-release*.apk odyssey-{{tag}}.apk
  gh release create {{tag}} odyssey-{{tag}}.apk \
    --title "{{tag}}" \
    --notes "{{notes}}"
  rm odyssey-{{tag}}.apk

# Prepare fastlane metadata images for publishing
metadata:
  mkdir -p fastlane/metadata/android/en-US/images/phoneScreenshots
  cp assets/home.png     fastlane/metadata/android/en-US/images/phoneScreenshots/1_home.png
  cp assets/drawer.png   fastlane/metadata/android/en-US/images/phoneScreenshots/2_drawer.png
  cp assets/settings.png fastlane/metadata/android/en-US/images/phoneScreenshots/3_settings.png
  magick -background none assets/icon.svg -resize 512x512 fastlane/metadata/android/en-US/images/icon.png

# create signing key
signkey:
  keytool -genkey -v -keystore ~/.android/odyssey-release.jks -alias odyssey -keyalg EC -keysize 256 -validity 10000 -dname "CN=Paul Chambaz, O=Odyssey, C=FR"
