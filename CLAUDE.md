# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project status: Phase 0 scaffolded

**Phase 0 is done and pushed** — the Compose/Hilt project skeleton exists (single Activity + NavHost with empty Home/Drawer/Themes/Settings; launches to an empty Home). Next up is **Phase 1** (become a real launcher). [`FocusLauncher Plan.md`](FocusLauncher Plan.md) is the authoritative spec; build strictly phase by phase, in order — later phases assume the earlier Room entities and the icon-resolution chain already exist.

**No Android toolchain on this machine** (no JDK, Gradle, Android SDK, or Android Studio — it's a Python-focused box). The scaffold was authored as source files but **cannot be compiled or run here**; build/verify happens in Android Studio (which brings its own JBR + SDK). Don't try to run `./gradlew` or `adb` locally — they aren't installed. The Gradle **wrapper jar** is intentionally absent (network was blocked at authoring time); Android Studio regenerates it on first sync (see README).

Before starting implementation-heavy work, load the Android skills: `android-dev` (baseline router) plus the specific ones per task — `compose`, `android-data-layer` (Room), `datastore`, `koin`/Hilt, `android-retrofit`, `kotlin-flows`, `kotlin-coroutines`, `coil-compose`, `android-testing`, `android-ux`.

## What this is

**Focus Launcher** — a minimal Android home launcher whose signature feature is **AI-generated icon themes**: the user picks or describes a style and the whole home screen is re-skinned in one coherent visual language. The launcher exists so the app controls icon rendering end-to-end and never depends on other launchers honoring a theme. The launcher is the delivery vehicle; **the icon-theme engine is the moat.**

## Tech stack (from the plan — not yet installed)

Kotlin · Jetpack Compose (entire UI: home + drawer) · min SDK **26** (adaptive icons) · Room · DataStore (Preferences) · Coil · Coroutines + Flow · Hilt (recommended past Phase 2) · Retrofit + OkHttp + kotlinx.serialization for the theme-generation server call. Backend generation server is out of scope to build here; the app talks to it over a REST contract (Phase 5). API keys live on the server, never in the app.

## Architecture (the big picture)

Three layers, top to bottom: **Presentation (Compose)** → **Domain (plain-Kotlin use-cases)** → **Data**. The Data layer holds `AppRepository` (PackageManager + `LauncherApps`), `ThemeRepository` (Room + file cache), `IconEngine` (mask/render + cache), and `GenerationClient` (Retrofit → server).

**The golden rule — internalize this before touching icon code:** the UI never decides how an icon is drawn. It calls `ResolveIconForApp(packageName)` and gets back a drawable or nothing. `IconEngine.resolve` reads the global `iconDisplayMode` **first**:

- `TEXT_ONLY` → returns nothing; home/drawer render the label alone (strict-minimalist look). Layouts must look intentional with icons absent — don't leave a gap.
- `ORIGINAL` → returns the app's own unmodified icon.
- `THEMED` → runs the full chain: (1) active theme's generated icon for this package → (2) the app's own icon masked/normalized into the theme's shape → (3) a generated fallback from the theme's style tokens.

The masked fallback (chain step 2) is a **safety net that must always look acceptable**, because there will always be an app no theme was generated for. It is not an afterthought.

A **theme is a spec, not a folder of images** (see plan §7 for the versioned `styleSpecJson` schema: palette, shape/mask, stroke, surface, motif, iconTreatment). Any icon — including a brand-new app installed tomorrow — must conform to the spec. Built-in (no-AI) themes and AI themes render through the **same** path; only the source of the per-icon result differs.

## Data model (Room — build these entities first, everything hangs off them)

`AppEntity` (packageName PK; label, isFavorite, isHidden, isDistracting, userSortIndex) · `AppLimitEntity` (packageName; dailyLimitMinutes, scheduleJson, enforcementMode enum, enabled) · `ThemeEntity` (id PK; styleSpecJson, isActive, source enum) · `IconEntity` (id PK; themeId FK, packageName, filePath, status enum READY/PENDING/FALLBACK). Exactly one active `ThemeEntity`; many `IconEntity` per theme (one per app).

DAOs **expose Flows** so Compose recomposes live as background icon generation flips rows PENDING → READY.

`iconDisplayMode` (TEXT_ONLY / THEMED / ORIGINAL) lives in **DataStore, not Room** — it's a single app-wide preference the icon engine short-circuits on.

## Build phases (each ends in something runnable — don't skip)

- **Phase 0** — scaffold Compose project, add deps, Hilt, single Activity, NavHost with empty Home/Drawer/Themes/Settings. *Done: launches to empty Home.*
- **Phase 1** — become a real launcher: add `HOME` + `DEFAULT` intent categories, handle home-button behavior, prompt to set default via `RoleManager` `ROLE_HOME`. *Done: OS offers it as home and it survives the home button.*
- **Phase 2** — app list + drawer via `LauncherApps`; favorites/clock/search; long-press to favorite/hide/mark-distracting; launch via `LauncherApps.startMainActivity`. *Done: usable as a daily launcher (raw icons fine).*
- **Phase 3** — icon rendering pipeline, **no AI**: implement `IconEngine.resolve` chain steps 2 & 3 (mask real icons to a shape at 108dp with 72dp safe zone; composite adaptive fg/bg then mask; cache as files; `IconEntity` status FALLBACK). Ship 2–3 built-in token-only themes. Wire the display-mode switch. *This phase is the real de-risking — if the launcher looks cohesive with token-only restyling + masking, the Phase-5 AI is upside, not a dependency.*
- **Phase 4** — minimal aesthetic (tasteful wallpapers, not pure black) + focus friction for `isDistracting` apps (an interstitial/short delay, **launcher-surface only — no Accessibility/Device Admin**).
- **Phase 4.5** — app timers/blocking (**read the plan's honest constraints**): ship `UsageStatsManager` observation + timers + warnings first (light `PACKAGE_USAGE_STATS` permission); add Accessibility-based hard block only as explicit opt-in gated behind `enforcementMode = BLOCK_AFTER_LIMIT` (heavy Play Store review bar).
- **Phase 5** — AI theme generation: `ThemePicker` sends style description + app labels to the server; stream results into `IconEntity` PENDING → READY; home updates live via Flow with Phase-3 fallbacks shown meanwhile.
- **Phase 6** — polish: theme editing, per-app icon override, local backup/restore, perf pass.

**First milestone to aim for:** Phase 2 + one built-in Phase-3 theme. That alone is a differentiated launcher and proves the engine before any AI call.

## Hard constraints — do not overpromise (plan §8)

- **Coherent set generation is the whole game.** One nice icon is easy; 60 sibling icons is the hard problem. Mitigation = the small, opinionated style spec + strong server-side constraints. Prototype a handful of apps before generating a full set.
- **Enforcement is "block/redirect," never "log out."** Android sandboxing means no API can end another app's session or sign you out of Instagram. UI copy must be honest about this.
- **No `appfilter.xml` / ADW internally.** Because the app draws its own home screen, theming needs no special OS permission and maps generated icons to package names in its own DB. ADW export for *other* launchers is explicitly deferred.
- **No public/cross-user leaderboard** (plan §10). Build self-comparison insights stored locally in Room only; a ranked board contradicts the product and is a dark pattern. Every surfaced metric must pass: *does seeing this help the user act, or just make them feel judged?*
- Adaptive-icon compositing has device quirks; default-launcher UX differs across OEMs (Samsung/Xiaomi/Pixel) — test on more than one skin.

## Commands (run in Android Studio or a box with the Android toolchain — not this machine)

Standard Gradle Android project. First open in Android Studio to generate the wrapper jar. On Windows use the `gradlew.bat` wrapper (the Bash tool can use `./gradlew`):

- Build debug APK: `./gradlew assembleDebug`
- Install on connected device/emulator: `./gradlew installDebug`
- Unit tests: `./gradlew test` — a single class: `./gradlew test --tests "com.example.FooTest"`
- Instrumented tests: `./gradlew connectedAndroidTest`
- Lint: `./gradlew lint`
- Logcat while debugging the launcher: `adb logcat`

## Repository / progress tracking

This `Launcher` folder is now its own git repo (`git init` here), separate from the surrounding home-directory repo. `origin` is `https://github.com/pragarocks/FocusLauncher.git` and `main` tracks it — the Phase 0 scaffold is pushed. Commit phase by phase. **Do not** confuse this with the home-directory repo that points at `second-you-life-simulator.git`. Note this folder lives under a **OneDrive** path, so avoid letting OneDrive sync mid-build corrupt `.gradle/`/`build/` (both are gitignored).
