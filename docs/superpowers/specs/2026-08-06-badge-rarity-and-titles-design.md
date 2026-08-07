# Badge rarity, titles, and badges on the friends list

Follow-up to [the badges design](2026-08-06-badges-design.md). That change shipped
eleven badges that all look identical — one trophy, gold if earned, grey if not —
and live on exactly one screen.

## Problem

Two things are missing, and they are the same thing.

**Nothing distinguishes a hard badge from an easy one.** `1 klubb` and
`Hele Norge` render with the same icon in the same gold. The collection game's
whole premise is that some things are hard to collect; the reward screen says
otherwise.

**Badges are invisible where comparison happens.** The friends leaderboard is the
one screen built for looking at other people, and it shows two bare numbers. A
badge nobody else can see is a private note, not an achievement.

## A correction to the previous spec

The badges design said badges on the friends view "needs a per-friend stats
endpoint that does not exist." **That was wrong.** `FriendDto` already carries
`totalCourses` and `totalRounds` for every friend, `FriendService.getLeaderboard`
already returns them, and `SocialView.js` already renders both. Eight of the
eleven badges are computable for friends today with no new endpoint.

Only the three Fylker badges need data that genuinely is not there.

## Rarity is one number per badge

Each badge carries a `rarity` from 1 to 11, unique across the set. Tier bands sit
on top of it. A single unique integer gives a total order for free, so nothing
downstream needs tiebreak logic.

| Tier | Rarity | Badges |
| --- | --- | --- |
| Bronse | 1–3 | 1 klubb · 10 runder · 1 fylke |
| Sølv | 4–6 | 10 klubber · 50 runder · 25 klubber |
| Gull | 7–9 | 5 fylker · 100 runder · 50 klubber |
| Legendarisk | 10–11 | 100 klubber · Hele Norge |

**`Hele Norge` takes rarity 11, above `100 klubber`, and that is forced rather
than aesthetic.** Completing all 15 fylker means playing all 182 active clubs, so
the two badges are *always* earned in the same instant. A title that names "your
rarest badge" needs a deterministic winner between them or it flickers on the
strength of array order.

The tier bands are contiguous and cover 1–11 with no gaps. A rarity outside a
band is a bug, and there is a test for it.

The tier definitions are the only place colour and icon are decided:

| Tier | Colour | MUI icon |
| --- | --- | --- |
| Bronse | `#B87333` | `MilitaryTech` |
| Sølv | `#90A4AE` | `WorkspacePremium` |
| Gull | `#FFD700` | `EmojiEvents` |
| Legendarisk | gradient `#A64AC9 → #FF6F61`, solid `#A64AC9` where a gradient will not go | `Diamond` |

Gull is `#FFD700` — the gold `Overview.js` already uses for a completed fylke —
rather than the slightly deeper `#E6B800` in the approved mockup. Matching the
existing app gold matters more than the mockup's exact hex; flip it in the tier
table if it reads too bright next to the new colours.

The badge tuples become objects (`{ target, label, rarity }`). A third positional
element was one too many to read.

## `computeBadges` stops knowing about `regionStats`

This is the one structural change, and it is what lets the friends list reuse the
thresholds instead of copying them.

Today `computeBadges` takes the `/api/overview` body and digs `regionStats` out of
it. A friend row has counts, not a region map, and synthesising a fake
`regionStats` to express "3 of 15 complete" would be grim. So the input becomes
normalised:

```
computeBadges({ clubs, rounds, fylkerComplete, fylkerTotal })
  -> [{ group, label, hint, current, target, earned, rarity, tier }]
```

`tier` is the resolved tier object (name, colour, icon), not just its name — every
consumer needs all three, and resolving it once in the function beats each caller
looking it up.

A `fromDashboard(stats)` adapter converts the dashboard response, keeping the
`playedCount === totalCount` fylke rule in one place: `fylkerComplete` is the count
of regions satisfying it, `fylkerTotal` is `Object.keys(regionStats).length` — which
remains `Hele Norge`'s target, still read from the data rather than hardcoded. The
friends path passes its fields straight in. Two callers, one set of thresholds.

**Rarity cross-cuts the groups; it does not replace them.** `group` stays in the
returned shape and the dialog keeps its three `Klubber` / `Fylker` / `Runder`
headings in that order. Rarity drives colour, icon and the title — not the layout.

A sibling function:

```
badgeTitle(badges) -> the earned badge with the highest rarity, or null
```

`null` for an account that has earned nothing — a new user gets no title line
rather than an empty one.

## Backend: two fields and a bug

**`FriendDto` gains `fylkerComplete` and `fylkerTotal`.** `fylkerTotal` is the same
for every row, which is redundant on the wire, but the endpoint returns a bare
`List<FriendDto>` and wrapping it in a response object to hoist one integer is a
larger change than the redundancy costs. `SocialView` stays self-contained — no
second fetch.

Counting a user's completed fylker asks, for each county with active courses,
whether the user's played set covers all of them. Rather than express that as a
grouped SQL aggregate, `getLeaderboard` loads `courseRepository.findByActiveTrue()`
**once** for the whole request and reuses the county grouping for every row; each
row then costs the existing `playedCourseRepository.findCourseIdsByUserId` and a
set intersection in Java.

That one played-ids set answers both questions — completed fylker *and* the
active-only played count — so it replaces today's `countByUserId` instead of adding
to it. No new SQL to get wrong, and the leaderboard goes from 2N queries to 2N + 1.

Null counties bucket under `"Unknown"`, mirroring `GolfService.getDashboardStats`
exactly, so a fylke count means the same thing on both screens.

A query per row remains. Marked with a `ponytail:` comment naming the ceiling and
the fix — fetch every row's played ids in one `where user_id in (...)` query —
rather than building that now.

**The bug is worth fixing whether or not badges ship.** `mapToDto` counts played
courses with `playedCourseRepository.countByUserId`, which includes courses the
club reconciler has since deactivated. `getDashboardStats` deliberately counts
only active ones — that is the fix behind the "34 av 30 and a percentage above
100" comment in `GolfService`. So a friend's `BANER` figure is already inflated
relative to your own, and badges would inherit the inflation: a friend could wear
a title they have not earned. Needs an active-only count, and the same count must
back both surfaces.

## UI

**Badges dialog** — each badge renders in its tier's colour and icon. Gull and
legendarisk rows get a tinted background; legendarisk additionally gets a gradient
label and a small `LEGENDARISK` caption. Locked badges stay grey with their
progress hint, exactly as now — tier styling applies to earned badges only, so the
dialog still reads as "here is what you have and here is what is next."

**Friend row** — the avatar gains a ring in the tier colour and a small
tier-coloured gem in its bottom-right corner; the title renders under the display
name, gradient for legendarisk. A friend with no earned badges gets neither, so a
new account's row is pixel-identical to today's.

The tier ring goes on the avatar, whose border is currently decorative white
(`SocialView.js:453-456`). It does not collide with the green `2px solid #4CAF50`
that marks your own row — that border is on the row's `Paper`
(`SocialView.js:424-427`), not the avatar.

## Testing

- `rarity` is unique across all eleven badges, and every value 1–11 is used.
- The tier bands cover 1–11 with no gaps and no overlaps.
- `badgeTitle` returns `Hele Norge`, not `100 klubber`, for an account that has
  earned both — the forced case above.
- `badgeTitle` returns `null` for an account with nothing earned.
- `computeBadges` produces the same eleven badges from `fromDashboard(stats)` as
  from an equivalent hand-built normalised input, pinning the adapter.
- A friend with `fylkerComplete: 0` earns no Fylker badge but still earns club and
  round badges — the friends path is not silently all-or-nothing.
- Backend: the active-only count excludes a played-but-deactivated course, mirroring
  the existing `GolfServiceActiveCoursesTest` case for the dashboard.

## Not doing

- **User-selectable titles.** The title is your rarest badge, computed. Letting
  people choose needs a column on `user`, a PATCH endpoint, and server-side
  validation that they earned it — the first badge state in the database, and the
  thing the whole derived design has avoided so far.
- **Flavour titles distinct from badge labels** (`100 klubber` unlocking
  "Landeveisrytter"). Worth wanting; it is a copy project, not a code one, and it
  can land later by adding one field to the definition table.
- **Badges for non-friends.** `searchUsers` returns `FriendDto` too, but a stranger's
  achievements are not obviously public. Out of scope until asked for.
- **Fixing the 3N query pattern.** Named above, deliberately deferred.
- **Animation on unlock.** Needs to know *when* a badge was earned, which needs the
  table the previous spec declined to build.
