# Departures Widget Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A sideloaded Android app + Glance home-screen widget showing live IDFM departures for user-defined groups of (stop, line, direction) entries.

**Architecture:** Single `app` module. `data/` (Room + DataStore + Ktor clients for PRIM and Opendatasoft), `refresh/` (5 s non-wakeup alarm chain gated on "launcher in front", one fetch per unique stop), `widget/` (Glance, responsive sizes, RemoteViews `ViewFlipper` for the pulsing 0), `ui/` (Compose M3 config screens). Widgets always render from the Room departure cache; minutes are computed at render time.

**Tech Stack:** Kotlin, Gradle KTS + `libs.versions.toml`, latest stable AGP, minSdk 26, targetSdk latest stable, Glance appwidget, Compose M3, Ktor client (OkHttp engine) + kotlinx.serialization, Room (KSP), DataStore Preferences, coroutines, manual DI.

**Spec:** `SPEC.md` (repo root). Executors read both.

## Global Constraints

- IDFM only, English UI, no automated test suite, no CI (SPEC §1, §0). Verification = `./gradlew assembleDebug` + on-device screenshots/logcat.
- Log tags exactly `DeparturesRefresh` and `DeparturesApi` (SPEC §10).
- Fetch reasons logged exactly `home-entered`, `interval`, `tap` (SPEC §5).
- API key: `IDFM_API_KEY` in `local.properties` → `BuildConfig.IDFM_API_KEY`; DataStore override wins when non-blank (trimmed). `local.properties` git-ignored.
- PRIM: `GET https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring?MonitoringRef=<ref>`, headers `apikey`, `Accept: application/json`. Never pass `LineRef` from the refresh engine (one request per stop, filter locally).
- Times in SIRI are UTC ISO-8601 → `Instant`; display in `ZoneId.of("Europe/Paris")`, 24 h.
- Refresh interval options 20 / 30 (default) / 60 s. Tick ~5 s with `AlarmManager.setExact(RTC, …)` — never `RTC_WAKEUP`.
- Theme LIGHT default / DARK from Settings only (never system).
- Debug keystore committed at `app/debug.keystore` (no secrets).

## ID mapping (verified 2026-10-06 against Opendatasoft; write to `docs/idfm-ids.md` in Task 1)

| Dataset value | Request value |
|---|---|
| `arrets-lignes.stop_id` = `IDFM:monomodalStopPlace:43105` | `MonitoringRef=STIF:StopArea:SP:43105:` |
| `arrets-lignes.stop_id` = `IDFM:5252` (bus quay) | `MonitoringRef=STIF:StopPoint:Q:5252:` |
| `arrets-lignes.id` = `IDFM:C01727` | `LineRef` in response = `STIF:Line::C01727:` |
| `referentiel-des-lignes.id_line` = `C01727` | same line |

Known fixtures: Cernay `monomodalStopPlace:43105` (RER C `C01727`, H `C01737`); Ermont Halte `monomodalStopPlace:47920` (H). `arrets-lignes.mode` values: `Bus, Metro, RapidTransit (RER), LocalTrain (Transilien), regionalRail, Tramway, RailShuttle, CableWay, Funicular`. Search with `where=search(stop_name,"<q>")`. **First task with a key: confirm one live call per mapping row; record the result in `docs/idfm-ids.md`.**

## Review Focus

1. **Times across midnight / DST** — departures at 00:10 seen at 23:55 must show `15 min`, not a negative or clock time; parse as `Instant`, never as local time. (Task 3 debug fake includes a 23:55→00:10 case.)
2. **Pasted API key with whitespace/newline** — must work; trim on save and on read. (Task 6 "Test API key" with a padded key.)
3. **Same stop name, different places** (e.g. "Gare de Cernay" bus in Ermont vs "Rue de Cernay" in Les Molières) — search results show name + commune + modes, grouped by `stop_id`. (Task 6 search for "Cernay".)
4. **Direction filter that no longer matches** (destination renamed) — row shows `—`, never crashes; editor shows saved directions even if absent from live list. (Task 3 filter returns empty list.)
5. **Process death / reboot with widgets placed** — widget renders from Room cache immediately and alarms re-arm. (Task 8 verification: `adb shell am force-stop`, then return home.)

---

### Task 1: Project scaffold, key plumbing, docs

**Files:**
- Create: `settings.gradle.kts`, `build.gradle.kts`, `gradle/libs.versions.toml`, `gradle.properties`, Gradle wrapper, `app/build.gradle.kts`, `app/src/main/AndroidManifest.xml`, `app/src/main/java/fr/departures/App.kt`, `app/debug.keystore`, `.gitignore`, `README.md`, `docs/idfm-ids.md`

**Interfaces:**
- Produces: package `fr.departures`; `BuildConfig.IDFM_API_KEY: String` (empty string if absent, no build warning); `App : Application` holding `lateinit var locator: ServiceLocator` (filled in Task 2).

- [ ] **Step 1:** `git init`; `.gitignore` covers `local.properties`, `build/`, `.gradle/`, `.idea/`, `*.png` screenshots in root.
- [ ] **Step 2:** Gradle files. Read `IDFM_API_KEY` from `local.properties` via `Properties()`; `buildConfigField("String","IDFM_API_KEY","\"$key\"")`. `signingConfigs.debug` → `app/debug.keystore` (generate with `keytool`, alias `androiddebugkey`, passwords `android`). Enable compose, KSP for Room.
- [ ] **Step 3:** README: where to put `IDFM_API_KEY`, the curl test (`curl -H "apikey: $KEY" -H "Accept: application/json" "https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring?MonitoringRef=STIF:StopArea:SP:43105:"`), build/install commands, phone setup (SPEC §10).
- [ ] **Step 4:** `docs/idfm-ids.md` from the mapping table above.
- [ ] **Step 5:** Run `./gradlew assembleDebug` — Expected: BUILD SUCCESSFUL with no `local.properties`.
- [ ] **Step 6:** Commit `chore: scaffold project`.

### Task 2: Domain model, Room, DataStore, ServiceLocator

**Files:**
- Create: `data/model/Models.kt`, `data/db/AppDatabase.kt`, `data/db/Daos.kt`, `data/db/Entities.kt`, `data/SettingsRepository.kt`, `data/GroupRepository.kt`, `ServiceLocator.kt`

**Interfaces:**
- Produces:
  - `enum class Mode { METRO, RER, TRAIN, TRAM, BUS, OTHER }`; `fun Mode.Companion.fromDataset(mode: String): Mode` (`Metro→METRO, RapidTransit→RER, LocalTrain|regionalRail|RailShuttle→TRAIN, Tramway→TRAM, Bus→BUS, else OTHER`).
  - `data class Group(id: Long, title: String, sortOrder: Int, entries: List<Entry>)`
  - `data class Entry(id: Long, groupId: Long, sortOrder: Int, stopRef: String, stopName: String, lineRef: String, lineShortName: String, mode: Mode, lineColor: Int?, lineTextColor: Int?, directions: Set<String>)`
  - Room tables: `groups`, `entries` (FK cascade delete), `widget_bindings(appWidgetId PK, groupId)` (**no** FK — deleted group must stay detectable), `departure_cache(stopRef PK, json: String, fetchedAt: Long)`.
  - `GroupRepository`: `observeGroups(): Flow<List<Group>>`, `getGroup(id): Group?`, `createGroup(title): Long`, `renameGroup(id,title)`, `deleteGroup(id)`, `moveGroup(id, toIndex)`, `addEntries(groupId, List<Entry>)`, `removeEntry(id)`, `moveEntry(id, toIndex)`, `bindWidget(appWidgetId, groupId)`, `unbindWidget(appWidgetId)`, `groupForWidget(appWidgetId): Group?`, `boundStopRefs(): Set<String>` (unique stops across all bound groups).
  - `enum class ThemePref { LIGHT, DARK }`; `data class Settings(theme: ThemePref = LIGHT, refreshIntervalSec: Int = 30, apiKeyOverride: String? = null)`; `SettingsRepository.settings: Flow<Settings>`, `update(transform)`, `effectiveApiKey(): String` (override trimmed if non-blank, else `BuildConfig.IDFM_API_KEY.trim()`).
  - `ServiceLocator(context)` exposing `db, groups, settings, prim, openData, departures` (later tasks add fields); `Context.locator` extension.
- [ ] **Step 1:** Implement. **Step 2:** `./gradlew assembleDebug` passes. **Step 3:** Commit `feat: data model and persistence`.

### Task 3: PRIM client, SIRI parsing, departure repository, debug fake

**Files:**
- Create: `data/api/PrimClient.kt`, `data/api/SiriDto.kt`, `data/Departure.kt`, `data/DepartureRepository.kt`, `app/src/debug/java/fr/departures/data/FakeDepartures.kt`, `app/src/release/java/fr/departures/data/FakeDepartures.kt` (no-op)

**Interfaces:**
- Consumes: `SettingsRepository.effectiveApiKey()`, `departure_cache` DAO, `GroupRepository.boundStopRefs()`.
- Produces:
  - `@Serializable data class Departure(lineRef: String, destination: String, expected: Long /*epoch ms*/, aimed: Long?, cancelled: Boolean, atStop: Boolean)`
  - `sealed interface FetchResult { Success(list: List<Departure>); Unauthorized; RateLimited; Failure(msg: String) }`
  - `PrimClient.stopMonitoring(stopRef: String, apiKey: String): FetchResult` — path `Siri.ServiceDelivery.StopMonitoringDelivery[*].MonitoredStopVisit[*].MonitoredVehicleJourney`; SIRI values are wrapped `{ "value": … }` (`LineRef`, `DestinationName[0]`, `DirectionName[0]`); `ignoreUnknownKeys = true`; expected = `ExpectedDepartureTime ?: ExpectedArrivalTime ?: AimedDepartureTime`; skip visits with no time; `cancelled = DepartureStatus.equals("cancelled", true)`; HTTP 401/403 → `Unauthorized`, 429 → `RateLimited`; blank key → `Unauthorized` without network. Log `DeparturesApi`: ref, status, count, duration.
  - `enum class FetchReason(val tag: String) { HOME_ENTERED("home-entered"), INTERVAL("interval"), TAP("tap") }`
  - `data class StopStatus(fetchedAt: Long?, lastError: ErrorKind?)`; `enum class ErrorKind { NETWORK, AUTH, RATE_LIMIT }`
  - `DepartureRepository.refresh(reason: FetchReason, stopRefs: Set<String> = boundStopRefs())` — one request per ref, max 5 req/s (sequential with ≥200 ms spacing); on `RateLimited` set backoff `nextAllowedAt = now + min(30s·2^n, 10 min)` and skip fetches until then (tap included); success resets n. Keeps last cache on any error. Logs `DeparturesRefresh` `fetch reason=<tag> stops=<n>`.
  - `DepartureRepository.departuresFor(entry: Entry): List<Departure>` — cache filtered by `lineRef == entry.lineRef` (compare normalized: strip to `C\d+` id) and `directions.isEmpty() || destination in directions`, sorted by expected, keep `expected >= now - 60 s`, take 3.
  - `DepartureRepository.globalState(): WidgetDataState` = `{ oldestFetchedAt: Long?, error: ErrorKind?, fetching: Boolean }`.
  - `FakeDepartures.enabled` (debug toggle in Settings) — when on, `refresh` writes synthetic data per stop: at-stop, 4 min delayed by 3, cancelled, 75 min, and a departure at local 00:10 when now is ≥ 23:30 (Review Focus 1); one entry with zero departures.
- [ ] **Step 1:** Implement. **Step 2:** `./gradlew assembleDebug`. **Step 3:** Once the user's key is in `local.properties`: add a temporary debug log on app start calling Cernay `STIF:StopArea:SP:43105:` and confirm in logcat that LineRefs `C01727`/`C01737` appear; record the confirmed formats in `docs/idfm-ids.md`, then remove the temp call. **Step 4:** Commit `feat: PRIM client and departure cache`.

### Task 4: Time formatting

**Files:**
- Create: `widget/DepartureFormatter.kt`

**Interfaces:**
- Produces:
  - `enum class CellStyle { NORMAL, DELAYED, CANCELLED, PULSE, EMPTY }`
  - `data class TimeCell(text: String, style: CellStyle)`
  - `fun formatDeparture(d: Departure, now: Instant): TimeCell` — rules exactly SPEC §6.2: cancelled → CANCELLED (text as otherwise computed); `atStop || mins < 1` → `"0"` PULSE; `mins < 60` → `"$mins min"` (floor); else `HH:mm` Europe/Paris; delayed (`expected - aimed >= 60 s`) → DELAYED unless cancelled/pulse.
  - `fun emptyCell() = TimeCell("—", EMPTY)`
  - `fun footerText(state: WidgetDataState, now: Instant): Pair<String, Boolean /*warning*/>` — `"Updated just now"` (<10 s), `"Updated 12 s ago"`, `"Updated 4 min ago"`; warning when age > 120 s or error; RATE_LIMIT → `"Rate limit, retrying soon"` (warning); never fetched → `"Loading…"`.
- [ ] **Step 1:** Implement. **Step 2:** build. **Step 3:** Commit `feat: departure formatting`.

### Task 5: Opendatasoft client (setup data)

**Files:**
- Create: `data/api/OpenDataClient.kt`

**Interfaces:**
- Produces:
  - `data class StationHit(stopId: String, name: String, commune: String, modes: Set<Mode>)` (grouped by `stop_id`)
  - `data class LineAtStation(lineId: String /*C01727*/, shortName: String, longName: String, mode: Mode, color: Int?, textColor: Int?)`
  - `OpenDataClient.searchStations(q: String): List<StationHit>` — `arrets-lignes`, `where=search(stop_name,"q")`, `limit=100`, then group; sort non-bus first.
  - `OpenDataClient.linesAt(stopId: String): List<LineAtStation>` — `arrets-lignes where stop_id="…"`, then one `referentiel-des-lignes where id_line in (…)` for colours (`colourweb_hexa`, `textcolourweb_hexa`, hex without `#`; missing → null).
  - `fun monitoringRefFor(stopId: String): String` and `fun lineRefFor(lineId: String): String` per the mapping table.
- [ ] **Step 1:** Implement. **Step 2:** build. **Step 3:** Commit `feat: open data client`.

### Task 6: App screens (Compose M3)

**Files:**
- Create: `ui/MainActivity.kt`, `ui/theme/Theme.kt`, `ui/LineBadge.kt` (Compose version), `ui/groups/GroupsScreen.kt`, `ui/groups/GroupEditorScreen.kt`, `ui/addentry/AddEntryFlow.kt`, `ui/settings/SettingsScreen.kt`, `ui/onboarding/PermissionsScreen.kt`, `ui/Nav.kt`

**Interfaces:**
- Consumes: Tasks 2, 3, 5. `RefreshScheduler.ensureScheduled(context)` (Task 8) called in `MainActivity.onCreate`.
- Produces: `MainActivity` intent extras `EXTRA_OPEN = "settings" | "editor"`, `EXTRA_GROUP_ID: Long`; editor returns created group id via `RESULT_OK` + `EXTRA_GROUP_ID` when launched for result (used by Task 7 config activity).
- Copy (exact): empty state button "Create a group"; no-live-data note "No live data for this line at this stop"; empty directions note "No departures right now, so directions can't be listed. You can still pick All directions."
- [ ] **Step 1:** Theme from `Settings.theme` (LIGHT default). **Step 2:** Groups list (title + "N entries"), rename/delete dialogs, reorder via up/down. **Step 3:** Group editor: title field, entries "Station · badge · directions", remove, reorder. **Step 4:** Add entry: debounced (300 ms) search → multi-select lines with badges + mode → directions from live `stopMonitoring` distinct destinations per line + "All directions" (default when nothing selected); saved directions not in the live list remain shown (Review Focus 4). **Step 5:** Settings: theme, interval 20/30/60, key field (trimmed), "Test API key" (calls Cernay, shows OK / bad key / rate limited / network error), debug-only "Fake data" switch, note when Usage access is denied. **Step 6:** Permissions onboarding (first run): Usage access (`Settings.ACTION_USAGE_ACCESS_SETTINGS`), exact alarms (`ACTION_REQUEST_SCHEDULE_EXACT_ALARM`, API 31+), battery optimisation (`ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS`) + link `https://dontkillmyapp.com/<Build.MANUFACTURER lowercase>`. **Step 7:** build; commit `feat: configuration app`.

### Task 7: Widget (design tokens → Glance)

**Files:**
- Create: `docs/widget-design.md` (tokens + ASCII wireframes for Narrow / Medium / Large + self-critique), `widget/DeparturesWidget.kt`, `widget/DeparturesWidgetReceiver.kt`, `widget/WidgetConfigActivity.kt`, `widget/WidgetTokens.kt`, `widget/BadgeRenderer.kt`, `widget/RefreshAction.kt`, `res/layout/pulse_zero.xml`, `res/xml/departures_widget_info.xml`, `res/drawable/ic_mode_{metro,rer,train,tram,bus}.xml`, `res/drawable/ic_refresh.xml`

**Interfaces:**
- Consumes: `GroupRepository.groupForWidget`, `DepartureRepository.departuresFor/globalState/refresh`, `formatDeparture`, `footerText`, `Settings.theme`.
- Produces: `suspend fun updateAllWidgets(context)` (used by Task 8); `RefreshAction : ActionCallback` → sets fetching, `refresh(TAP)`, updates.
- Decisions:
  - Widget info: `resizeMode="horizontal|vertical"`, `minWidth/minHeight` ≈ 2×1, `targetCellWidth=3 targetCellHeight=2`, `widgetFeatures="reconfigurable|configuration_optional"` **omitted** (config is mandatory), `configure=".widget.WidgetConfigActivity"`, `updatePeriodMillis=0`.
  - `SizeMode.Responsive(setOf(DpSize(110.dp,60.dp) /*Narrow*/, DpSize(200.dp,110.dp) /*Medium*/, DpSize(260.dp,180.dp) /*Large*/))`. Narrow: title + badge + first time. Medium: badge + 3 times. Large: station subheaders (only if >1 station) + mode pictogram + badge + 3 times.
  - Body is a Glance `LazyColumn`; header and footer outside it.
  - First time per row: larger (~20 sp, bold); 2nd/3rd ~14 sp, secondary colour; no chips on 2nd/3rd. DELAYED → orange token, CANCELLED → `TextDecoration.LineThrough` + muted, PULSE → `AndroidRemoteViews(R.layout.pulse_zero)` (ViewFlipper `autoStart=true`, `flipInterval=700`, fade in/out anims, children = full and 35%-alpha "0"); on failure fall back to static accent-filled "0" and note it in `docs/widget-design.md`.
  - Badges rendered to `Bitmap` (`BadgeRenderer.render(shortName, mode, bg, fg, density): Bitmap`): METRO circle, RER/TRAIN rounded square, TRAM rectangle, BUS wide rounded rect; null colour → `#8A8F98` on white.
  - Corners: `GlanceModifier.cornerRadius(android.R.dimen.system_app_widget_background_radius)` on API 31+, 16 dp otherwise.
  - Whole widget clickable → `RefreshAction`; except deleted-group state ("This group was deleted. Open the app to choose another." → opens `MainActivity`) and missing/bad key ("Add your API key in the app" → opens Settings).
  - Config activity: "Which group?" list + "New group" (launches editor for result, then binds), `RESULT_CANCELED` on back; on OK bind, trigger `refresh(HOME_ENTERED)` and update.
  - Receiver `onDeleted` → `unbindWidget`; `onEnabled` → `RefreshScheduler.ensureScheduled`; `onDisabled` → `RefreshScheduler.cancel`.
- [ ] **Step 1:** Write `docs/widget-design.md` (light + dark palette tokens, type scale, spacing, three wireframes); review against SPEC §6. **Step 2:** Implement. **Step 3:** build. **Step 4:** Commit `feat: departures widget`.

### Task 8: Refresh engine

**Files:**
- Create: `refresh/HomeDetector.kt`, `refresh/RefreshScheduler.kt`, `refresh/TickReceiver.kt`, `refresh/BootReceiver.kt`; Modify manifest (permissions `PACKAGE_USAGE_STATS` tools:ignore, `SCHEDULE_EXACT_ALARM`, `RECEIVE_BOOT_COMPLETED`, `INTERNET`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`; `<queries>` for HOME intent)

**Interfaces:**
- Consumes: `DepartureRepository.refresh`, `updateAllWidgets`, `Settings.refreshIntervalSec`, `GroupRepository.boundStopRefs`.
- Produces: `RefreshScheduler.ensureScheduled(context)`, `RefreshScheduler.cancel(context)`; `HomeDetector.isLauncherInFront(context): Boolean?` (null = no usage access).
- Tick algorithm (each ~5 s, `goAsync()`):
  1. If no bound widgets → don't re-arm.
  2. `screenOn = PowerManager.isInteractive`, `locked = KeyguardManager.isKeyguardLocked`. `home = HomeDetector` (latest `ACTIVITY_RESUMED` in last 10 min of `queryEvents`, compared with `resolveActivity(ACTION_MAIN+CATEGORY_HOME)` package); if null → `home = screenOn && !locked`.
  3. If `!screenOn || locked || !home` → `wasHome=false`, re-arm, return (no network).
  4. If `!wasHome` → `refresh(HOME_ENTERED)`; else if cache age ≥ interval → `refresh(INTERVAL)`. Always `updateAllWidgets` (re-render minutes). `wasHome=true` (persist in SharedPreferences).
  5. Re-arm `setExact(RTC, now+5000)` if `canScheduleExactAlarms()`, else `set(RTC, …)` and Settings shows "Updates may lag".
- Boot / `MY_PACKAGE_REPLACED` → `ensureScheduled`.
- [ ] **Step 1:** Implement. **Step 2:** build. **Step 3:** Commit `feat: launcher-gated refresh`.

### Task 9: On-device verification (SPEC §8)

Requires the user's phone via adb on Windows (Android Studio / platform-tools). Builds can be produced in the sandbox; install, logcat, and screenshots run on the user's machine.

- [ ] **Step 1:** `./gradlew installDebug`; complete onboarding permissions.
- [ ] **Step 2:** Create "Paris": Cernay (RER C, H) + Ermont Halte (H), directions towards Paris. Place widget, screenshot, critique against §6 in `docs/widget-design.md`.
- [ ] **Step 3:** `adb logcat -s DeparturesRefresh DeparturesApi`: open another app 1 min → no fetches; lock/screen off → none; return home → immediate `home-entered`; stay → `interval` every 30 s; tap → `tap`.
- [ ] **Step 4:** Fake data on → screenshot delayed/cancelled/pulsing 0/≥60 min/`—`.
- [ ] **Step 5:** Resize to narrow/medium/large; overflow scrolls. Toggle Dark. Airplane mode → warning footer. Wrong key → key message. Two widgets sharing Cernay → one request per tick. Force-stop app then return home → widget still shows cache and fetches (Review Focus 5).
- [ ] **Step 6:** Fix with superpowers:systematic-debugging; then superpowers:finishing-a-development-branch.
