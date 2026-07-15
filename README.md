# Focus Launcher

A minimal Android home launcher whose signature feature is **AI-generated icon themes**:
pick or describe a style, and the whole home screen is re-skinned in one coherent visual
language. See [`FocusLauncher Plan.md`](FocusLauncher%20Plan.md) for the full product/technical
plan and [`CLAUDE.md`](CLAUDE.md) for the architecture summary and build phases.

## Status — Phase 0 (project skeleton)

Scaffolded: Kotlin + Jetpack Compose single-Activity app, Hilt, and a Compose `NavHost`
with empty **Home / Drawer / Themes / Settings** destinations. It launches to an empty Home.
Nothing launcher-specific yet (Phase 1 adds the `HOME` intent categories).

> This scaffold was authored on a machine with **no Android toolchain installed** and could
> not be compiled/run here. First build happens in Android Studio (see below). Dependency
> versions are pinned in [`gradle/libs.versions.toml`](gradle/libs.versions.toml); let Android
> Studio's sync bump anything it flags.

## Prerequisites

- **Android Studio** (latest stable) — brings its own JDK (JBR 17+) and the SDK manager.
- **Android SDK: compileSdk/targetSdk 35**, **minSdk 26**.
- A device/emulator on API 26+.

## First-time setup — generate the Gradle wrapper jar

`gradle/wrapper/gradle-wrapper.jar` is **not committed** (it's a binary that couldn't be
fetched in the authoring environment). Do **one** of the following once, then it's cached:

- **Android Studio:** just open this folder. On first Gradle sync it downloads Gradle
  8.11.1 (per `gradle-wrapper.properties`) and generates the wrapper jar automatically.
- **CLI (if Gradle is installed):** run `gradle wrapper --gradle-version 8.11.1`.

## Build / run

Once the wrapper jar exists (Windows uses `gradlew.bat`; the commands below use the POSIX form):

```bash
./gradlew assembleDebug          # build debug APK
./gradlew installDebug           # install on a connected device/emulator
./gradlew test                   # JVM unit tests
./gradlew connectedAndroidTest   # instrumented tests (device/emulator required)
./gradlew lint                   # Android lint
```

Or use Android Studio's **Run** button with the `app` configuration.

## Progress tracking

Tracked at **https://github.com/pragarocks/FocusLauncher**. This folder is its own git repo
(not the surrounding home-directory repo). Build strictly phase by phase per the plan — each
phase ends in something runnable.
