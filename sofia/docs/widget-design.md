# Widget design

Goal: read the **next departure** at a glance. Everything else is quieter.

## Critique of the reference screenshot

- Every time sits in an identical grey chip, so the first train weighs the same as the third.
- The "wifi" live glyph repeats on every time and says nothing (everything here is live).
- Line pills float on a separate row from their times, which costs a full row per line.
- The footer refresh button is a heavy circle for a secondary action.

## Tokens

| Token | Light | Dark | Use |
|---|---|---|---|
| `bg` | `#FBFAF7` | `#16181C` | widget surface (warm paper, not pure white) |
| `ink` | `#15171B` | `#F3F2EF` | title, first departure |
| `ink2` | `#5E636B` | `#A2A7AF` | 2nd/3rd departures, units |
| `ink3` | `#8C9198` | `#6E737B` | station subheaders, footer |
| `rule` | `#ECEAE4` | `#272A30` | hairline between stations |
| `now` | `#0B8A5A` | `#3FD99A` | pulsing `0` |
| `late` | `#D9480F` | `#FF8A47` | delayed time |
| `muted` | `#B4B8BE` | `#4E535B` | cancelled (struck through) |
| `warn` | `#C2410C` | `#FF9F66` | stale/error footer |
| `badgeFallback` | `#8A8F98` / white text | same | missing line colour |

Type (system sans; no font files):

| Role | Size | Weight | Colour |
|---|---|---|---|
| Group title | 15 sp | Bold | ink |
| Station subheader | 11 sp | Medium, upper-case | ink3 |
| First departure number | 22 sp | Bold | ink / now / late / muted |
| First departure unit ("min") | 12 sp | Medium | ink2 |
| 2nd/3rd departure | 14 sp | Medium | ink2 / late / muted |
| Footer | 11 sp | Normal | ink3 / warn |

Spacing: outer padding 14 dp; row height ~34 dp; gap between badge and first time 10 dp;
gap between 2nd and 3rd 12 dp. Corner radius = `system_app_widget_background_radius` (API 31+), 18 dp before.

Badges (generated bitmaps, official colours): metro = circle, RER/Transilien = rounded square,
tram = square-cornered rectangle, bus = wide pill-ish rectangle. Height 22 dp (18 dp in Narrow).
Mode pictograms (14 dp, ink3) before the badge only in Large.

## Wireframes

The widget uses `SizeMode.Exact`: layout comes from the real size, not three fixed buckets.

- **Height < 110 dp → compact:** 26 dp rows, 18 dp badges, no footer; data age sits next to the title.
- **Width decides how much of each row shows**, in this order of priority:
  first departure → station label (only for groups spanning several stations) → 2nd → 3rd departure
  (`fitRow`, unit-tested in `RowFitTest`). Labels go on every row or on none.
- **Station names:** compact → inline right after the badge (no room for subheaders);
  taller → station subheaders with a hairline between stations (preferred on device).

Compact, wide (one group, two stations):

```
┌──────────────────────────────────────┐
│ Paris nord                      15 s │
│ [H] Cernay           4 min   34 12:55│
│ [H] Ermont Halte    20 min   49 13:09│
└──────────────────────────────────────┘
```

Compact, narrow (~2.5 cells): label wins over the 3rd time; at ~2 cells the labels drop.

```
┌────────────────────────┐
│ Paris nord        15 s │
│ [H] Cernay  4 min   34 │
│ [H] Ermont… 20 min  49 │
└────────────────────────┘
```

Tall (footer, station subheaders, 22 dp badges, 22 sp first departure; pictograms from 300 dp wide):

```
┌──────────────────────────────────────┐
│ Paris nord                           │
│ CERNAY                               │
│ [H]  4 min                 34  12:55 │
│ ──────────────────────────────────── │
│ ERMONT HALTE                         │
│ [H] 20 min                 49  13:09 │
│ Updated just now                  ⟳  │
└──────────────────────────────────────┘
```

Platform: an outlined box right after the first departure (`4 min [2]`). Minutes are bare bold
text, the platform is always boxed, so they never read alike. Shown only when IDFM publishes it
(`DeparturePlatformName`, else `ArrivalPlatformName`); it gives way to later times when space is short.

Short widget with few rows: rows switch to full size and are centred vertically, no empty band.

Single-station groups have no labels: `[C] 4 min ........ 19  34`.
Body scrolls (LazyColumn) when rows overflow; title and footer stay fixed.

## Self-critique against SPEC §6

- On device (2026-10-06): short-but-wide size left the right side blank, and two "H" rows needed station names. Fixed with exact sizing and inline labels.
- First departure is ~1.6× the size of the others and full-ink: passes "the one thing that matters".
- No identical chips anywhere; hierarchy title → station → rows → footer comes from size and colour only.
- Pulse via `ViewFlipper` RemoteViews (700 ms fade). Fallback if a launcher doesn't animate: static `0` in `now` green (documented below once tested on the phone).
- To verify on device: contrast of `ink3` footer on `bg` in sunlight; badge legibility at 18 dp.
