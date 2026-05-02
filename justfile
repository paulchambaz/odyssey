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

# Capture screenshots of all main screens into screenshots/
screenshot:
    #!/usr/bin/env bash
    set -euo pipefail
    mkdir -p screenshots
    dev=$(adb-device)
    w=$(adb -s $dev shell wm size | grep -oP '\d+x\d+' | tail -1 | cut -dx -f1)
    h=$(adb -s $dev shell wm size | grep -oP '\d+x\d+' | tail -1 | cut -dx -f2)
    nav_y=$(( h - h / 16 ))          # bottom nav centre (~94% down)
    nav_lib=$(( w / 6 ))              # Library tab
    nav_books=$(( w / 2 ))            # Books tab
    nav_settings=$(( w * 5 / 6 ))    # Settings tab
    first_book_y=$(( h / 7 ))         # first list item (~14% down, below top bar)
    adb -s $dev shell am start -n xyz.chambaz.odyssey/.MainActivity
    sleep 2
    adb -s $dev exec-out screencap -p > screenshots/1_library.png
    adb -s $dev shell input tap $nav_books $nav_y
    sleep 2
    adb -s $dev exec-out screencap -p > screenshots/2_books.png
    adb -s $dev shell input tap $nav_settings $nav_y
    sleep 2
    adb -s $dev exec-out screencap -p > screenshots/3_settings.png
    adb -s $dev shell input tap $nav_lib $nav_y
    sleep 2
    adb -s $dev shell input tap $(( w / 2 )) $first_book_y
    sleep 2
    adb -s $dev exec-out screencap -p > screenshots/4_player.png
    adb -s $dev shell input keyevent KEYCODE_BACK
    echo "Saved screenshots/1_library.png … 4_player.png"

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
