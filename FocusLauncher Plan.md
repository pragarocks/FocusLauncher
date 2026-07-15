# Focus Launcher + AI Icon Themes — Product & Technical Plan

> A minimal Android home launcher whose signature feature is **AI-generated icon
> themes**: pick or describe a style, and the whole home screen is re-skinned in one
> coherent visual language. The launcher exists so we control icon rendering
> end-to-end and never depend on other launchers accepting our theme.

This document is written to be handed to Claude Code. Build it phase by phase, in
order. Each phase ends in something runnable. Do not skip ahead — later phases
assume the earlier data models exist.

---

## 1. The one-line thesis

Existing minimal launchers are deliberately joyless (black screen, monospace list).
Existing icon-theming is either hand-drawn packs that never cover your newest apps,
or Google's AI icons locked to Pixel + a few preset styles. **Nobody ships a minimal
launcher whose icons are AI-themed on any phone.** That combination is the product.

The launcher is not the moat — the icon-theme engine is. The launcher is what makes
the engine deliverable, because a launcher renders its own home screen and therefore
does not need any other app to honor the theme.

---

## 2. Scope: what we build vs. explicitly defer

**In scope (v1):**
- A working home launcher (replaces the home screen).
- App drawer / app list, search, favorites.
- Minimal aesthetic with *tasteful* wallpapers (not dead black).
- Icon theme engine: apply a generated theme across all app icons the launcher draws.
- **Icon display modes** (user choice): (a) **Text-only** — no icons at all, just app
  names, like a strict minimalist launcher; (b) **Themed icons** — our generated or
  built-in theme; (c) **Original icons** — the apps' own unmodified icons. This is one
  setting that flips how the icon engine resolves everything.
- Gentle focus friction (make distracting apps inconvenient, not impossible).
- App timers / usage limits (see the dedicated enforcement track in §6.5 — buildable,
  but read the honest constraints there first).
- Local persistence of settings, themes, and the icon cache.

**Deferred (note but don't build yet):**
- Selling/exporting themes as standard ADW icon packs for *other* launchers. (Possible
  later, but it drags in the whole `appfilter.xml` compatibility mess. Skip for v1.)
- Device Admin / parental-control style *hard locks* a user can't dismiss. (App timers
  and blocking ARE in v1 — see §6.5 — but the un-dismissable, password-protected
  parental-control tier is deferred.)
- Cloud sync of themes across devices.
- On-device image generation. v1 calls a server; on-device generation is a research
  spike, not a v1 dependency.

**Hard constraint to respect throughout:** because *we* draw the home screen, icon
theming needs **no** special OS permission and does **not** use the `appfilter.xml`
ADW standard internally. We map generated icons to app package names in our own DB.
The ADW standard is only relevant if we later export packs for other launchers.

---

## 3. Tech stack

- **Language:** Kotlin.
- **UI:** Jetpack Compose (the whole launcher, including home + drawer).
- **Min SDK:** 26 (adaptive icons landed here). Target the latest stable SDK.
- **Local DB:** Room.
- **Settings/prefs:** DataStore (Preferences).
- **Image loading/caching:** Coil.
- **Async:** Kotlin Coroutines + Flow.
- **DI:** Hilt (optional but recommended once past Phase 2).
- **Networking (theme engine):** Retrofit + OkHttp + kotlinx.serialization.
- **Backend for generation:** a thin server (out of scope to build here, but the app
  talks to it over a documented REST contract — see Phase 5). Keep the API key on the
  server, never in the app.

---

## 4. Architecture overview

```
+-------------------------------------------------------------+
|  Presentation (Compose)                                     |
|   HomeScreen  ·  AppDrawer  ·  ThemePicker  ·  Settings      |
+-------------------------------------------------------------+
|  Domain (use-cases, plain Kotlin)                           |
|   GetInstalledApps · ApplyTheme · GenerateTheme ·           |
|   ResolveIconForApp · SetFavorite · FocusRules              |
+-------------------------------------------------------------+
|  Data                                                       |
|   AppRepository (PackageManager + LauncherApps)             |
|   ThemeRepository (Room + file cache)                       |
|   IconEngine (mask/render + cache)                          |
|   GenerationClient (Retrofit -> our server)                 |
+-------------------------------------------------------------+
```

Golden rule: the home screen asks `ResolveIconForApp(packageName)` and gets back a
drawable (or nothing). The resolver checks `iconDisplayMode` first: if TEXT_ONLY it
returns no drawable and the UI renders the label alone; if ORIGINAL it returns the
app's own unmodified icon. Only in THEMED mode does it run the chain: (1) active
theme's generated icon for this package, (2) a masked/normalized version of the app's
own icon in the theme's shape, (3) a generated fallback from the theme's style tokens.
The UI never knows or cares which path produced the result.

---

## 5. Data model (Room)

Build these entities first; everything hangs off them.

- `AppEntity`: packageName (PK), activityName, label, isFavorite, isHidden,
  isDistracting (bool, for focus friction), userSortIndex.
- `AppLimitEntity`: packageName (PK/FK), dailyLimitMinutes (nullable),
  scheduleJson (nullable — e.g. "blocked 9pm–7am"), enforcementMode (enum:
  FRICTION_ONLY, BLOCK_AFTER_LIMIT), enabled. Drives the §6.5 enforcement track.
- `ThemeEntity`: id (PK), name, styleSpecJson (the style tokens — see §7),
  createdAt, isActive, source (enum: BUILT_IN, GENERATED, USER_EDITED).
- `IconEntity`: id (PK), themeId (FK), packageName, filePath (cached PNG/vector),
  status (enum: READY, PENDING, FALLBACK), generatedAt.
- Relationships: one active `ThemeEntity`; many `IconEntity` per theme (one per app).

**Global icon display mode** lives in DataStore (not Room), since it's a single
app-wide preference: `iconDisplayMode` (enum: TEXT_ONLY, THEMED, ORIGINAL). The icon
engine reads this first — in TEXT_ONLY it short-circuits and draws nothing but the
label.

DAOs expose Flows so Compose recomposes when icons finish generating in the
background.

---

## 6. Build phases (each is independently runnable)

### Phase 0 — Project skeleton
- New Compose project, Kotlin, min SDK 26.
- Add dependencies (Compose, Room, DataStore, Coil, Coroutines, Retrofit,
  kotlinx.serialization, Hilt).
- Set up Hilt, a single Activity, and a Compose NavHost with empty Home / Drawer /
  Themes / Settings destinations.
- **Done when:** app launches to an empty Home composable.

### Phase 1 — Become a real launcher
- In `AndroidManifest.xml`, add to the main activity's intent filter:
  `android.intent.category.HOME` and `android.intent.category.DEFAULT`.
- Handle being the home app: don't finish on back press to home; behave as the
  home surface.
- Prompt the user to set us as default launcher (open the appropriate settings
  intent; on modern Android use `RoleManager` with `ROLE_HOME`).
- **Done when:** the OS offers this app as a home screen option and it survives a
  press of the home button.

### Phase 2 — App list + drawer (the functional core)
- Use `LauncherApps` (preferred) / `PackageManager` to enumerate launchable
  activities. Populate `AppEntity` rows; refresh on install/uninstall broadcasts.
- Home: favorites + clock + search entry, minimal type-forward layout.
- Drawer: scrollable/searchable list, launch apps via `LauncherApps.startMainActivity`.
- Long-press an app → mark favorite / hide / mark distracting.
- **Done when:** you can actually use this as your daily launcher (no theming yet —
  raw app icons are fine here).

### Phase 3 — Icon rendering pipeline (no AI yet)
- Implement `IconEngine.resolve(packageName)` with the fallback chain from §4, but
  for now only paths (2) and (3): take the app's real icon and normalize it to a
  chosen **shape mask** (circle / squircle / rounded-square) at 108dp with the
  72dp safe zone respected.
- Support adaptive icons: composite foreground + background layers, then mask.
- Cache results as files; store `IconEntity` rows with status FALLBACK.
- Ship 2–3 **built-in** themes defined purely as style tokens (see §7): e.g.
  "Mono Line" (monochrome outline), "Soft Clay", "Flat Pastel". These prove the
  engine re-skins the whole screen coherently *without any AI at all*.
- Wire the **icon display mode** switch (TEXT_ONLY / THEMED / ORIGINAL) in Settings.
  `IconEngine.resolve` reads the mode first: TEXT_ONLY returns nothing (home + drawer
  render labels only, the strict-minimalist look); ORIGINAL returns the app's own
  unmodified icon; THEMED runs the full resolve chain. Home and drawer layouts must
  look intentional with icons absent — don't just leave a gap where the icon was.
- **Done when:** switching a built-in theme visibly restyles every home icon
  consistently; unknown/newly-installed apps still get a coherent masked icon; and
  toggling to Text-only gives a clean names-only launcher with no icons anywhere.

> This phase is the real de-risking. If the launcher looks cohesive with only
> token-driven restyling + masking, the AI in Phase 5 is upside, not a dependency.

### Phase 4 — Minimal aesthetic + focus friction
- Wallpaper: bundle a small set of tasteful minimal wallpapers + support the
  system wallpaper. Avoid pure black as the only option (that's the thing users
  dislike about existing minimal launchers).
- Focus friction for apps flagged `isDistracting`: a short interstitial ("Open
  Instagram? You marked this distracting.") with a brief delay or a confirm tap.
  This is *friction*, using only our own launcher surface — **no** Accessibility or
  Device Admin. Be explicit in code comments that hard blocking is out of scope.
- **Done when:** distracting apps require a deliberate extra step; wallpapers feel
  intentional.

### Phase 4.5 — App timers & blocking (the enforcement track — read carefully)

This is what you meant by "log us out of Instagram/Facebook/YouTube." An honest
framing first, because it determines what's buildable:

**What a launcher genuinely cannot do:** it cannot log you out of Instagram or end
another app's session. Android sandboxes every app from every other app — there is no
API to reach into another app and sign it out. Any product that "limits Instagram"
does it by **detecting** that Instagram is in the foreground and **covering or
redirecting** it — not by touching Instagram itself.

**The two real mechanisms (pick per feature):**

1. **UsageStatsManager (lighter permission).** With the `PACKAGE_USAGE_STATS`
   permission (user grants it in a special settings screen), the app reads how long
   each app has been used today. Good for: showing usage, warning as a limit
   approaches, and triggering friction. It's *observational* — it tells you time is
   up but doesn't forcibly stop the app on its own.
2. **Accessibility Service (heavier, higher review bar).** A foreground-app watcher
   that, when a limited app opens past its limit, launches a full-screen blocking
   overlay / sends the user back home. This is how strict blockers actually *enforce*.
   **Cost:** Google scrutinizes Accessibility use heavily and rejects apps that use it
   for blocking without clear justification and disclosure. Plan for a careful Play
   Store submission (privacy policy, prominent disclosure, possibly a declaration
   form). Consider distributing this tier via direct APK / F-Droid as a fallback.

**Recommended v1 build order for this track:**
- Start with UsageStatsManager only: a per-app daily timer (`AppLimitEntity`), a live
  usage readout, and a warning + friction interstitial as the limit nears. This ships
  cleanly with no Accessibility risk.
- Add the Accessibility-based hard block as an **opt-in** feature the user explicitly
  enables, clearly explained. Gate it behind `enforcementMode = BLOCK_AFTER_LIMIT`.
- Schedules (e.g. "no social apps 9pm–7am") reuse the same machinery.
- Frame every blocking moment as *the user's own rule*, not a punishment: "You set a
  30-min limit on Instagram. Time's up for today." with an optional "add 5 min" escape
  so it's a self-control aid, not a cage (the un-dismissable parental-control version
  is deferred — see §2).

**Done when:** the user can set a daily limit on an app, see time remaining, and get
blocked/redirected when it's exceeded — with the enforcement level matching the
permission they chose to grant.

> Wellbeing note to keep in the product's voice: this is a self-control tool. Limits
> should be firm but not shaming, and always escapable by the user's own deliberate
> choice. Don't design dark patterns that trap someone.

### Phase 5 — AI theme generation (the signature feature)
- Define the **style spec** contract (§7) as the single source of truth a theme is
  generated from. This is what makes a *set* coherent instead of 40 unrelated images.
- `ThemePicker` UI: choose a preset style OR describe one in text ("frosted glass,
  muted teal, thin strokes"). Send description + the list of app labels/categories to
  our server.
- Server responsibilities (documented contract, not built here):
  - Turn the prompt into a locked style spec (palette, stroke, corner, grain, motif).
  - Generate one icon per app *constrained to that spec* so they match. Return either
    finished raster icons or vector/style params the app renders locally.
  - Enforce a consistent silhouette/subject per app (e.g. camera app = lens motif).
- App side: stream results into `IconEntity` rows with status PENDING → READY; the
  home screen updates icons live via Flow as they arrive. Unfinished apps show the
  Phase-3 masked fallback in the meantime, so the screen is never broken.
- **Done when:** a user picks/describes a style and, within a reasonable wait, the
  whole home screen converges to a coherent AI-generated set, on any device.

### Phase 6 — Polish
- Theme editing (tweak palette/shape after generation, regenerate a single icon).
- Per-app manual icon override.
- Backup/restore themes locally (JSON + cached images).
- Performance pass: icon cache eviction, cold-start time, memory.

---

## 7. The style spec (the actual IP)

A theme is NOT a folder of images. It is a spec that *any* icon must conform to, so a
brand-new app added tomorrow can be themed to match. Model it as JSON, versioned:

```jsonc
{
  "version": 1,
  "name": "Frosted Teal",
  "palette": { "bg": "#0E2F2A", "fg": "#8FE3CE", "accent": "#DFF7EF" },
  "shape": { "mask": "squircle", "cornerPct": 42 },
  "stroke": { "style": "thin-line", "widthDp": 2.0 },
  "surface": { "fill": "flat", "grain": "none", "shadow": "none" },
  "motif": "single-glyph-centered",
  "iconTreatment": "monochrome-glyph-on-tinted-bg"
}
```

The generator (built-in themes locally, AI themes on the server) consumes this spec
and produces per-app icons that all share it. Keep the spec small and opinionated —
the fewer free axes, the more coherent the set. This spec is also what makes built-in
(no-AI) themes and AI themes go through the *same* rendering path.

---

## 8. Known hard parts (call these out to Claude Code so it doesn't overpromise)

1. **Coherent set generation is the whole game.** Generating one nice icon is easy;
   generating 60 that look like siblings is the hard research problem. The style spec
   + strong server-side constraints are the mitigation. Prototype with a handful of
   apps before generating a full set.
2. **Icon coverage for unknown apps.** There will always be an app the theme wasn't
   generated for. The masked fallback (Phase 3) must always look acceptable — it's
   the safety net, not an afterthought.
3. **Adaptive icon compositing** has device quirks. Test on multiple shapes/densities.
4. **Default-launcher UX** differs across OEMs (Samsung One UI, Xiaomi, Pixel). Test
   the "set as default" flow on more than one skin.
5. **Enforcement is "block/redirect," never "log out."** No API can end another
   app's session. Be honest in UI copy: the app blocks or bounces you out, it doesn't
   sign you out of Instagram. And keep Accessibility-based blocking as an explicit
   opt-in tier (§6.5) — it raises the Play Store review bar, so basic friction and
   UsageStats timers ship first and independently.
6. **Cost/latency of generation.** A full set is many image generations. Cache
   aggressively; generate lazily (favorites first, rest in background).

---

## 9. First working milestone to aim for

Ship yourself a build that is: a usable daily launcher (Phase 2) + one built-in
token-driven theme that restyles every icon coherently (Phase 3). That alone is a
real, differentiated minimal launcher — and it proves the engine before a single
AI call. Everything after is upside.

---

## 10. Usage insights & social — the leaderboard decision

We already collect the data for this in §6.5 (UsageStatsManager). The question is what
to *do* with it. This section is a deliberate design decision, not just a feature list
— build the default path, treat the public leaderboard as a flagged risk.

### 10.1 Build by default: self-comparison
The motivating loop that doesn't backfire is **you vs. your own past self**:
- Daily/weekly time per app and per category, with week-over-week trends.
- Streaks tied to the user's *own* limits: "5 days under your Instagram limit."
- Gentle, specific wins ("2 hours less scrolling than last week") — never a raw
  "you wasted X hours" verdict.
- Store this locally (Room). No account, no network, no server needed for this tier.

### 10.2 Why NOT a public / cross-user leaderboard (design rationale)
Do not build a ranked public board of "who wasted the least time." Reasons, so the
decision is on record:
1. **It contradicts the product.** A leaderboard is a thing you check compulsively —
   more screen time inside a focus app. The winners over-check; the app becomes the
   new distraction.
2. **Shame reduces engagement.** Being visibly near the bottom makes people quit
   self-improvement tools, not try harder. A focus app that makes you feel bad gets
   uninstalled.
3. **"Wasted time" isn't comparable.** 6h in a design app is work; 1h of Instagram may
   be a chosen break. Ranking raw screen time is both inaccurate and preachy.
4. **Comparison IS the distraction loop.** Leaderboards run on the same
   social-comparison dopamine mechanic these apps exist to break. Don't rebuild the
   slot machine and point it at a different number.

### 10.3 The healthy social option (optional, later)
If a social layer is wanted, build the cooperative shape, not the competitive one:
- **Small, private, opt-in groups** (e.g. 3–5 friends who agree to a "less
  doomscrolling this week" pact).
- Share **progress toward each person's own goals**, not a ranked ladder of raw
  numbers. Cooperative framing — everyone can succeed, nobody is "last."
- Requires accounts + a backend + real privacy handling (screen-time data is
  sensitive — explicit consent, clear data policy, easy opt-out and delete). Treat as
  its own project after v1, not a bolt-on.

### 10.4 Guardrail for whoever builds this
Every metric surfaced must pass one test: *does seeing this help the user act, or just
make them feel judged?* Insights that inform action stay; scoreboards that only rank
worth get cut. Firm, kind, and never a dark pattern — the app is a self-control aid,
not a cage or a comparison engine.
