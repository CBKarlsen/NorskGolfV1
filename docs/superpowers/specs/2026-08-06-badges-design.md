# Badges

## Problem

The collection game has a scoreboard but no rewards. `Overview` reports how many
clubs and fylker you have collected, and the fylke ranking says what to finish
next, but nothing ever names an achievement. Milestones that a collector
actually feels — the first club, the tenth, the first whole fylke — pass
silently. The avatar menu, meanwhile, holds exactly one item ("Logg ut"), so
there is a natural home for a profile surface that does not exist yet.

## Scope

Frontend, plus two small changes in `GolfService`/`DashboardStats`. Badges are
**derived**, not stored: a pure function over the `/api/overview` response
decides what is earned. No entity, no repository, no migration, no backfill.

The cost of that choice is deliberate and named in *Not doing*: no earned-on
date, no unlock notification, no badges on the friends view.

## Backend

Two changes, net a smaller `DashboardStats`.

**Add `roundCount`.** `getDashboardStats` already loads every round to build
`recentRounds` and then discards the list after `.limit(5)`. Capture the list
into a local, set `roundCount` from its `size()`, and keep taking 5 for
`recentRounds`. No new query.

**Delete `bestScore`.** The field is declared, has a getter and setter, is
serialized on every `/api/overview` response — and is never set, so it is
permanently `0`. Nothing reads it: `MapView` computes its own per-course best
from `/api/rounds`. A field that always lies is worse than no field.

There is deliberately no score-quality badge. `Round` stores a raw stroke count,
`Course` has `holes` but no `par`, and a round does not record how many holes
were played — so "under par" cannot be computed and "under 80" would be unlocked
for free by a 9-hole round.

## Badge definitions

`frontend/src/badges.js` holds a flat array of definitions and one exported
function. No class, no registry, no plugin seam for the one caller.

```
computeBadges(stats) -> [{ id, label, hint, current, target, earned }]
```

| Group  | Thresholds  | Source                                            |
| ------ | ----------- | ------------------------------------------------- |
| Klubber | 1, 10, 25, 50, 100 | `stats.totalPlayed`                        |
| Fylker  | 1, 5, alle         | count of `regionStats` entries with `playedCount === totalCount` |
| Runder  | 10, 50, 100        | `stats.roundCount`                         |

Labels: `Første klubb`, `Ti klubber`, `25 klubber`, `50 klubber`,
`100 klubber`; `Første fylke`, `Fem fylker`, `Hele Norge`; `Ti runder`,
`Femti runder`, `Hundre runder`. They live in the definition table, so rewording
them is a one-line edit.

Two rules the implementation must not get wrong:

- **"Fullført" means the same thing everywhere.** The fylke badges reuse
  `splitRegions`' definition — `playedCount === totalCount` — rather than
  restating it. `GolfService` counts against active courses only, so a
  deactivated club cannot block a fylke.
- **"Hele Norge" derives its target.** The target is
  `Object.keys(regionStats).length`, not the literal 15 that
  `golf_clubs.json` happens to contain today. The club list is edited by hand;
  a hardcoded 15 becomes wrong the first time a fylke is added or emptied.

A related trap worth knowing: `GolfService` groups clubs with a null county
under `"Unknown"`. Every one of the 182 clubs currently has a county, so no such
bucket exists — but if one ever appears it becomes a 16th "fylke" that
`Hele Norge` would require, and it can never be completed by name. Nothing in
this change guards against that; the fix belongs in the club list.

## UI

**`App.js`** — the avatar `Menu` gains a `Merker` item above `Logg ut`, holding
one boolean of state for the dialog. The menu only renders when `user` is set,
so there is no guest story to write.

**`BadgesDialog.js`** — an MUI `Dialog` that fetches `/api/overview` itself when
opened, keeping the badge state entirely inside the component. `Overview.js` is
untouched. This trades one extra request — only when the dialog is actually
opened, by which point the JVM is warm — against lifting overview state into
`App` and re-plumbing both consumers.

Every badge renders, earned or not. Locked ones are greyed and show their
progress (`7 / 10 klubber`); the reward loop is seeing what is next, so hiding
locked badges would remove the reason the screen exists. Badges group under
their three headings in the order of the table above.

## Testing

`badges.test.js` covers `computeBadges` directly, since the thresholds are the
feature:

- a zero-progress account earns nothing and reports honest `current` values
- crossing a threshold exactly (`totalPlayed === 10`) earns that badge
- `Hele Norge` is earned only when every fylke is complete, and its `target`
  tracks the number of fylker in the input rather than a constant

One render test opens `BadgesDialog` against a stubbed `/api/overview` and
asserts an earned badge and a locked badge with its progress caption both
appear.

## Not doing

- **A `user_badge` table.** Ship the derived version; add persistence when a
  concrete need arrives. Nothing is wasted when it does — `computeBadges`
  becomes the awarding rule the service calls.
- **Earned-on dates and unlock notifications.** Both need the table above.
- **Badges on the friends/leaderboard view.** Needs a per-friend stats endpoint
  that does not exist.
- **An "all 18-hole clubs" badge.** 11 clubs have no hole count, `CourseDto`
  does not carry `holes`, and the missing-data rule would have to be invented
  before the badge means anything.
- **A "same course 5 times" badge.** It rewards replaying one club, which is the
  opposite of what the collection game asks people to do.
