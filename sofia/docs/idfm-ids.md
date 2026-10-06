# IDFM id mapping

Verified 2026-10-06 against the Opendatasoft Explore API v2.1
(`https://data.iledefrance-mobilites.fr/api/explore/v2.1/catalog/datasets/...`).

| Dataset value | Used as |
|---|---|
| `arrets-lignes.stop_id` = `IDFM:monomodalStopPlace:43105` | `MonitoringRef=STIF:StopArea:SP:43105:` |
| `arrets-lignes.stop_id` = `IDFM:5252` (a single quay, mostly bus) | `MonitoringRef=STIF:StopPoint:Q:5252:` |
| `arrets-lignes.id` = `IDFM:C01727` | line `C01727`; PRIM `LineRef` = `STIF:Line::C01727:` |
| `referentiel-des-lignes.id_line` = `C01727` | same line (colours: `colourweb_hexa`, `textcolourweb_hexa`, hex without `#`) |

Lines are compared on the bare `C\d{5}` code, so prefix differences between datasets don't matter.

## `arrets-lignes.mode` → app mode

| Dataset | App |
|---|---|
| `Metro` | METRO |
| `RapidTransit` | RER |
| `LocalTrain`, `regionalRail`, `RailShuttle` | TRAIN |
| `Tramway` | TRAM |
| `Bus` | BUS |
| `CableWay`, `Funicular`, other | OTHER |

## Fixtures

- Cernay: `IDFM:monomodalStopPlace:43105` (Ermont). Lines: RER C `C01727`, Transilien H `C01737`.
- Ermont Halte: `IDFM:monomodalStopPlace:47920`. Line: H `C01737`.

## Live check (PRIM)

Pending: needs the API key. Once `IDFM_API_KEY` is in `local.properties`, run the curl in the README
for Cernay and confirm `LineRef` values `STIF:Line::C01727:` / `STIF:Line::C01737:` appear.
