# Build TreasureNova BEP20 Address Manager

Requirements: JDK 17 or 21, Android SDK platform 35, build-tools 35.0.0.

```bash
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=$HOME/android-sdk
yes | "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$ANDROID_HOME" "platforms;android-35" "build-tools;35.0.0"
cd TreasureNovaBep20
chmod +x ./gradlew
./gradlew :app:assembleDebug :app:testDebugUnitTest
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

The app is read-only. It logs into https://treasurenova.net/ with credentials you enter, reads the visible USDT BEP20 deposit address, and logs out. It does not withdraw, transfer, trade, or pay. If the site shows 2FA, finish it on the page and tap Continue. If a security, legal, or regional block appears, that account stops and the message is shown.
