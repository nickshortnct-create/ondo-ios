# Ondo for iPhone

Ondo is Nick's music player for his Navidrome server: a Kotlin Multiplatform + Compose fork of [Navic](https://github.com/ssalggnikool/Navic) (GPL-3.0). This repo is a snapshot of Ondo `ios-testflight` (from 1.0.82) for building the iPhone app. The Android app is maintained elsewhere.

The iPhone build has **no downloaders** (`mediaAcquisitionEnabled = false` in `composeApp/src/iosMain/.../StoreBuild.ios.kt`). It only plays what's already in Navidrome. That keeps it clear of App Store guideline 5.2.3.

## Build (macOS)

1. JDK 21 and Xcode 16+.
2. Open `iosApp/iosApp.xcodeproj` and set the signing team (`TEAM_ID` / Signing & Capabilities).
3. Build or Archive the `iosApp` scheme. Its build phase runs `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode`.

Bundle id `com.nickshortnct.ondo`, version from `iosApp/Configuration/Config.xcconfig`.

Kotlin-only check (also works on Linux): `./gradlew :composeApp:compileKotlinIosArm64 -Pkotlin.native.enableKlibsCrossCompilation=true`
