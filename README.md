# Clean

A personal, offline, native Android workout tracker. It reads and writes the Strong CSV export byte for byte, keeps records per exercise variation, and has profiles, templates, programs, a body figure with balance, fatigue and strength, measurements and progress photos. It never asks for the internet permission (the build fails if a library adds it).

## Repository layout

- `core/`: plain Kotlin, no Android. CSV parsing and writing, the workout model, PRs, stats, the exercise library. Unit tested.
- `app/`: the Android app (Jetpack Compose, Material 3, Room). Package `clean.app`, minSdk 26.
- `docs/`: the data formats (`data-format.md`, `other-formats.md`), the training rules (`domain.md`, `left-right.md`) and the design language (`design.md`).
- `tools/`: scripts that generate committed data (body figure outlines, exercise text, icons, sounds).

Not in git, on purpose: `original/` (personal training data and a reference APK), `dist/` (built APKs), signing keys, `local.properties`.

## Building

Needs a JDK 21 or newer (Android Studio's bundled one works) and the Android SDK.

```bash
./gradlew :core:test            # unit tests
./gradlew :app:assembleRelease  # APKs in app/build/outputs/apk/release/
./gradlew :app:installDebug     # build and install on a connected phone or emulator
```

The release build is optimised and signed with the machine's debug key (`~/.android/debug.keystore`). Android only updates an installed app from an APK signed with the same key, so keep building on the same key; installing a differently signed APK means uninstalling first, which deletes the app's data.

## On-device tests

The round trip through the real database runs on a phone or emulator and skips itself unless a Strong export has been copied into the app's private folder by hand (it is personal data, so it is never in the repository):

```bash
./gradlew :app:installDebug :app:installDebugAndroidTest
adb shell mkdir -p /data/local/tmp/o && adb push original/strong*.csv /data/local/tmp/o/
adb shell "run-as clean.app sh -c 'mkdir -p files/original && cp /data/local/tmp/o/* files/original/'" && adb shell rm -r /data/local/tmp/o
adb shell am instrument -w clean.app.test/androidx.test.runner.AndroidJUnitRunner
```

Run it this way, not with `connectedAndroidTest`, which uninstalls the app afterwards and wipes its data. The Robolectric screen and timing tests need `ROBOLECTRIC_DEPS` set to a folder holding Robolectric's Android jars, on a path with no spaces; without it they are left out of the run.

## Which APK

Each build makes four APKs:

| File | For | versionCode |
|---|---|---|
| `app-arm64-v8a-release.apk` | almost every phone from the last ten years | base x 10 + 2 |
| `app-armeabi-v7a-release.apk` | older 32-bit phones | base x 10 + 1 |
| `app-x86_64-release.apk` | emulators and Chromebooks | base x 10 + 3 |
| `app-universal-release.apk` | any device, slightly larger | base x 10 + 0 |

The base is `VERSION_CODE` at the top of `app/build.gradle.kts`; raise it by one for each APK you hand out. Stay with one kind of APK on a device: Android refuses to install a lower versionCode over a higher one without an uninstall.

## GitHub Actions

`.github/workflows/build.yml` runs the core tests and builds the release APKs on every push and pull request, and uploads them as the `clean-apks` artifact of the run. To make those APKs install over the app already on the phone, add a repository secret `DEBUG_KEYSTORE_BASE64` holding the base64 of the same `debug.keystore`; without it they are signed with a throwaway key.

## iOS (later)

Not built. The plan is to make `core/` Kotlin Multiplatform first, then the screens with Compose Multiplatform, with widgets and Live Activities in SwiftUI, installed by sideloading. An `iosApp/` folder would sit beside `app/`; the `.gitignore` already leaves out Xcode's per-user and build files.

## Licence

Clean's own code is under the MIT licence (`LICENSE`). Third-party material (MuscleMap outlines, exercise text, Inter) is credited in `NOTICE.md`.
