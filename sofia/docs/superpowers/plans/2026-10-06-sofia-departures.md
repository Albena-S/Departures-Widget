# Sofia Departures Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A standalone "Departures Sofia" app (installable next to Departures Paris) with the same widget, refresh loop and setup flow, fed by the sofiatraffic.bg virtual table and a GTFS stop list.

**Architecture:** This repo is a copy of the Paris app. Widget, refresh engine, theming, formatter and persistence stay; the IDFM layer (PRIM, open data, API key, colours lookup) is replaced by `SofiaClient` (virtual table + cookie/XSRF session) and `SofiaStopRepository` (GTFS `stops.txt`, transliterated search). Kotlin package stays `fr.departures`; only `applicationId` changes.

**Tech Stack:** As Paris (Kotlin 2.4.20, AGP 9.4.1, Glance 1.2.0, Compose M3, Ktor 3.6 OkHttp, DataStore JSON) + Ktor `HttpCookies` with a persistent storage, `ktor-client-mock` for tests.

**Spec:** `docs/sofia-app-spec.md` (binding), `SPEC.md` (the Paris base it inherits), `../paris/docs/paris-app-naming.md` (naming/icon rules).

## Global Constraints

- `applicationId = "fr.departures.sofia"`; app name "Departures Sofia"; widget label "Departures · Sofia"; description "Live departures for your Sofia groups"; Groups title "Departures · Sofia".
- Icon: `tools/icon/gen_icon.py S "#BD202E"` (same base as Paris, "S" tag, monochrome layer). Nothing city-related inside the widget.
- Endpoint: `POST https://www.sofiatraffic.bg/bg/trip/getVirtualTable`, body `{"stop":"<code>"}`, headers `Content-Type: application/json`, `Accept: application/json`, `X-XSRF-TOKEN: <url-decoded XSRF-TOKEN cookie>`. Handshake: `GET https://www.sofiatraffic.bg/bg/public-transport`. On `302/401/419` or a non-JSON body: handshake once, retry once.
- One request per unique stop code per tick; refresh interval/backoff as Paris (no quota UI).
- Times: `expected = fetchTime + t·60 000`; zone `Europe/Sofia`; ≥ 60 min → `HH:mm`. No delay colour, no strike-through. `t == 0` pulses.
- Footer on repeated endpoint failure: "Sofia data unavailable". Log raw status + first 300 chars of body under `DeparturesApi`.
- Log tags `DeparturesRefresh` / `DeparturesApi` unchanged.

## Verified facts (2026-10-06, from this sandbox)

- Handshake and POST work as specified; response is a JSON object keyed `"<last_stop>_<ext_id>"`.
- Fields seen: `name`, `ext_id`, `type`, `color`, `st_name`, `st_name_en`, `last_stop`, `details[] {t, ac, wheelchairs, bikes}`.
- `type`: **1 bus, 2 tram, 3 metro, 4 trolleybus, 5 night bus** (tram stop 0720: 9/20/22/N4; metro stop 18: M4). **Metro is covered.**
- GTFS: `stops.txt` is the **2nd zip entry (offset 235, 129 KB compressed)**; the server ignores `Range`. Stream with `ZipInputStream` and close after `stops.txt` → ~130 KB instead of 19 MB.
- `stops.txt`: 4,797 rows; use `location_type == 0` with non-empty `stop_code` (4,412). **The same code is shared by bus/trolley/tram stop ids at one physical stop** (`A0328`, `TB0328`) → dedupe by code. Codes are 1–4 digits (metro: `18`, `302`).
- `stop_id` prefix → modes: `A` bus, `TB` trolleybus, `TM` tram, `M`/`ME`/`MSt` metro.

## Review Focus

1. **XSRF token rotation** — Laravel re-issues `XSRF-TOKEN` on responses; the header must be read from the *current* cookie on every request, or every second call gets `419`. (Task 3 test: second POST uses the rotated token.)
2. **Night/empty responses** may be `[]` instead of `{}` → "No vehicles right now…", never a crash or "unavailable". (Task 3 parser test.)
3. **Short metro codes** — "18" must find metro stop 18 and must not be zero-padded to "0018". Exact match first; zero-padding only as a fallback when no exact match. (Task 4 test.)
4. **`t == 0` vanishing seconds after a fetch** — Paris drops departures whose time has passed; Sofia minutes are whole, so keep a departure until 60 s after its computed time. (Task 2 test.)
5. **First run offline / download cut** — stop search shows a retry, the app never crashes, a partial file is never saved. (Task 4: write to temp, rename on success.)

---

### Task 1: Rebrand and strip IDFM build plumbing

**Files:** `app/build.gradle.kts`, `app/src/main/res/values/strings.xml`, icon resources (generated), `res/layout/widget_preview.xml`, `res/drawable/preview_badge_*.xml`, `README.md`, `docs/sofia-endpoint.md` (verified facts above)

- [ ] `applicationId = "fr.departures.sofia"`; remove `local.properties` reading and the `IDFM_API_KEY` BuildConfig field (`buildConfig = true` stays for `DEBUG`).
- [ ] Strings per Global Constraints. Regenerate icon with `gen_icon.py S "#BD202E"`. Preview layout: title "Sofia", sample rows trolleybus `6` (`#2AA9E0`) and bus `310` (`#BD202E`).
- [ ] README: no key needed; side-by-side install; the endpoint is unofficial.
- [ ] Build fails only on code still referencing `BuildConfig.IDFM_API_KEY` (fixed in Task 5) — so do Task 1 + Task 5's key removal before the first green build. Commit `chore: rebrand as Departures Sofia`.

### Task 2: Domain changes

**Files:** `data/model/Models.kt`, `data/Departure.kt`, `data/DepartureRepository.kt` (filter), `widget/DepartureFormatter.kt`, `widget/BadgeRenderer.kt`, `widget/DeparturesWidget.kt` (mode icon), tests

**Interfaces — Produces:**
- `Mode` gains `TROLLEY`; `Mode.fromSofiaType(type: Int): Mode` (1→BUS, 2→TRAM, 3→METRO, 4→TROLLEY, 5→BUS, else OTHER). `fromDataset` removed.
- `Departure` gains `destinationId: String = ""` (`last_stop`); `destination` = `st_name_en` (fallback `st_name`).
- `Entry` mapping (names kept to limit churn): `stopRef` = stop code, `lineRef` = `ext_id` or `"*"` (all lines), `lineShortName` = `name`, `directions` = set of `last_stop` ids, new `directionLabels: Map<String,String> = emptyMap()` (id → Latin name, for the editor).
- `filterForEntry`: line match `entry.lineRef == "*" || dep.lineRef == entry.lineRef`; direction match on `destinationId`; keep while `expected >= now - 60_000`.
- Formatter zone `Europe/Sofia`. `ErrorKind.UNAVAILABLE`; `footerText` → "Sofia data unavailable" (warning) for it.
- Badge: TROLLEY drawn like BUS; "All" badge (lineRef `*`) neutral grey.
- [ ] Tests: `filterForEntry` by destination id, `"*"`, and t=0 kept for 59 s; formatter clock time in Sofia zone; footer UNAVAILABLE. Watch them fail, implement, pass. Commit.

### Task 3: SofiaClient (session + parser)

**Files:** `data/api/SofiaClient.kt`, `data/api/VirtualTableParser.kt`, `data/api/PersistentCookies.kt`, tests with `ktor-client-mock`

**Interfaces — Produces:**
- `fun parseVirtualTable(body: String, fetchedAt: Long): List<Departure>` — accepts object or array; throws `NotJson` for HTML/garbage.
- `class SofiaClient(engine: HttpClientEngine, cookies: CookiesStorage)` with `suspend fun virtualTable(code: String): FetchResult` (`Success`, `Failure`, new `Unavailable(status, snippet)`). `followRedirects = false`. Handshake if no XSRF cookie, then on `302/401/419`/non-JSON: handshake + one retry.
- `class PersistentCookiesStorage(prefs: SharedPreferences) : CookiesStorage` (survives process death; expiry respected).
- [ ] Tests (MockEngine): first call performs handshake then POST with decoded header; `419` → handshake → retry succeeds; second POST sends the **rotated** token; HTML twice → `Unavailable`; `[]` → empty success. Commit.

### Task 4: SofiaStopRepository (GTFS + search)

**Files:** `data/stops/Transliteration.kt`, `data/stops/StopIndex.kt`, `data/stops/SofiaStopRepository.kt`, tests

**Interfaces — Produces:**
- `fun toLatin(cyrillic: String): String` (official Bulgarian scheme; word-final "ия" → "ia"), `fun normalize(s: String): String` (lowercase, punctuation → space, collapse spaces).
- `data class SofiaStop(code: String, name: String, latin: String, modes: Set<Mode>)`.
- `fun parseStopsCsv(csv: String): List<SofiaStop>` (location_type 0, non-empty code, dedupe by code, modes from stop_id prefix).
- `class StopIndex(stops)` with `fun search(q: String, limit: Int = 50): List<SofiaStop>`: digits → exact code, then zero-padded fallback; text → normalized prefix-of-word matches on Cyrillic and Latin first, then substring.
- `SofiaStopRepository(context, http)`: `state: StateFlow<StopsState>` (`Missing`, `Downloading(bytes)`, `Ready(count, updatedAt)`, `Failed(msg)`), `ensure()`, `refreshIfOlderThan(30 days)`, `index(): StopIndex?`. Streams the zip, stops after `stops.txt`, writes `stops.json` via temp file + rename.
- [ ] Tests: "БУЛ. К. ВЕЛИЧКОВ" → "bul. k. velichkov"; "СОФИЯ" → "sofia"; search "0328", "18" (not "0018"), "velichkov", "величков", "bul k vel"; dedupe A0328/TB0328 → one stop with {BUS, TROLLEY}. Commit.

### Task 5: Wire data layer, widget states, settings, onboarding

**Files:** `ServiceLocator.kt`, `data/DepartureRepository.kt`, `data/SettingsRepository.kt`, `widget/WidgetModel.kt`, `widget/DeparturesWidget.kt`, `ui/settings/SettingsScreen.kt`, `ui/onboarding/PermissionsScreen.kt`, `ui/Nav.kt`; delete `PrimClient.kt`, `SiriParser.kt`, `OpenDataClient.kt`, IDFM tests

- [ ] `DepartureRepository.refresh` calls `sofia.virtualTable(code)`; `Unavailable` twice in a row → `ErrorKind.UNAVAILABLE` (backoff as NETWORK). Remove API-key logic, `NO_KEY` widget kind, key/test UI, `effectiveApiKey`.
- [ ] Onboarding gains a first card: "Sofia stop list" with progress/"Ready (N stops)"/Retry; `App.onCreate` triggers `refreshIfOlderThan(30d)` in the background.
- [ ] Settings: theme, interval, permissions, stop list status + "Update now", debug fake data. Fake data uses `"*"`-aware lines.
- [ ] `./gradlew assembleDebug testDebugUnitTest lintDebug` green, 0 warnings. Commit.

### Task 6: Setup flow (stop → lines → directions)

**Files:** `ui/addentry/AddEntryFlow.kt`

- [ ] Step 1: search field (numeric keyboard hint off; accepts code or name), results "NAME · code" with Latin subtitle + mode labels; state for Missing/Downloading/Failed stop list.
- [ ] Step 2: one `virtualTable(code)` call → distinct lines (badge in `color`, white text, mode label). Empty → "No vehicles right now. Try again during service hours" and a single option "All lines" (`lineRef = "*"`). `Unavailable` → "Sofia data unavailable. Try again later."
- [ ] Step 3: per line, destinations from the same response (`st_name_en`, keyed by `last_stop`) + "All directions". Save `Entry` per Task 2 mapping.
- [ ] Build green. Commit.

### Task 7: Review and on-device verification

- [ ] Whole-branch review (fresh reviewer, Review Focus above).
- [ ] User checklist: both apps installed side by side; group by code ("0328") and by Latin name; live minutes; clear app data cookies (Settings → "Reset session" debug button) → recovers; countdown ticks; logcat one request per stop per tick.
