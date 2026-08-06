# Per-fylke completion goals

## Problem

The Overview already lists every fylke with a progress bar, but it reads as a
report rather than a goal. Fylker sort by raw played count, so the one you are
two clubs away from finishing sits below the one where you have simply played
more. Finishing a fylke turns the bar gold and nothing else — the collection
game's most obvious milestone passes without being named.

## Scope

Frontend only. `DashboardStats.RegionStat` already carries `playedCount`,
`totalCount` and the club list per fylke, and `GolfService` counts against
active courses only, so "complete" is already well defined server-side. No API,
entity or migration change.

## Design

`splitRegions(regionStats)` in `Overview.js` turns the region map into two
lists, each row carrying `name`, `stats`, `remaining` and `progress`:

- **inProgress** (`remaining > 0`) — sorted by percent complete descending, then
  by fewest clubs remaining, then by name using the `nb` collation.
- **completed** (`remaining === 0`) — sorted by name.

The percentage leads the ranking so progress is what earns the top slot. Ranking
by fewest-remaining alone would push an untouched one-club fylke above one you
are 12/14 through, which inverts the point of the feature.

Untouched fylker all tie at 0%, and there the fewest-remaining tiebreak is
skipped deliberately: it would order them by club count, which is meaningless
progress information and would leave a logged-out visitor — where every fylke is
at 0% — with a list silently sorted by size. Skipping it drops through to the
name comparison, so untouched fylker read A–Å.

### Rendering

The section keeps its heading and gains a `{completed} av {total} fylker
fullført` subtitle, then splits into two labelled groups:

- **Nærmest mål** — each row shows `12 / 14 · 2 igjen`.
- **Fullført (N)** — gold border, gold bar, a star beside the name, and
  `Fullført — 3 av 3` in place of the count.

Both groups render through one `renderRegion` function, so the accordion body
(the per-club list, the played chips, the map navigation on click) is unchanged
and unduplicated. Either group is omitted when empty, which covers both a brand
new account and a finished one.

## Testing

`Overview.test.js` covers the ranking directly, since the ranking is the
feature: the untouched-fylke ordering, the percentage tiebreak, and the
completed split. One render test mounts `Overview` against a stubbed
`/api/overview` and asserts both group headings, the counter and the
`2 igjen` caption appear.

## Not doing

- A fourth stat tile for the completed count. The subtitle and the `Fullført (N)`
  heading already carry it, and a fourth tile crowds the mobile layout.
- User-chosen goal fylker. That needs backend persistence; the ranking already
  answers "what should I finish next" without asking.

## Known data wart

Stord has two rows in production, deliberately left ambiguous by the club-list
importer and awaiting a manual merge. Until that is resolved, Vestland cannot
reach `Fullført` without both rows being played. This is a data problem, not a
ranking one, and this change neither causes nor hides it.
