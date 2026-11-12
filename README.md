# LiquidBoard Glass AI — Android IME Project

Generated from **LiquidBoard Studio** with your customized Dynamic Liquid Glass parameters:
- Preset: `liquid-glass`
- Glass Opacity: `0.34`
- Blur Radius: `20dp`
- Specular Rim: `0.68`
- Corner Radius: `11dp`
- Default Live Translation Target: `Spanish`

## Build the Android APK

1. Open this folder in **Android Studio Ladybug (or newer)** with JDK 17.
2. Update `AI_PROXY_BASE_URL` in `app/build.gradle.kts` to point to your deployed LiquidBoard server URL.
3. Run Gradle to generate the installable APK:
   ```bash
   ./gradlew assembleDebug
   ```
4. Install onto a connected Android device or emulator:
   ```bash
   adb install app/build/outputs/apk/debug/app-debug.apk
   ```
5. Enable **LiquidBoard Glass AI** under **Settings > System > Keyboard > On-screen keyboard**.
