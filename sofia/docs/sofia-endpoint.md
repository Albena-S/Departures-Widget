# Sofia data sources (verified 2026-10-06)

## Live departures: sofiatraffic.bg "virtual table" (unofficial)

The endpoint the sofiatraffic.bg website uses for its stop boards. Undocumented; isolated in
`data/api/SofiaClient.kt` + `VirtualTableParser.kt`.

1. `GET https://www.sofiatraffic.bg/bg/public-transport` → cookies `XSRF-TOKEN`, `sofia_traffic_session`.
2. `POST https://www.sofiatraffic.bg/bg/trip/getVirtualTable`, body `{"stop":"0328"}`,
   header `X-XSRF-TOKEN` = url-decoded `XSRF-TOKEN` cookie. The token **rotates** on responses;
   the client reads it from the cookie jar before every request (verified live: 3 calls in a row).
3. `302` / `401` / `419` / HTML body → new handshake, one retry; still failing → "Sofia data unavailable".

Response: object keyed `"<last_stop>_<ext_id>"`; fields `name`, `ext_id`, `type`, `color`, `st_name`,
`st_name_en`, `last_stop`, `details[].t` (whole minutes; also `ac`, `wheelchairs`, `bikes`).

| `type` | Mode | Seen at |
|---|---|---|
| 1 | bus | 0328 (309, 310) |
| 2 | tram | 0720 (20, 22) |
| 3 | metro | 18 (M4), 302/303 (M3) |
| 4 | trolleybus | 0328 (6, 7) |
| 5 | night bus | 0720 (N4) |

Metro **is covered**. Metro stop codes are short (`18`, `302`).

## Stop list: official GTFS static

`https://gtfs.sofiatraffic.bg/api/v1/static` (~19 MB zip, no `Range` support). `stops.txt` is the
**second entry**, so the app streams the zip and stops after it: ~130 KB read (verified live).

- 4,797 rows; kept: `location_type` 0 with a `stop_code` → **3,519 unique codes** (bus, trolley
  and tram stop ids at one physical stop share a code, e.g. `A0328` + `TB0328`).
- `stop_id` prefix → mode: `A` bus, `TB` trolleybus, `TM` tram, `M`/`ME`/`MSt` metro.
- Names are Cyrillic; search also matches the official Latin transliteration.

## Fallback

GTFS-realtime trip updates: `https://gtfs.sofiatraffic.bg/api/v1/trip-updates` (whole city, ~900 KB,
no metro). Only if the virtual table disappears.
