# Curated club list

**Date:** 2026-08-05
**Status:** approved, not yet implemented

## Why

GolfJakten is a collection game: you play Norwegian golf clubs and watch your coverage grow. The club list is therefore game rules, not reference data. Two failures follow from getting it wrong, and both hit the core mechanic:

- The progress figure lies. "34 of 160" is meaningless if the real number of clubs is ~180.
- A missing club cannot be logged at all. A user who played Ålesund has nowhere to record it.

Today's list comes from OpenStreetMap via the Overpass API — whatever volunteers happened to tag `leisure=golf_course`. A spot-check of 29 well-known clubs found 7 absent: Larvik, Moss, Hamar, Gjøvik, Ålesund, Molde, Sarpsborg.

County is also wrong for some clubs. `CourseSyncService.estimateCounty` assigns fylke from hardcoded lat/lon rectangles, so anything near a boundary lands in the wrong region and anything unmatched becomes "Andre fylker" — while the overview page reports progress *by county*.

## Decisions

| Question | Decision |
|---|---|
| Source of truth | Curated list in the repo, assembled from NGF/provgolf data |
| Unit of collection | One entry per **club**, not per course |
| Per-club data | Name, coordinates, municipality, county, hole count |
| Courses missing from the list | Deactivated, never deleted |

One entry per club matches how golfers speak ("I've played Miklagard") and mirrors Fjelltoppjakten, where a peak is a place. Hole count is stored so full-size and 9-hole clubs can be distinguished later without a second migration.

NGF publishes no scrapable register: `golfforbundet.no/spiller/klubber-og-baner/finn-en-klubb-ner-deg` redirects to `provgolf.no`, which loads clubs into a map via JavaScript. The list is therefore assembled once by hand and refreshed manually — appropriate for a set that changes about once a year.

## Data

`backend/src/main/resources/golf_clubs.json` replaces `golf_courses.json`:

```json
[
  {
    "clubId": "miklagard-gk",
    "name": "Miklagard Golfklubb",
    "lat": 60.0234,
    "lon": 11.1421,
    "municipality": "Ullensaker",
    "county": "Akershus",
    "holes": 18
  }
]
```

`clubId` is a stable slug owned by this project, not an OSM node id, so later re-imports match on identity rather than heuristics.

`Course` gains three fields:

| Field | Type | Notes |
|---|---|---|
| `municipality` | `String` | New; enables finer-grained goals than fylke |
| `holes` | `Integer` | New; nullable for anything unmatched |
| `active` | `boolean` | New, defaults `true`; false hides a course without deleting it |

`estimateCounty` and its lat/lon rectangles are deleted.

## Import

`CourseSyncService` changes from "seed once if the table is empty" to an idempotent reconcile that runs on every boot:

1. **Match** each list entry to an existing course — by `clubId` where one is already stored, otherwise by normalised name (lowercased, diacritics folded, `golfklubb|gk|golfpark|golfbane` stripped) combined with proximity within 3 km.
2. **Update** matched rows in place. Foreign keys from `played_course` and `round` stay valid because no row is replaced.
3. **Insert** entries with no match.
4. **Deactivate** existing courses that no entry matched. Never delete.
5. **Log** a summary: matched, inserted, deactivated, and every ambiguous case.

A dry-run mode prints the diff without writing, so the first run can be reviewed before it touches production data.

Ambiguity is expected on the first run — "Moss & Rygge" against "Moss Golfklubb", clubs that share a municipality — which is why the dry run exists and why ambiguous matches are logged individually rather than silently resolved.

## Effects on the app

- `GET /api/courses` returns active courses only.
- Completion denominators count active courses only.
- A round logged at a now-inactive course still appears in the user's history; it simply stops counting toward the total.
- Regional progress uses the imported county instead of a guess.

## Testing

Unit tests for the matcher:

- name normalisation across the club-name variants that occur in the real list
- the 3 km proximity rule, including a near-miss that must not match
- an ambiguous case where two candidates are plausible: it is reported, not guessed

An integration test for the import, running outside a transaction against an in-memory database:

- a user with a played course and a logged round keeps both after the import
- an unmatched course is deactivated, not deleted
- a new club is inserted
- the course count is unchanged for entries that only had fields updated

## Rollout

1. Assemble `golf_clubs.json` and check it into the branch.
2. `pg_dump` the Neon database. There is no backup today, and this rewrites the table the entire history depends on.
3. Run the import in dry-run mode against a copy of production data; review the diff by hand.
4. Merge, deploy, confirm the logged summary matches the reviewed diff.
5. Verify the map, overview and per-county progress in the browser while logged in.

## Out of scope

Crowdsourced corrections, a course-detail page, GPS-verified check-ins, badges, and per-municipality goals. Each is a separate piece of work; this one only makes the list correct.
