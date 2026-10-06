# Departures Paris

A personal Android home-screen widget that shows **live departures** for the stops and lines you
care about in Île-de-France: metro, RER, Transilien, tram and bus. Data comes from IDFM's
real-time API (PRIM). Sideloaded, never published; built for one family.

```
┌──────────────────────────────────────┐
│ Paris nord                           │
│ CERNAY                               │
│ [H]  0 [2]                 28  16:28 │   ← "0" pulses: the train is at the platform
│ ──────────────────────────────────── │
│ ERMONT HALTE                         │
│ [H] 12 min [2]             42     54 │   ← [2] = platform 2
│ Updated just now                  ⟳  │
└──────────────────────────────────────┘
```

Sister app: **Departures Sofia** ([`../sofia`](../sofia)). It uses the same widget with Sofia data,
and both install side by side.

---

## Contents

1. [Features](#features)
2. [Quick start](#quick-start)
3. [Using the app](#using-the-app)
4. [Reading the widget](#reading-the-widget)
5. [How refreshing works](#how-refreshing-works)
6. [API key and quota](#api-key-and-quota)
7. [Testing on your phone](#testing-on-your-phone)
8. [Troubleshooting](#troubleshooting)
9. [Project layout](#project-layout)
10. [Development](#development)

---

## Features

- **Groups.** A group is a titled, hand-picked list of `(stop, line, direction)` entries, for example
  "Paris nord" = Cernay (H) + Ermont Halte (H), towards Paris. Each widget shows one group.
- **The next departure stands out:** it is large and bold, and the 2nd and 3rd are smaller and grey.
- **Platform** next to the next departure, in an outlined box (`[2]`), when IDFM publishes it.
- **States at a glance:**
  - `0`, pulsing: at the platform or less than a minute away
  - orange: delayed
  - struck through: cancelled
  - a clock time such as `16:28`: 60 minutes or more away
  - `—`: no departures
- **Resizable.** The layout uses the widget's real size:
  - 1 row tall: compact, with station names inline
  - 2+ rows tall: station headers and a footer
  - Width decides how many departures fit.
- **Saves quota and battery.** It only fetches while the **home screen is showing**, one request per
  stop no matter how many widgets use it.
- **Light / Dark** theme, chosen in the app (not taken from the system).
- **Official line colours**, drawn as badges whose shape follows the mode:
  - metro: circle
  - RER / Transilien: rounded square
  - tram: rectangle
  - bus: wide pill

## Quick start

### 1. Get an API key

1. Create an account on <https://prim.iledefrance-mobilites.fr> and generate an API key.
2. Optional: check that the key works.

```bash
curl -H "apikey: YOUR_KEY" -H "Accept: application/json" \
  "https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring?MonitoringRef=STIF:StopArea:SP:43105:"
```

A JSON answer that starts with `{"Siri":{"ServiceDelivery":...` means the key works.

### 2. Put the key in `local.properties`

`local.properties` sits in the project root and is git-ignored. **Never commit it.**

```properties
sdk.dir=C\:\\Users\\you\\AppData\\Local\\Android\\Sdk
IDFM_API_KEY=your-key-here
```

The app also builds **without** a key. The widget then says "Add your API key in the app", and
you can paste one in **Settings** instead. That's also how each family member can use their own key.

### 3. Build and install

You need Android Studio, or JDK 17 + Android SDK + platform-tools. Then:

```bash
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # installs on the phone connected over adb
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

Prefer not to build? Sideload an APK someone built for you; see
[Sharing with family](#sharing-with-family). APKs are never committed.

### 4. First launch

The app walks you through three permissions. All three matter for refreshing:

| Permission | Why | Without it |
|---|---|---|
| **Usage access** | To know when the home screen is in front | Refreshes whenever the phone is unlocked, even inside other apps (more quota) |
| **Alarms & reminders** | Checks every ~5 s, on time | Updates may lag |
| **Battery optimisation** | Stops the system from killing the refresh loop | Refreshes may stop after a while |

## Using the app

### Example: the "Paris nord" group

1. Open **Departures Paris** and tap **Create a group**. Title: `Paris nord`.
2. Tap **Add station and lines**.
3. **Search**: type `Cernay`. Each result shows the station name, then town, modes and lines,
   for example **Cernay** / `Ermont — RER · Train — C, H`. Rail stations come before bus stops.
4. **Lines**: tick **H**.
5. **Directions**: the app makes one live call and lists today's destinations, for example
   `Paris Nord` and `Pontoise`. Pick `Paris Nord`, or **All directions**.
   If nothing is running (at night), you'll see *"No departures right now, so directions can't be
   listed. You can still pick All directions."*
6. **Add to group**. Repeat for `Ermont Halte` → **H** → `Paris Nord`.

### Example: placing the widget

1. Long-press the home screen → **Widgets** → **Departures · Paris**.
2. Drop it on the home screen. **"Which group?"** opens: pick `Paris nord`, or **New group** to
   make one on the spot. Backing out cancels the placement.
3. Resize it to suit (see [Sizes](#sizes)).

From then on, all editing happens in the app. Rename a group, reorder entries or add a line, and
every widget showing that group updates.

### Settings

| Setting | Options |
|---|---|
| Theme | Light (default) · Dark. Applies to the app and every widget |
| Refresh while on the home screen | Every 20 s · **30 s** · 60 s |
| API key | Paste your own key. **Test API key** checks it against Cernay |
| Permissions | Status and shortcuts for the three permissions above |
| Debug: Fake departures | Debug builds only. Shows every state without calling the API |

## Reading the widget

| You see | Meaning |
|---|---|
| **`4 min`** (large, bold) | Next departure, 4 minutes from now (minutes are rounded down) |
| `28` (small, grey) | A later departure, in minutes |
| `16:28` | 60 min or more away, shown as a clock time (24 h, Paris time) |
| **`0`**, green and pulsing | At the platform, or less than a minute away |
| orange time | Delayed by at least a minute |
| ~~`12`~~ (grey, struck through) | Cancelled. It still counts as one of the 3 shown, so you can see a train vanished |
| `—` | No departures returned for this line or direction |
| `[2]` | Platform 2 (only when IDFM publishes it) |
| "Updated 12 s ago" | Age of the data. It turns **orange** after 2 minutes or on an error |
| "Rate limit, retrying soon" | The API said 429. The app backs off and keeps the last data |
| "Add your API key in the app" | No key, or the key was refused. Tap to open Settings |
| "This group was deleted…" | The widget's group no longer exists. Tap to open the app |

**Tap anywhere on the widget** to refresh it. The refresh icon spins while it fetches.

### Sizes

```
1 row tall, wide                          1 row tall, narrow
┌──────────────────────────────────────┐  ┌────────────────────────┐
│ Paris nord                      15 s │  │ Paris nord        15 s │
│ [H] Cernay           4 min   34 12:55│  │ [H] Cernay  4 min   34 │
│ [H] Ermont Halte    20 min   49 13:09│  │ [H] Ermont… 20 min  49 │
└──────────────────────────────────────┘  └────────────────────────┘
```

- **1 row tall:** compact rows, station name inline after the badge, and the data's age next to the title.
- **2+ rows tall:** station headers with a thin line between stations, plus a footer (example at the top).
- **Width:** the next departure always shows. Then, as room allows: the station name, the 2nd
  departure, the 3rd.
- **Too many rows:** the list scrolls; the title and footer stay put.

## How refreshing works

Android doesn't let widgets refresh every 30 s through the normal mechanisms, so this app runs
its own loop:

```
every ~5 s (alarm that never wakes a sleeping phone)
  ├─ screen off, locked, or another app in front? → do nothing (no network)
  ├─ just came back to the home screen?           → fetch now   (reason: home-entered)
  ├─ last fetch older than the interval?          → fetch       (reason: interval)
  └─ always: redraw the minutes from the cached absolute times
tap on the widget                                  → fetch       (reason: tap)
```

- **One request per stop.** If two widgets both include Cernay, Cernay is fetched once per refresh.
- **Failures back off:** network errors wait 15 s, then 30 s, … up to 5 min. 429 waits 30 s up to
  10 min, and a bad key waits 1 min up to 30 min. The last data stays on screen meanwhile.
- **Survives restarts.** The cache is on disk, so the widget redraws right after a reboot or app
  update, and the loop restarts by itself.
- **Known limit:** Android can't tell which home-screen *page* is showing, so a widget on page 2
  also refreshes while you're on page 1.

## API key and quota

Some PRIM keys allow only **1,000 requests/day** (and 5/second); older ones allow 1,000,000.
Check yours on PRIM's "My API usage" page.

Rough cost: **2 stops × every 30 s × 3 h on the home screen ≈ 720 requests/day per phone.**
To use less:

- Use the 60 s interval in Settings.
- Keep groups small; stops shared between widgets cost nothing extra.
- Give each family member their own key (Settings → API key).

## Testing on your phone

### One-time phone setup

1. **Settings → About phone** → tap **Build number** 7 times.
2. **Developer options** → **USB debugging** on. Wireless debugging also works: `adb pair`, then
   `adb connect`.
3. Plug in, accept the prompt, then run `adb devices`.

### Watching it work

```bash
adb logcat -s DeparturesRefresh DeparturesApi
```

Typical output:

```
DeparturesRefresh: fetch reason=home-entered stops=2 STIF:StopArea:SP:43105:, STIF:StopArea:SP:47920:
DeparturesApi:     GET STIF:StopArea:SP:43105: -> 200 count=14 312ms
DeparturesApi:     GET STIF:StopArea:SP:47920: -> 200 count=9 287ms
DeparturesRefresh: fetch reason=interval stops=2 ...
DeparturesRefresh: left home (screenOn=true locked=false)      ← opened another app: fetching stops
DeparturesRefresh: fetch reason=home-entered stops=2 ...        ← back home: immediate refresh
DeparturesRefresh: fetch reason=tap stops=2 ...
DeparturesRefresh: NETWORK: backing off 15s (failure #1)
```

### Example test checklist

| Action | Expected |
|---|---|
| Open another app for a minute | No `fetch` lines |
| Lock the phone / screen off | No `fetch` lines |
| Return to the home screen | `fetch reason=home-entered` right away |
| Stay on the home screen | `fetch reason=interval` every 30 s |
| Tap the widget | `fetch reason=tap`, spinner on the refresh icon |
| Airplane mode | Footer turns orange: "Updated 2 min ago" |
| Wrong key in Settings | Widget says "Add your API key in the app" |
| Settings → Fake departures, then tap the widget | Delayed / cancelled / `0` / clock time / `—` all shown |
| Screenshot | `adb exec-out screencap -p > shot.png` |

### Sharing with family

Send `app/build/outputs/apk/debug/app-debug.apk`. On their phone, allow **Install unknown
apps** for the app that opens it (Files, Drive…). Every build is signed with the committed
`app/debug.keystore`, so **new versions install over old ones** and keep their groups.

## Troubleshooting

| Problem | Try |
|---|---|
| Widget stops updating after a while | Settings → Battery optimisation → **Allow**. See <https://dontkillmyapp.com> for your phone brand |
| Updates only every few minutes | Allow **Alarms & reminders** |
| Fetches while inside other apps | Allow **Usage access** |
| "No live data for this line at this stop" | That stop/line isn't in IDFM's real-time coverage. Try the neighbouring station |
| Footer orange, "Updated 6 min ago" | Network problem; the app is backing off and will retry. Tap to force a retry |
| "Rate limit, retrying soon" | Daily quota used up. Use the 60 s interval, or a second key |
| No platform box | IDFM doesn't publish the platform for that stop |

## Project layout

```
app/src/main/java/fr/departures/
├── data/            Groups, settings, departure cache (DataStore + JSON)
│   └── api/         PRIM client + SIRI Lite parser, IDFM open-data search
├── refresh/         5 s tick, home-screen detection, permissions, boot re-arm
├── widget/          Glance widget, badges, time formatting, width fitting, tap action
└── ui/              Compose screens: groups, editor, add entry, settings, onboarding
docs/
├── idfm-ids.md      How dataset ids map to API ids (verified)
├── widget-design.md Colours, type, wireframes, design decisions
├── icon-preview.png Paris and Sofia icons, colour and themed
└── superpowers/plans/  Implementation plan
tools/icon/gen_icon.py  Generates the launcher icon (shared with the Sofia app)
SPEC.md              The original brief
```

**Stack:**
- Kotlin, AGP 9, compile/target SDK 37, min SDK 26
- Jetpack Glance (widget), Compose Material 3 (app)
- Ktor + kotlinx.serialization
- DataStore
- No Hilt, no Room

## Development

```bash
./gradlew testDebugUnitTest      # JVM tests: SIRI parsing, time formatting, filters, width fitting
./gradlew lintDebug
```

### Regenerating the icon

Needs `pip install cairosvg fonttools pillow`:

```bash
python3 tools/icon/gen_icon.py P "#0B6E7F" app/src/main/res app/src/main/ic_launcher-playstore.png docs/icon-preview.png
```

### Ids

IDFM ids are checked against the live datasets; see `docs/idfm-ids.md`. For example:

```
arrets-lignes stop_id IDFM:monomodalStopPlace:43105  →  MonitoringRef=STIF:StopArea:SP:43105:
arrets-lignes id      IDFM:C01737                    →  LineRef STIF:Line::C01737:  (Transilien H)
```

### Rules for changes

- Keep `applicationId` (`fr.departures`) and the DataStore file names, or updates will install as a new app
  and lose groups.
- Log with the tags `DeparturesRefresh` / `DeparturesApi`.
- Never commit `local.properties` or a key.
