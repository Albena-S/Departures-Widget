# Brief: "Departures" — an Android home-screen widget for Île-de-France transit

Save this file in the repo root as `SPEC.md` and tell Claude Code: *"Read SPEC.md and build this app."*

---

## 0. How to work (skills)

Use these skills, in this order. If a skill isn't installed, install it first (see the end of this section).

1. **superpowers:writing-plans.** Turn this spec into an implementation plan before writing code. Every product decision below is already made. Do **not** run a brainstorming round that re-asks them. Only raise a question if real API data contradicts the spec, or if Android makes something impossible.
2. **frontend-design.** Use it for the widget and the app screens. Follow its two-pass process: write the token system (palette, type, layout with ASCII wireframes for each widget size), review it against this brief, then build. Adapt it to widget constraints (§6.4). The screenshot the user provided is only an example. Improve on it, don't copy it.
3. **superpowers:executing-plans** (or **subagent-driven-development** if available). Implement the plan task by task.
4. **superpowers:systematic-debugging.** Use it for any failing build, parsing bug, or widget that doesn't update.
5. **superpowers:verification-before-completion.** Before saying anything works, prove it: `./gradlew assembleDebug` succeeds, `./gradlew installDebug` installs on **the user's physical Android phone** (§10), and you have looked at a real screenshot of the widget showing live data (`adb exec-out screencap -p > shot.png`, then view it). Use those screenshots for the design self-critique too.
6. **superpowers:finishing-a-development-branch.** Use it at the end.

No automated test suite is required. The goal is a working, good-looking app.

Install, if missing. The exact commands may differ; check each repo's README.
```
/plugin marketplace add obra/superpowers-marketplace
/plugin install superpowers@superpowers-marketplace
/plugin marketplace add anthropics/claude-code
/plugin install frontend-design@claude-code-plugins
```

---

## 1. What we're building

This is a personal Android app with a home-screen widget showing **live departures** for a user-defined **group** of stops and lines in Île-de-France. It covers all modes: metro, RER, Transilien, tram, and bus.

Example group, titled **"Paris"**:
- Cernay, RER C, towards Paris
- Cernay, Transilien H, towards Paris
- Ermont-Halte, Transilien H, towards Paris

The group title is free text, like "Paris", "Work", or "School". The app does **not** need to understand routing or connections between lines. A group is just a hand-picked list of `(stop, line, direction)` entries shown together.

- Audience: the user and their family. It is sideloaded, never published.
- Language: English UI.
- Scope: IDFM only. No multi-city abstraction, no tests, no CI.

---

## 2. Data source: IDFM PRIM

### 2.1 Real-time departures

- Endpoint: `GET https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring?MonitoringRef=<ref>`
- Header: `apikey: <KEY>`, `Accept: application/json`
- `MonitoringRef` accepts a stop area (`STIF:StopArea:SP:<id>:`) or a stop point (`STIF:StopPoint:Q:<id>:`). An optional `LineRef=STIF:Line::<id>:` filters by line.
- Response format: SIRI Lite JSON. Fields to use, per `MonitoredStopVisit`:
  - `MonitoredVehicleJourney.LineRef`: which line
  - `DestinationName` / `DirectionName`: used for direction filtering
  - `MonitoredCall.ExpectedDepartureTime` (fall back to `ExpectedArrivalTime`): the time shown
  - `MonitoredCall.AimedDepartureTime`: used only to detect delays
  - `MonitoredCall.DepartureStatus`: `cancelled` means cancelled
  - `MonitoredCall.VehicleAtStop`: `true` means at platform
- Note: per IDFM docs, `ResponseTimestamp` is in local time and the other times are UTC. Parse carefully, using `java.time` and the `Europe/Paris` zone for display.
- A multi-line stop returns all lines, so filter by `LineRef` on the client.
- **Fetch once per unique stop per refresh tick**, across all widgets and groups, then filter locally. This is the main quota saver.

### 2.2 Quota (important)

The PRIM page lists different quotas depending on when the key was created. Older keys get 1,000,000 requests/day. Some newer keys get only **1,000 requests/day and 5 requests/second**. The user can check and raise this on PRIM's "My API usage" page.

Rough cost: 2 stops × a refresh every 30 s × 3 h of screen-on time per day ≈ 720 requests/day **per phone**. The design must:
- dedupe requests per stop (§2.1)
- make the refresh interval configurable (§5)
- handle `429` with exponential backoff, keeping the last data on screen and marking it stale
- let the API key be **overridden in app settings**, so each family member can paste their own key if a shared one runs out

### 2.3 Stop and line reference data, for the setup screens

These are IDFM open-data datasets (Opendatasoft, `data.iledefrance-mobilites.fr`). Query them through the Explore API v2.1 at setup time. Do not bundle them.
- `arrets-lignes`: stops per line (stop id, stop name, line id, line name, mode). Use it for station search and for listing the lines at a station.
- `referentiel-des-lignes`: line metadata, including **official colors** (`colourweb_hexa`, `textcolourweb_hexa`), short name, and mode.

Before building the setup flow, call these endpoints and check the real field names and id formats. Write down the id mapping (dataset stop id → `MonitoringRef`, dataset line id → `LineRef`) in a short `docs/idfm-ids.md`.

Not every stop has real-time data. IDFM publishes a "real-time perimeter" dataset. If a picked stop/line returns no live data, say so on the setup screen ("No live data for this line at this stop") rather than failing silently.

### 2.4 API key handling

- Build-time default: `IDFM_API_KEY` in `local.properties`, exposed as a `BuildConfig` field. Never commit it, and add it to `.gitignore`.
- Runtime override: a field in the app's Settings, stored in DataStore.

---

## 3. Data model

```
Group
  id, title (e.g. "Paris"), sortOrder
  entries: List<Entry>

Entry
  id, groupId, sortOrder
  stopRef        // MonitoringRef
  stopName       // "Cernay"
  lineRef        // LineRef
  lineShortName  // "C", "H", "14", "T2", "95-01"
  mode           // METRO | RER | TRAIN | TRAM | BUS | OTHER
  lineColor, lineTextColor   // from referentiel-des-lignes
  directions: Set<String>    // destination names to keep; empty = all directions

WidgetBinding
  appWidgetId -> groupId

Settings (DataStore)
  theme: LIGHT (default) | DARK
  refreshIntervalSec: 20 | 30 (default) | 60
  apiKeyOverride: String?
```

Persist with Room, or DataStore if that's simpler for this size. Cache the latest departures per stop, with a fetch timestamp, so the widget renders instantly and survives process death.

---

## 4. The app (minimal)

Configuration only. No in-app departure boards. Use Jetpack Compose with Material 3, light theme by default and dark if chosen in settings.

**Screens:**
1. **Groups.** List groups (title + entry count), then add, rename, delete, and reorder. Show an empty state with one clear action: "Create a group".
2. **Group editor.** Title field and an entry list (station · line badge · directions), with add, remove, and reorder.
3. **Add entry**, in three steps:
   1. Search for a station by name (debounced, against `arrets-lignes`).
   2. Pick one or more lines at that station. Show generated line badges (§6.3) and the mode.
   3. Pick directions. Populate them from a live `stop-monitoring` call (the distinct `DestinationName`s for that line), plus an "All directions" option. If the live call returns nothing (night or no service), allow "All directions" and say why the list is empty.
4. **Settings.** Theme (Light/Dark), refresh interval, API key override, and a "Test API key" button that shows the result.

**Widget placement flow:** when the user drops a widget on the home screen, a configuration activity opens and asks **"Which group?"**. It lists existing groups plus "New group", which opens the group editor inline and then returns. If the user backs out, the widget is not added (`RESULT_CANCELED`). After placement, **all editing happens in the app**. There is no long-press reconfigure.

---

## 5. Refresh strategy

Android doesn't allow widgets to update every 20–30 s through the normal mechanisms (`updatePeriodMillis` has a 30-minute floor, and WorkManager a 15-minute floor). The agreed behaviour: **fetch only while the home screen (launcher) is the foreground app.** No fetching while another app is open, while the phone is locked, or while the screen is off.

Implementation, to verify on a real device:
- **Detect "home screen visible"** with `UsageStatsManager`:
  - Read `queryEvents` for the last few seconds and take the latest `ACTIVITY_RESUMED` package.
  - Compare it with the default launcher package, resolved via an `Intent.ACTION_MAIN` + `CATEGORY_HOME` query.
  - This needs the `PACKAGE_USAGE_STATS` special access. That's fine for a sideloaded app. On first run, show one clear screen explaining why, with a button to the system setting.
  - If access is denied, fall back to "screen on and unlocked" and show a note in Settings.
- **Cheap check loop, gated by a screen-state check.** Use chained non-wakeup exact alarms (`AlarmManager.setExact(RTC, …)`, **not** `RTC_WAKEUP`) every ~5 s. Non-wakeup alarms don't fire while the device sleeps, and they deliver as soon as it wakes. Each tick:
  1. If the screen is off, the keyguard is locked, or the launcher isn't in front, do nothing (no network) and re-arm.
  2. If the launcher **just came to the foreground**, or the cached data is older than the refresh interval, fetch each unique stop used by any placed widget (§2.1) and update all widgets.
  3. Re-arm.

  The result: data refreshes the moment the user returns home, then every interval while they stay there. The 5 s tick does no network work, so it costs almost nothing.
- Android 12+ exact alarms need `SCHEDULE_EXACT_ALARM`. That's fine for a sideloaded app. Guide the user to the toggle on first run. If it's denied, fall back to inexact alarms and say updates may lag.
- Re-arm on `BOOT_COMPLETED`, `MY_PACKAGE_REPLACED`, widget `onEnabled`, and after app launch. Cancel in `onDisabled` (no widgets left).
- **Minutes are always computed from absolute times at render time.** Re-render from cache on every tick where the launcher is in front, even without a fetch.
- **Tapping anywhere on the widget forces a refresh.** That's the only tap action. Show a brief loading state on the refresh icon while fetching.
- **Known limitation:** Android exposes no API for "which home-screen page is showing", so a widget on page 2 still refreshes while the user is on page 1. That's acceptable.
- Log every fetch with its reason (`home-entered`, `interval`, `tap`) so request volume can be checked against the quota.

---

## 6. The widget

Build it with **Jetpack Glance** (latest stable). Use one widget class; each instance shows one group.

### 6.1 Content rules

- **Header:** group title.
- **Body:** entries, grouped by station and in the user's order within a station. Each row shows:
  - line badge (§6.3)
  - **next 3 departures**

  There is no direction label: the group title already says where these departures go. Station names appear as small subheaders, and only when the group has more than one station.
- **Footer:** "Updated 12 s ago" plus a refresh icon. The text turns into a warning colour once data is older than 2 minutes.

### 6.2 Time formatting and states

| Situation | Display |
|---|---|
| Departure in < 60 min | `11 min` |
| Departure in ≥ 60 min | `14:52` (24 h, Europe/Paris) |
| Vehicle at platform (`VehicleAtStop`) or < 1 min | `0`, **gently pulsing** (§6.4) |
| Delayed (expected − aimed ≥ 1 min) | the time itself in **orange**, with no "+5" |
| Cancelled | time **struck through**, muted |
| No departures returned | `—` |
| Next departure far away (e.g. night) | just the clock time, e.g. `05:42`, with no extra label |

Notes:
- **Real-time only.** Show only what the API returns, with no "live" icon and no theoretical timetable fallback. If the API doesn't return far-future departures at night, `—` is acceptable.
- Minutes are rounded down.
- Cancelled departures still count toward the 3 shown, so the user sees that a train vanished.

### 6.3 Line badges (generated, not official logo files)

Draw badges as UI elements using the **official line colours** from `referentiel-des-lignes`, with the short name in the official text colour. Use the shape to suggest the mode, following IDFM conventions:
- Metro: circle
- RER / Transilien: rounded square
- Tram: rectangle
- Bus: wide rounded rectangle

Add a small mode pictogram (simple vector drawables drawn for this app: train, metro, tram, bus) before the badge, or skip it at small sizes. If a colour is missing, use a neutral grey.

### 6.4 Design direction and constraints

Apply **frontend-design** within widget limits. The example screenshot (white card, grey rounded "time chips", small coloured line pills, "just now" + refresh in the footer) is a starting point. Make it clearer and more glanceable:

- **The one thing that matters is the next departure.** Give the first time per row the most visual weight, and make the 2nd and 3rd quieter.
- Keep the hierarchy clear: title → station → line rows → footer. Avoid identical chips everywhere.
- Light theme by default. The dark theme comes from Settings, not from the system or wallpaper.
- Use system rounded corners (`system_app_widget_background_radius` on Android 12+).
- Glance and RemoteViews constraints:
  - no custom font files (use system families/weights)
  - no arbitrary animations
  - limited layout primitives
- **The pulsing "0":** Glance can't animate. Implement it with an `AndroidRemoteViews` block containing a `ViewFlipper` (autoStart, ~700 ms interval, fade in/out between the full-colour and ~35%-alpha versions of the label). If that proves unreliable on the test launcher, fall back to a static, clearly highlighted "0" and say so.
- Write ASCII wireframes for each size class before coding (§6.5), and critique the screenshots afterwards.

### 6.5 Size and overflow

A widget can't resize itself to fit its content; the user resizes it. Therefore:
- `resizeMode="horizontal|vertical"`, a sensible default (about 3×2 cells, using `targetCellWidth/Height` on Android 12+), and a small minimum (2×1).
- `SizeMode.Responsive` with about 3 breakpoints:
  - **Narrow/short:** title + per row: badge + first departure only
  - **Medium:** badge + 3 times
  - **Large:** grouped by station with station subheaders
- **Scroll:** if rows don't fit at the current size, the body is a `LazyColumn` (scrollable in widgets), with the header and footer fixed.
- **No empty widget:** placement always binds a group (§4). If its group is later deleted, the widget shows "This group was deleted. Open the app to choose another." and tapping opens the app.

### 6.6 Error and stale states

- Network/API error: keep the last data, footer shows "Updated 4 min ago" in the warning colour.
- Bad or missing API key: "Add your API key in the app", and tapping opens Settings.
- Quota hit (`429`): back off, keep the last data, footer shows "Rate limit, retrying soon".

---

## 7. Tech stack

- Kotlin, Gradle Kotlin DSL with a version catalog, latest stable AGP
- `minSdk 26`, `targetSdk` = latest stable
- Jetpack Glance (widget), Jetpack Compose + Material 3 (app)
- Ktor client or Retrofit with kotlinx.serialization (pick one)
- Room and/or DataStore
- Coroutines; manual dependency injection is fine at this size (no Hilt needed)
- Module layout: a single `app` module with packages `data/`, `widget/`, `ui/`, `refresh/`

---

## 8. Done when

- [ ] `./gradlew assembleDebug` passes with no warnings about missing keys. A README explains where to put `IDFM_API_KEY`.
- [ ] Creating the group "Paris" with Cernay (RER C, H) and Ermont-Halte (H), filtered towards Paris, works end to end through the setup screens.
- [ ] A placed widget shows live departures for that group. A screenshot of it has been checked against §6.
- [ ] Logcat shows fetches only while the launcher is in front. Opening another app, locking the phone, or turning the screen off stops them. Returning home fetches immediately.
- [ ] Tapping the widget refreshes it.
- [ ] Delayed (orange), cancelled (struck through), `0` pulsing, ≥60 min clock time, and `—` all render correctly. Use a debug-only fake data source to force each state for screenshots.
- [ ] Everything above was checked on the user's phone, not only an emulator.
- [ ] Resizing the widget switches layouts. Overflowing content scrolls.
- [ ] Light/Dark setting applies to the widget and the app.
- [ ] Airplane mode shows stale data with a warning-coloured "Updated … ago". A wrong API key shows the key message.
- [ ] Two widgets showing groups that share a stop make **one** request per tick for that stop (visible in logs).

## 9. Before you start (for the user)

1. Create a PRIM account at prim.iledefrance-mobilites.fr and generate an API key. One key works for all PRIM APIs; "Subscribe" on an API page only turns on email alerts. The swagger.json "interface contract" is documentation and is not needed by the app.
2. Test the key with one curl call (see the README). After that, check "Ma consommation API" to see your usage and quota. Request an increase only if the quota is tight.
3. Put the key in `local.properties`: `IDFM_API_KEY=...`
4. Install Android Studio (or just the Android SDK platform-tools) and prepare your phone as described in §10.

---

## 10. Testing on the user's phone

All testing happens on the user's own Android phone. No emulator is needed, though one can be used for quick UI iterations.

**Phone setup (user, once):**
1. Settings → About phone → tap **Build number** 7 times to enable Developer options.
2. Developer options → turn on **USB debugging**. **Wireless debugging** (Android 11+) also works, using `adb pair` and then `adb connect`.
3. Connect to the computer, accept the "Allow USB debugging?" prompt, and check that `adb devices` lists the phone.

**Claude Code workflow:**
- Install with `./gradlew installDebug`, or `adb install -r app/build/outputs/apk/debug/app-debug.apk`.
- Debug with `adb logcat -s DeparturesRefresh DeparturesApi` (use these log tags in the code).
- Screenshot with `adb exec-out screencap -p > shot.png`. Ask the user to put the widget on the home screen and resize it when needed, since placing widgets can't be scripted reliably.
- Give the user a short checklist of manual actions for each test (e.g. "open another app for 1 minute, then return home") and read the logs afterwards.

**On first run, the app guides the user through these permissions:**
- **Usage access** (§5), needed for "home screen visible" detection
- **Alarms & reminders** (`SCHEDULE_EXACT_ALARM`, Android 12+)
- **Battery optimisation exemption.** Many manufacturers (Samsung, Xiaomi, OnePlus, Huawei…) kill background alarms aggressively. Show a one-time screen with a button to the battery-optimisation setting, plus a link to dontkillmyapp.com for the phone's brand. If refreshes stop after a while on the test phone, check this first.

**Distribution to family:** the same debug APK can be sent and sideloaded. Each phone needs "Install unknown apps" allowed for whatever app opens the APK (Files, Drive…). Use one fixed debug keystore committed to the repo, without secrets, so updates install over older versions without uninstalling.
