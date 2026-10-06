# Departures Sofia

A personal Android home-screen widget that shows **live arrivals** for the stops and lines you care
about in Sofia: bus, trolleybus, tram and metro. **No API key needed.** Sideloaded, never
published; built for one family.

```
┌──────────────────────────────────────┐
│ Борово                               │
│ БЛ. 214 Ж.К. БОРОВО                  │
│ [204]  4 min               15     25 │   ← red: bus 204
│ [ 2 ]  4 min               18     28 │   ← blue: trolleybus 2
│ ──────────────────────────────────── │
│ Ж.К. БОРОВО                          │
│ [ 9 ]  0                    7     15 │   ← "0" pulses: arriving now
│ ──────────────────────────────────── │
│ УЛ. Т. КАБЛЕШКОВ                     │
│ [ 7 ]  8 min               18     27 │   ← orange: tram 7
│ [27 ]  3 min               13     23 │
│ Updated 31 s ago                  ⟳  │
└──────────────────────────────────────┘
```

Sister app: **Departures Paris** ([`../paris`](../paris)). The widget, refresh loop and
setup flow are the same; only the data source differs. Both install side by side: this app's id is
`fr.departures.sofia`.

---

## Contents

1. [Features](#features)
2. [Quick start](#quick-start)
3. [Using the app](#using-the-app)
4. [Reading the widget](#reading-the-widget)
5. [Where the data comes from](#where-the-data-comes-from)
6. [How refreshing works](#how-refreshing-works)
7. [Testing on your phone](#testing-on-your-phone)
8. [Troubleshooting](#troubleshooting)
9. [Project layout](#project-layout)
10. [Development](#development)

---

## Features

- **Groups.** A group is a titled, hand-picked list of `(stop, line, direction)` entries, for example
  "Борово" = bus 204 + trolleybus 2 at БЛ. 214, trolleybus 9 at Ж.К. БОРОВО, trams 7 and 27
  at УЛ. Т. КАБЛЕШКОВ. Each widget shows one group.
- **Search stops your way:**
  - by the **code on the sign** (`0328`, metro `18`)
  - by **name in Cyrillic** (`величков`)
  - by **name in Latin** (`velichkov`, `bul k vel`)
- **All modes, metro included.** Badges use the line colours from Sofia's own data.
- **The next arrival stands out:** it is large and bold, and the 2nd and 3rd are smaller and grey.
- **Countdown between fetches.** Sofia publishes whole minutes, which the app turns into absolute
  times on arrival, so the widget keeps counting down on its own.
- **Resizable:**
  - 1 row tall: compact, with station names inline
  - 2+ rows tall: station headers and a footer
  - Width decides how many arrivals fit.
- **Only fetches while the home screen is showing**, one request per stop no matter how many
  widgets use it.
- **Light / Dark** theme, chosen in the app.

## Quick start

### 1. Build and install

You need Android Studio, or JDK 17 + Android SDK + platform-tools. Then:

```bash
./gradlew assembleDebug        # → app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # installs on the phone connected over adb
```

On Windows, use `gradlew.bat`. APKs are never committed; build one or get it from whoever built it.

There's no key to configure and nothing to put in `local.properties` besides the SDK path that
Android Studio writes itself.

### 2. First launch

The setup screen does two things:

1. **Downloads the Sofia stop list** (official GTFS feed). The phone reads about **130 KB**,
   not the whole 19 MB file: the stop list sits at the start of the zip, so the app stops reading
   right after it. You'll see *"Ready. Search stops by name (Cyrillic or Latin) or by the code on
   the sign."*
2. Asks for three permissions. All three matter for refreshing:

| Permission | Why | Without it |
|---|---|---|
| **Usage access** | To know when the home screen is in front | Refreshes whenever the phone is unlocked, even inside other apps |
| **Alarms & reminders** | Checks every ~5 s, on time | Updates may lag |
| **Battery optimisation** | Stops the system from killing the refresh loop | Refreshes may stop after a while |

## Using the app

### Example: the "Борово" group

1. Open **Departures Sofia** and tap **Create a group**. Title: `Борово`.
2. Tap **Add station and lines**.
3. **Search.** Any of these works:

   | You type | You get |
   |---|---|
   | `0328` | `БУЛ. К. ВЕЛИЧКОВ · 0328` (exact code) |
   | `720` | `НУ ЗА ТАНЦОВО ИЗКУСТВО · 0720` (padded to 4 digits if no exact match) |
   | `18` | `МЛАДОСТ 3 · 18`, metro (exact; metro codes are short) |
   | `velichkov` / `величков` / `bul k vel` | every БУЛ. К. ВЕЛИЧКОВ stop |
   | `borovo` | Борово stops |

   Results show `NAME · code`, then the Latin name and the modes. Several stops share a name, one per
   side of the street; **the code on the sign tells them apart.**
4. **Lines.** The app makes **one** live call for that stop and lists the lines arriving there, with
   their colours: for example **204** (bus), **2** (trolleybus). Tick what you take.
   - At night you'll see *"No vehicles right now. Try again during service hours."*, with a single
     option, **All lines**: the widget then shows the next vehicles of any line at that stop.
   - If the call fails, there's a **Retry** button.
5. **Directions.** Destinations come from the same call, in Latin, for example `ZH.K. LOZENETS`. Pick one or
   more, or **All directions**. Short-turn trips show up as their own destination.
6. **Add to group.** Repeat for the other stops.

### Example: placing the widget

1. Long-press the home screen → **Widgets** → **Departures · Sofia**.
2. **"Which group?"** opens: pick `Борово`, or **New group**. Backing out cancels the placement.
3. Resize it to suit.

### Settings

| Setting | Options |
|---|---|
| Theme | Light (default) · Dark |
| Refresh while on the home screen | Every 20 s · **30 s** · 60 s |
| Sofia stop list | Number of stops, last update, **Update now**. It also refreshes itself monthly |
| Permissions | Status and shortcuts |
| Debug: Fake departures | Debug builds only. Shows every state without calling the server |
| Debug: Reset Sofia session | Clears the session cookies, to check that the app recovers on its own |

## Reading the widget

| You see | Meaning |
|---|---|
| **`4 min`** (large, bold) | Next arrival, 4 minutes from now |
| `15` (small, grey) | A later arrival, in minutes |
| `16:28` | 60 min or more away, shown as a clock time (Sofia time) |
| **`0`**, green and pulsing | Arriving now. It stays up to a minute, because Sofia only gives whole minutes |
| `—` | No vehicles for this line or direction right now |
| `[All]` badge (grey) | An "All lines" entry: next vehicles of any line at that stop |
| "Updated 31 s ago" | Age of the data. It turns **orange** after 2 minutes or on an error |
| "Sofia data unavailable" | sofiatraffic.bg stopped answering properly (see [Troubleshooting](#troubleshooting)) |
| "This group was deleted…" | The widget's group no longer exists. Tap to open the app |

Sofia data has **no delays, cancellations or platforms**, so you'll never see orange times,
strike-through or a platform box here (unlike the Paris app).

**Tap anywhere on the widget** to refresh it. The refresh icon spins while it fetches.

### Badge colours and shapes

| Mode | Badge | Example |
|---|---|---|
| Bus | red, wide pill | `204`, `310` |
| Trolleybus | blue, wide pill | `2`, `6`, `9` |
| Tram | orange, square corners | `7`, `27` |
| Metro | line colour, circle | `M4` |
| All lines | grey | `All` |

## Where the data comes from

### Live arrivals: sofiatraffic.bg "virtual table" (unofficial)

This is the endpoint the sofiatraffic.bg website uses for its stop boards. It isn't documented
and may change; it's isolated in `data/api/SofiaClient.kt`.

```
GET  https://www.sofiatraffic.bg/bg/public-transport          → cookies: XSRF-TOKEN, sofia_traffic_session
POST https://www.sofiatraffic.bg/bg/trip/getVirtualTable
     X-XSRF-TOKEN: <url-decoded XSRF-TOKEN cookie>
     {"stop":"0328"}
```

Example response (trimmed):

```json
{
  "TB0648_TB6": {
    "name": "6", "ext_id": "TB6", "type": 4, "color": "#2AA9E0",
    "st_name": "Ж.К. ЛОЗЕНЕЦ", "st_name_en": "ZH.K. LOZENETS", "last_stop": "TB0648",
    "details": [ { "t": 7 }, { "t": 19 }, { "t": 29 } ]
  }
}
```

| `type` | Mode |
|---|---|
| 1 | bus |
| 2 | tram |
| 3 | metro |
| 4 | trolleybus |
| 5 | night bus |

- **The session token changes after each request.** The app reads it from its saved cookies before every call.
- **Recovery:**
  - On `302`, `401`, `419` or an HTML page, it redoes the handshake once and retries.
  - If it still fails, the widget says "Sofia data unavailable".
  - If the JSON no longer looks like the format above, that's also reported as unavailable.
- **Politeness:** one request per stop per refresh, spaced out, with backoff on errors.

### Stop list: official GTFS feed

`https://gtfs.sofiatraffic.bg/api/v1/static` (zip). Only `stops.txt` is read.

- It yields 3,519 unique stop codes.
- Bus, trolleybus and tram stops at the same spot share one code (`A0328` and `TB0328` are both `0328`).
- Names are transliterated to Latin with the official Bulgarian scheme:
  `БУЛ. К. ВЕЛИЧКОВ` → `bul. k. velichkov`, `СОФИЯ` → `sofia`, `ЩАСТИЕ` → `shtastie`.

### Fallback

If the virtual table disappears for good, GTFS-realtime
(`https://gtfs.sofiatraffic.bg/api/v1/trip-updates`) has the same data for the whole city in one
file, but no metro. Details are in `docs/sofia-endpoint.md`.

## How refreshing works

Same loop as the Paris app:

```
every ~5 s (alarm that never wakes a sleeping phone)
  ├─ screen off, locked, or another app in front? → do nothing (no network)
  ├─ just came back to the home screen?           → fetch now   (reason: home-entered)
  ├─ last fetch older than the interval?          → fetch       (reason: interval)
  └─ always: redraw the minutes from the cached absolute times
tap on the widget                                  → fetch       (reason: tap)
```

- **One request per stop code**, shared by all widgets.
- **One broken stop doesn't blank the others.** The good stops keep updating; the footer turns orange.
- **Failures back off** (15 s, 30 s, … up to 5 min), and the last data stays on screen.
- **Survives restarts:** the cache and session cookies are on disk.
- **Two apps, two loops.** Paris and Sofia each run their own 5 s check. Each check does no network work
  unless the home screen is in front.

## Testing on your phone

### One-time phone setup

1. **Settings → About phone** → tap **Build number** 7 times.
2. **Developer options** → **USB debugging** on.
3. Plug in, accept the prompt, then run `adb devices`.

### Watching it work

```bash
adb logcat -s DeparturesRefresh DeparturesApi
```

Typical output:

```
DeparturesApi:     stop list: 3519 stops
DeparturesRefresh: fetch reason=home-entered stops=3 1283, 1287, 0742
DeparturesApi:     handshake -> 200, token=true
DeparturesApi:     POST stop=1283 -> 200 1834B 210ms
DeparturesApi:     POST stop=1287 -> 200 912B 188ms
DeparturesApi:     POST stop=0742 -> 200 1290B 195ms
DeparturesRefresh: fetch reason=interval stops=3 ...
DeparturesApi:     POST stop=1283 -> 419 0B 95ms
DeparturesApi:     stop 1283: 419, new session and retry        ← session expired: recovers by itself
DeparturesApi:     handshake -> 200, token=true
DeparturesApi:     POST stop=1283 -> 200 1834B 201ms
DeparturesRefresh: left home (screenOn=false locked=true)       ← screen off: fetching stops
```

(Stop codes above are illustrative.)

### Example test checklist

| Action | Expected |
|---|---|
| Install next to Departures Paris | Two icons (P teal, S red), two entries in the widget picker |
| Search `0328`, then `velichkov` | Same stop found both ways |
| Place the widget, stay on the home screen | `fetch reason=interval` every 30 s, one `POST` per stop |
| Watch for a minute between fetches | Minutes count down; `0` pulses |
| Settings → Reset Sofia session, then tap the widget | `handshake` line, then data again |
| Open another app / lock the phone | No `fetch` lines |
| Airplane mode | Footer turns orange: "Updated 2 min ago" |
| Settings → Fake departures, then tap the widget | `0`, minutes, clock time and `—` all shown |

### Sharing with family

Send `app/build/outputs/apk/debug/app-debug.apk`. On their phone, allow **Install unknown apps** for the app that
opens it. Builds are signed with the committed `app/debug.keystore`, so updates install over old
versions and keep their groups.

## Troubleshooting

| Problem | Try |
|---|---|
| "Sofia data unavailable" | sofiatraffic.bg is down or changed its format. Tap to retry later. If it lasts, see the fallback in `docs/sofia-endpoint.md` |
| One row always shows `—` | That line isn't running now, or the direction you picked no longer appears. Re-add it with **All directions** |
| Search finds nothing | Check Settings → Sofia stop list. If it's missing, tap **Update now** |
| Two stops with the same name | Pick by the **code** printed on the sign at your side of the street |
| Widget stops updating after a while | Settings → Battery optimisation → **Allow**, and see <https://dontkillmyapp.com> |
| Fetches while inside other apps | Allow **Usage access** |

## Project layout

```
app/src/main/java/fr/departures/
├── data/
│   ├── api/       SofiaClient (session + virtual table), parser, persistent cookies
│   └── stops/     GTFS stop list download, transliteration, search index
├── refresh/       5 s tick, home-screen detection, permissions, boot re-arm   (shared with Paris)
├── widget/        Glance widget, badges, time formatting, width fitting      (shared with Paris)
└── ui/            Compose screens: groups, editor, add entry, settings, onboarding
docs/
├── sofia-app-spec.md    The brief for this app
├── sofia-endpoint.md    Verified facts about both data sources
├── superpowers/plans/   Implementation plan
└── icon-preview.png     Paris and Sofia icons, colour and themed
SPEC.md                  The Paris brief this app inherits from
```

The Kotlin package is still `fr.departures`, as in the Paris app. Only the `applicationId`
(`fr.departures.sofia`) differs, which is what lets both apps be installed together.

## Development

```bash
./gradlew testDebugUnitTest      # parser, session handshake (mock server), search, transliteration, widget rules
./gradlew lintDebug

# Checks against the real services: handshake + rotating token, metro stop 18, stop-list streaming
LIVE_SOFIA=1 ./gradlew testDebugUnitTest --tests fr.departures.LiveSofiaTest
```

Example live-test output:

```
LIVE stops=3519 compressedBytesRead=129533
LIVE search 18 -> [МЛАДОСТ 3·18 [METRO]]
LIVE 0328 -> Ok(rows=[VtRow(extId=TB7, name=7, type=4, ..., destination=ZH.K. GOTSE DELTCHEV, minutes=[2, 26]), ...])
LIVE 18 -> Ok(rows=[VtRow(extId=M4, name=M4, type=3, ..., destination=SLIVNITSA, minutes=[7, 16, 25]), ...])
```

### Regenerating the icon

Needs `pip install cairosvg fonttools pillow`:

```bash
python3 tools/icon/gen_icon.py S "#BD202E" app/src/main/res app/src/main/ic_launcher-playstore.png docs/icon-preview.png
```

### Rules for changes

- Keep `applicationId` (`fr.departures.sofia`), or updates install as a new app and lose groups.
- Nothing city-related goes inside the widget itself; the city shows only in the app name and icon.
- Log with the tags `DeparturesRefresh` / `DeparturesApi`.
