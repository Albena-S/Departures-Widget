# Spec: Sofia Departures, a separate app derived from the Paris (IDFM) app

The Paris app is built and working; see `SPEC.md`. Sofia becomes a **separate, standalone app**, made by copying the Paris project and replacing its data layer. **Widget design, refresh strategy, time formatting, theming and phone-testing setup stay exactly as in `SPEC.md`.** Only the data source and stop search change.

Use the same skills as before (writing-plans → executing-plans → systematic-debugging → verification-before-completion). Verify on the user's phone.

## 0. Creating the new project

- Copy the Paris repo into a new folder/repo (e.g. `sofia-departures`), keeping the git history if convenient. The Paris repo is not modified.
- Change the `applicationId` (e.g. `…departures.sofia`) so **both apps can be installed side by side** on the same phone.
- **Naming and icon**, mirroring the Paris app (see `../paris/docs/paris-app-naming.md`):
  - app name **"Departures Sofia"**
  - widget picker label "Departures · Sofia"
  - Groups screen title "Departures · Sofia"
  - the same icon base as Paris with a bold **"S"** city tag (also present in the `monochrome` layer for themed icons)
  - Sofia Traffic red background (`#BD202E`)
  - the widget picker preview updated to match
  - **Nothing city-related is added inside the widget itself.**
- Remove everything IDFM-specific: the PRIM client, the `apikey` header, the API-key setting and "Test API key" button, `IDFM_API_KEY` in `local.properties`/`BuildConfig`, the PRIM quota handling, and the IDFM open-data search.
- Remove the official IDFM line-colour lookup. Colours now come from the Sofia response (§1).
- **Keep** the usage-access, exact-alarm and battery-optimisation screens: the refresh strategy is unchanged.
- Remove the fields that only made sense for IDFM (`AimedDepartureTime`, cancellation, `VehicleAtStop`) from the departure model, or leave them unused.

## 1. Data source: sofiatraffic.bg "virtual table" (unofficial)

This is the endpoint the official sofiatraffic.bg website uses for its stop arrival boards. It is undocumented and may change, so isolate it behind the provider interface (§3) and fail gracefully.

**Request** (tested and working on 6 Oct 2026):
```
POST https://www.sofiatraffic.bg/bg/trip/getVirtualTable
Content-Type: application/json
X-XSRF-TOKEN: <url-decoded value of the XSRF-TOKEN cookie>
Cookie: XSRF-TOKEN=...; sofia_traffic_session=...

{"stop":"0328"}
```
- `stop` is the 4-digit code printed on the stop sign (keep leading zeros).
- **Session handshake:** without cookies the server answers `302` (redirect). Fix this as follows:
  1. `GET https://www.sofiatraffic.bg/bg/public-transport` once to receive both cookies.
  2. URL-decode `XSRF-TOKEN` and send it as the `X-XSRF-TOKEN` header.
  3. Keep the cookies in a persistent cookie jar. The session lasts about 2 hours.
  4. On a `302`, `401` or `419`, redo the handshake once and retry.
- No API key and no published quota. Be polite: one request per stop per tick, respect the configured interval, and back off on errors.

**Response:** an object keyed by `"<lastStop>_<lineExtId>"` (e.g. `"TB0648_TB6"`). Each value has:

| Field | Meaning | Use |
|---|---|---|
| `name` | line number shown to riders ("6", "310") | badge text |
| `ext_id` | line id ("TB6", "A60") | stable line key |
| `type` | 1 = bus, 4 = trolleybus (verify tram/metro codes on real stops) | mode / badge shape |
| `color` | hex colour ("#2AA9E0") | badge background (text colour: white) |
| `st_name` / `st_name_en` | destination, Cyrillic / Latin | direction filter. Show Latin in the setup UI |
| `last_stop` | destination stop id | stable direction key |
| `details[].t` | **minutes until arrival** | the times shown |

Notes:
- The same line can appear twice with different destinations (short-turn trips). Treat each `(ext_id, last_stop)` as a direction.
- Only whole minutes are returned. There are no clock times, delay, cancellation or at-platform flags. So in Sofia there is **no orange delay and no strike-through**. `t == 0` still shows the pulsing `0`. To keep the countdown working between fetches, convert to absolute times when the data arrives: `fetchTime + t minutes`.
- `≥ 60 min` still shows as clock time (Europe/Sofia zone).
- Check metro stations early: find a metro stop code and confirm it returns data and which `type` it uses. If metro isn't covered, say so in the setup screen for that stop.

## 2. Stop search

The virtual table needs a stop code, but people search by name. Use the **official GTFS static feed** for the stop list:
- `GET https://gtfs.sofiatraffic.bg/api/v1/static`. This is a ~19 MB zip; only `stops.txt` is needed (`stop_code`, `stop_name`, `stop_lat`, `stop_lon`, about 4,800 rows).
- Download it the first time Sofia is used, extract `stops.txt` into the local database, discard the zip, and refresh it monthly in the background.

**Search** should accept:
- the 4-digit code (exact match; this is the fastest path, since the code is on every sign)
- the name in Cyrillic **or Latin**. Transliterate names to Latin using the official Bulgarian scheme ("БУЛ. К. ВЕЛИЧКОВ" → "bul. k. velichkov"), then normalise both sides (lowercase, strip punctuation).

Show results as `name · code`. Several stops share a name (one per side of the street), so the code tells them apart.

**Line and direction picking:** after a stop is chosen, call the virtual table once and list the lines and destinations it returns. The user ticks what they want, or "All directions". If it returns nothing (night), show "No vehicles right now. Try again during service hours" and allow "All lines".

## 3. Code changes

- Replace the IDFM data layer with a `SofiaClient` (virtual table and session handshake) and a `SofiaStopRepository` (GTFS `stops.txt` and search). There is no multi-city abstraction; this app only knows Sofia.
- **Data model:** entries store `stopCode`, `stopName`, `lineExtId`, `lineName`, `mode`, `lineColor` and a set of `lastStop` destination ids (empty = all).
- **Setup flow:** same screens as Paris: stop search, then lines, then directions.
- **Refresh loop:** unchanged. Requests are deduplicated per stop code.
- **Errors:** if the endpoint stops working (repeated non-JSON or HTML responses), the widget footer says "Sofia data unavailable". Log the raw status and body for debugging.
- **First run:** no API key screen. Show the GTFS stop-list download progress instead (§2).

## 4. Done when

- [ ] The Sofia app installs **alongside** the Paris app on the user's phone. Both widgets can be on the home screen at once, and the Paris app is untouched.
- [ ] A Sofia group created by stop code and by Latin name search shows live minutes on the widget, on the user's phone.
- [ ] The session handshake recovers on its own after the cookie expires (test by clearing cookies).
- [ ] The countdown keeps ticking between fetches. `0` pulses.
- [ ] One request per Sofia stop per tick (visible in logcat).

## Fallback if the endpoint disappears

The official GTFS-realtime trip-updates feed (`https://gtfs.sofiatraffic.bg/api/v1/trip-updates`, no key needed) has the same data. But it is a single ~900 KB file for the whole city, and it currently doesn't include the metro. Switch to it only if the virtual table stops working.
