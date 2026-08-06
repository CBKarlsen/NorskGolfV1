# Badge Rarity, Titles, and Friends-List Badges Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make rare badges look rare, give every player a title from their rarest badge, and show both on the friends leaderboard.

**Architecture:** Rarity is a unique integer 1–11 per badge; tier bands sit over it and carry colour, icon and treatment. `computeBadges` moves from taking the dashboard response to a normalised `{ clubs, rounds, fylkerComplete, fylkerTotal }`, with a `fromDashboard` adapter — that is what lets the friends list reuse the thresholds instead of copying them. Badges stay derived: still no table, still no persisted state.

**Tech Stack:** Spring Boot 3 / JUnit 5 / Mockito on the backend; React 19, Material UI 7, Jest + React Testing Library on the frontend.

**Spec:** `docs/superpowers/specs/2026-08-06-badge-rarity-and-titles-design.md`

**Branch:** `feat/badge-rarity`, stacked on `feat/badges` (PR #20, unmerged).

## Global Constraints

- **No new dependencies.** MUI and `@mui/icons-material` are already present. Do not run `npm install`.
- **All user-facing copy is Norwegian (bokmål).**
- **Biome formatting: tabs for indent, double quotes.** Run `npm run lint` from `frontend/` before every frontend commit; it fixes in place, so re-stage. CI runs `npm run lint:ci`, which fails instead of fixing.
- **Frontend tests need `CI=true`**: `CI=true npm test -- <file>` for one, `CI=true npm test` for all. Without it the runner stays in watch mode and never exits.
- **Backend tests:** `./mvnw test -Dtest=ClassName#methodName` from `backend/`, `./mvnw test` for all.
- **Do not edit `backend/src/main/resources/static/`** — generated output.
- **Tier colours are exactly:** Bronse `#B87333`, Sølv `#90A4AE`, Gull `#FFD700`, Legendarisk `#A64AC9` with gradient `linear-gradient(90deg, #A64AC9, #FF6F61)`. Gull matches the gold `Overview.js` already uses for a completed fylke.
- **Rarity assignments are exactly:** Første klubb 1, Ti runder 2, Første fylke 3, Ti klubber 4, Femti runder 5, 25 klubber 6, Fem fylker 7, Hundre runder 8, 50 klubber 9, 100 klubber 10, Hele Norge 11.
- **Badge labels do not change.** All eleven keep the exact text they ship with today.
- **Display grouping is unchanged:** the dialog keeps its `Klubber` → `Fylker` → `Runder` headings. Rarity drives colour, icon and title — never layout order.

## Deviation from the spec, decided while writing this plan

The spec says a friend's completed-fylke count needs "one native query per user", making the leaderboard 3N queries, with the N+1 left as a documented ceiling.

**Do it in Java instead.** `FriendService.getLeaderboard` loads `courseRepository.findByActiveTrue()` **once** for the whole request and reuses it for every row. Per row it then calls the existing `playedCourseRepository.findCourseIdsByUserId`, and intersects. That single played-ids set yields *both* the completed-fylke count and the active-only played count — so it replaces today's `countByUserId` rather than adding to it.

Result: no new SQL to get wrong, the active-count bug is fixed by construction with the same rule `GolfService` uses, and the leaderboard goes from 2N queries to 2N + 1 instead of 3N. Task 3 implements this; the spec has been updated to match.

---

### Task 1: Rarity, tiers, the normalised input, and `badgeTitle`

The whole logic change lands here. `badges.js` gains rarity and tiers, swaps its input shape, and grows two exported siblings. Nothing renders yet — Task 2 consumes it.

**Files:**
- Modify: `frontend/src/badges.js` (rewrite — the file is 77 lines)
- Test: `frontend/src/badges.test.js` (rewrite — every existing test passes the old input shape)

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces, all named exports from `frontend/src/badges.js`:
  - `TIERS` — array of four tier objects, ascending: `{ name, max, color, Icon, gradient?, tint?, ring? }`. `Icon` is a MUI icon **component**, render it as `<Icon sx={{...}} />`.
  - `computeBadges(counts)` where `counts` is `{ clubs, rounds, fylkerComplete, fylkerTotal }` — returns eleven objects `{ group, label, hint, current, target, earned, rarity, tier }`, in group order Klubber → Fylker → Runder. `tier` is the resolved object from `TIERS`, not a name.
  - `fromDashboard(stats)` — converts an `/api/overview` body to that `counts` shape.
  - `badgeTitle(badges)` — the earned badge object with the highest `rarity`, or `null`.

- [ ] **Step 1: Write the failing tests**

Replace the entire contents of `frontend/src/badges.test.js`:

```js
import { badgeTitle, computeBadges, fromDashboard, TIERS } from "./badges";

const counts = (over = {}) => ({
	clubs: 0,
	rounds: 0,
	fylkerComplete: 0,
	fylkerTotal: 15,
	...over,
});

const find = (badges, label) => badges.find((b) => b.label === label);

test("a brand new account earns nothing and reports honest progress", () => {
	const badges = computeBadges(counts());

	expect(badges.every((b) => !b.earned)).toBe(true);
	expect(find(badges, "Første klubb").hint).toBe("0 / 1 klubb");
});

test("earns a tier the moment the threshold is reached exactly", () => {
	const badges = computeBadges(counts({ clubs: 10 }));

	expect(find(badges, "Ti klubber").earned).toBe(true);
	expect(find(badges, "25 klubber").earned).toBe(false);
	expect(find(badges, "25 klubber").hint).toBe("10 / 25 klubber");
});

test("Hele Norge targets the fylker in the data, not a hardcoded 15", () => {
	const twoFylker = counts({ fylkerComplete: 1, fylkerTotal: 2 });

	expect(find(computeBadges(twoFylker), "Hele Norge").target).toBe(2);
	expect(find(computeBadges(twoFylker), "Hele Norge").earned).toBe(false);
	expect(find(computeBadges(twoFylker), "Første fylke").earned).toBe(true);

	const done = computeBadges(counts({ fylkerComplete: 2, fylkerTotal: 2 }));
	expect(find(done, "Hele Norge").earned).toBe(true);
});

test("no fylker in the data must not read as having finished them all", () => {
	const badges = computeBadges(counts({ fylkerTotal: 0 }));

	expect(find(badges, "Hele Norge").earned).toBe(false);
});

test("counts rounds separately from clubs", () => {
	const badges = computeBadges(counts({ clubs: 4, rounds: 50 }));

	expect(find(badges, "Femti runder").earned).toBe(true);
	expect(find(badges, "Hundre runder").hint).toBe("50 / 100 runder");
	expect(find(badges, "Ti klubber").earned).toBe(false);
});

test("caps the hint so an overshoot does not read as 37 / 10", () => {
	expect(find(computeBadges(counts({ clubs: 37 })), "Ti klubber").hint).toBe(
		"10 / 10 klubber",
	);
});

test("Norwegian takes the singular after 1", () => {
	// Hele Norge's target is data-driven and can legitimately be 1.
	const badges = computeBadges(counts({ fylkerTotal: 1 }));

	expect(find(badges, "Første klubb").hint).toBe("0 / 1 klubb");
	expect(find(badges, "Ti klubber").hint).toBe("0 / 10 klubber");
	expect(find(badges, "Hele Norge").hint).toBe("0 / 1 fylke");
});

test("missing counts degrade to zero rather than NaN", () => {
	// A new frontend against an old backend sees no roundCount at all.
	const badges = computeBadges({});

	expect(badges).toHaveLength(11);
	expect(badges.every((b) => !b.earned)).toBe(true);
	expect(badges.every((b) => !b.hint.includes("NaN"))).toBe(true);
});

test("rarity is a unique 1-11, and every badge resolves to a tier", () => {
	const badges = computeBadges(counts());

	expect(badges.map((b) => b.rarity).sort((a, b) => a - b)).toEqual([
		1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11,
	]);
	// A rarity outside every band would leave tier undefined.
	expect(badges.every((b) => b.tier?.name)).toBe(true);
	// Bands are contiguous, ascending, and end at the highest rarity.
	expect(TIERS.map((t) => t.max)).toEqual([3, 6, 9, 11]);
});

test("the title is Hele Norge, not 100 klubber, when both legendaries are earned", () => {
	// Finishing every fylke means playing every club, so these always land together.
	// Without a unique rarity the winner would depend on array order.
	const badges = computeBadges(
		counts({ clubs: 182, rounds: 300, fylkerComplete: 15, fylkerTotal: 15 }),
	);

	expect(find(badges, "100 klubber").earned).toBe(true);
	expect(badgeTitle(badges).label).toBe("Hele Norge");
	expect(badgeTitle(badges).tier.name).toBe("Legendarisk");
});

test("the title is the rarest earned badge, not the last one earned", () => {
	// 25 klubber is rarity 6; Femti runder is rarity 5.
	const badges = computeBadges(counts({ clubs: 25, rounds: 50 }));

	expect(badgeTitle(badges).label).toBe("25 klubber");
});

test("no title until something is earned", () => {
	expect(badgeTitle(computeBadges(counts()))).toBeNull();
});

test("fromDashboard produces the same badges as the equivalent counts", () => {
	const fylke = (playedCount, totalCount) => ({
		playedCount,
		totalCount,
		courses: [],
	});
	const stats = {
		totalPlayed: 17,
		roundCount: 40,
		regionStats: {
			Oslo: fylke(3, 3),
			Vestfold: fylke(14, 14),
			Agder: fylke(2, 11),
		},
	};

	expect(fromDashboard(stats)).toEqual({
		clubs: 17,
		rounds: 40,
		fylkerComplete: 2,
		fylkerTotal: 3,
	});
	expect(computeBadges(fromDashboard(stats))).toEqual(
		computeBadges(
			counts({ clubs: 17, rounds: 40, fylkerComplete: 2, fylkerTotal: 3 }),
		),
	);
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run from `frontend/`:
```bash
CI=true npm test -- badges.test.js
```
Expected: FAIL — `TIERS`, `fromDashboard` and `badgeTitle` are not exported yet, and the existing `computeBadges` reads `totalPlayed`/`regionStats` rather than the new shape.

- [ ] **Step 3: Rewrite `badges.js`**

Replace the entire contents of `frontend/src/badges.js`:

```js
import DiamondIcon from "@mui/icons-material/Diamond";
import EmojiEventsIcon from "@mui/icons-material/EmojiEvents";
import MilitaryTechIcon from "@mui/icons-material/MilitaryTech";
import WorkspacePremiumIcon from "@mui/icons-material/WorkspacePremium";

// Badges are derived, never stored: each one is a threshold over four counts,
// recomputed on every render. See
// docs/superpowers/specs/2026-08-06-badge-rarity-and-titles-design.md.

// Rarity is a unique 1-11 across the whole set. One integer gives a total order for
// free: badgeTitle needs no tiebreak, and the tiers are just bands over it. The bands
// are ascending and contiguous, so the first band a rarity fits is its tier.
export const TIERS = [
	{ name: "Bronse", max: 3, color: "#B87333", Icon: MilitaryTechIcon },
	{ name: "Sølv", max: 6, color: "#90A4AE", Icon: WorkspacePremiumIcon },
	{
		name: "Gull",
		max: 9,
		color: "#FFD700",
		Icon: EmojiEventsIcon,
		tint: "linear-gradient(90deg, rgba(255,215,0,0.20), transparent)",
	},
	{
		name: "Legendarisk",
		max: 11,
		color: "#A64AC9",
		Icon: DiamondIcon,
		gradient: "linear-gradient(90deg, #A64AC9, #FF6F61)",
		tint: "linear-gradient(90deg, rgba(166,74,201,0.18), rgba(255,111,97,0.10), transparent)",
		ring: "inset 0 0 0 1px rgba(166,74,201,0.35)",
	},
];

const tierFor = (rarity) => TIERS.find((t) => rarity <= t.max);

// ponytail: three sources cover all eleven badges, so the value accessor is written
// once per group rather than once per badge.
const GROUPS = [
	{
		name: "Klubber",
		unit: "klubber",
		unitSingular: "klubb",
		value: (c) => c.clubs ?? 0,
		tiers: [
			{ target: 1, label: "Første klubb", rarity: 1 },
			{ target: 10, label: "Ti klubber", rarity: 4 },
			{ target: 25, label: "25 klubber", rarity: 6 },
			{ target: 50, label: "50 klubber", rarity: 9 },
			{ target: 100, label: "100 klubber", rarity: 10 },
		],
	},
	{
		name: "Fylker",
		unit: "fylker",
		unitSingular: "fylke",
		value: (c) => c.fylkerComplete ?? 0,
		tiers: [
			{ target: 1, label: "Første fylke", rarity: 3 },
			{ target: 5, label: "Fem fylker", rarity: 7 },
			// golf_clubs.json is hand-edited: 15 fylker today, not necessarily
			// tomorrow. Read the goal out of the data instead of hardcoding it.
			// Rarity 11 puts this above 100 klubber deliberately — finishing every
			// fylke means playing every club, so both are earned in the same instant
			// and the title needs a deterministic winner.
			{ target: (c) => c.fylkerTotal ?? 0, label: "Hele Norge", rarity: 11 },
		],
	},
	{
		name: "Runder",
		unit: "runder",
		unitSingular: "runde",
		value: (c) => c.rounds ?? 0,
		tiers: [
			{ target: 10, label: "Ti runder", rarity: 2 },
			{ target: 50, label: "Femti runder", rarity: 5 },
			{ target: 100, label: "Hundre runder", rarity: 8 },
		],
	},
];

export function computeBadges(counts) {
	return GROUPS.flatMap((group) => {
		const current = group.value(counts);

		return group.tiers.map(({ target: goal, label, rarity }) => {
			const target = typeof goal === "function" ? goal(counts) : goal;
			const unit = target === 1 ? group.unitSingular : group.unit;

			return {
				group: group.name,
				label,
				rarity,
				tier: tierFor(rarity),
				hint: `${Math.min(current, target)} / ${target} ${unit}`,
				current,
				target,
				// target === 0 means the goal does not exist yet (no fylker loaded).
				// Without this guard 0 >= 0 would award "Hele Norge" to everyone.
				earned: target > 0 && current >= target,
			};
		});
	});
}

// The dashboard speaks regionStats; a friend row speaks plain counts. Normalising here
// keeps computeBadges to one input shape and the "fullført" rule to one place.
//
// That rule counts fylker where playedCount === totalCount, matching splitRegions in
// Overview.js for every fylke the backend produces (GolfService groups by actual
// courses, so each has at least one). The totalCount > 0 guard rejects the degenerate
// zero-course case; unreachable now, but a safer invariant than silently counting
// empty fylker complete.
export function fromDashboard(stats) {
	const regionStats = stats?.regionStats ?? {};

	return {
		clubs: stats?.totalPlayed ?? 0,
		rounds: stats?.roundCount ?? 0,
		fylkerComplete: Object.values(regionStats).filter(
			(s) => s.totalCount > 0 && s.playedCount === s.totalCount,
		).length,
		fylkerTotal: Object.keys(regionStats).length,
	};
}

// Your title is your rarest earned badge. Null until you have earned one, so a new
// account shows no title line rather than an empty one.
export function badgeTitle(badges) {
	return badges.reduce(
		(best, b) => (b.earned && (!best || b.rarity > best.rarity) ? b : best),
		null,
	);
}
```

- [ ] **Step 4: Run the tests to verify they pass**

Run from `frontend/`:
```bash
CI=true npm test -- badges.test.js
```
Expected: PASS, 13 tests.

`BadgesDialog.test.js` will now fail — the dialog still calls `computeBadges(stats)` with the raw response. That is expected and Task 2 fixes it. Do not touch `BadgesDialog.js` in this task.

- [ ] **Step 5: Lint**

Run from `frontend/`:
```bash
npm run lint
```
Expected: no errors. Re-stage anything it rewrote.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/badges.js frontend/src/badges.test.js
git commit -m "Give badges a rarity, a tier, and a title"
```

---

### Task 2: Tier styling in the badges dialog

**Files:**
- Modify: `frontend/src/BadgesDialog.js`
- Test: `frontend/src/BadgesDialog.test.js`

**Interfaces:**
- Consumes: `computeBadges(counts)`, `fromDashboard(stats)` and the `tier` field from Task 1. Each badge's `tier` carries `{ name, color, Icon, gradient?, tint?, ring? }`; `Icon` is a component.
- Produces: nothing new. The dialog's props are unchanged.

- [ ] **Step 1: Write the failing test**

Append these two tests to `frontend/src/BadgesDialog.test.js` (keep the four already there):

```js
test("marks the rarest badges with their tier caption", async () => {
	// Everything earned: Hele Norge is legendarisk and says so.
	global.fetch = jest.fn(() =>
		Promise.resolve({
			ok: true,
			json: () =>
				Promise.resolve({
					totalPlayed: 182,
					roundCount: 300,
					regionStats: {
						Oslo: { playedCount: 3, totalCount: 3, courses: [] },
					},
				}),
		}),
	);

	render(<BadgesDialog open onClose={() => {}} />);

	expect(await screen.findByText("Hele Norge")).toBeInTheDocument();
	expect(screen.getByText("LEGENDARISK")).toBeInTheDocument();
});

test("locked badges show no tier caption", async () => {
	global.fetch = jest.fn(() =>
		Promise.resolve({
			ok: true,
			json: () =>
				Promise.resolve({
					totalPlayed: 1,
					roundCount: 0,
					regionStats: {
						Oslo: { playedCount: 0, totalCount: 3, courses: [] },
					},
				}),
		}),
	);

	render(<BadgesDialog open onClose={() => {}} />);

	expect(await screen.findByText("Første klubb")).toBeInTheDocument();
	expect(screen.queryByText("LEGENDARISK")).not.toBeInTheDocument();
});
```

- [ ] **Step 2: Run the tests to verify they fail**

Run from `frontend/`:
```bash
CI=true npm test -- BadgesDialog.test.js
```
Expected: FAIL. The two new tests fail on the missing `LEGENDARISK` caption, and the four pre-existing tests **also** fail now — Task 1 changed `computeBadges`'s input shape and the dialog has not caught up. All six should be green by Step 4.

- [ ] **Step 3: Update `BadgesDialog.js`**

Three edits.

1. Change the import line:

```js
import { computeBadges } from "./badges";
```

to:

```js
import { computeBadges, fromDashboard } from "./badges";
```

2. In the `useEffect`, change:

```js
				if (!cancelled) setBadges(computeBadges(stats));
```

to:

```js
				if (!cancelled) setBadges(computeBadges(fromDashboard(stats)));
```

3. Replace the whole badge-row `.map(...)` block — from `{badges` on the line beginning `.filter((b) => b.group === group)`'s parent through its closing `))}` — so the group body reads:

```js
							{badges
								.filter((b) => b.group === group)
								.map((badge) => {
									const { Icon, color, gradient, tint, ring } = badge.tier;

									return (
										<Box
											key={badge.label}
											sx={{
												display: "flex",
												alignItems: "center",
												gap: 1.5,
												py: 0.75,
												px: badge.earned && tint ? 1 : 0,
												borderRadius: 2,
												background: badge.earned ? tint : undefined,
												boxShadow: badge.earned ? ring : undefined,
											}}
										>
											<Icon sx={{ color: badge.earned ? color : "#dcdcdc" }} />
											<Box>
												<Typography
													variant="body2"
													sx={{
														fontWeight: badge.earned ? 700 : 400,
														color: badge.earned ? "#333" : "#999",
														// Gradient text needs the colour transparent, so it
														// has to win over the line above it.
														...(badge.earned && gradient
															? {
																	fontWeight: 800,
																	background: gradient,
																	backgroundClip: "text",
																	WebkitBackgroundClip: "text",
																	color: "transparent",
																}
															: {}),
													}}
												>
													{badge.label}
												</Typography>
												{/* Only the top tier names itself — captioning all four
												    would turn the list into a legend. */}
												{badge.earned && gradient && (
													<Typography
														variant="caption"
														sx={{
															display: "block",
															fontWeight: 700,
															letterSpacing: "0.08em",
															fontSize: "0.62rem",
															color,
														}}
													>
														{badge.tier.name.toUpperCase()}
													</Typography>
												)}
												{/* Seeing what is next is the point, so locked badges
												    keep their progress instead of being hidden. */}
												{!badge.earned && (
													<Typography variant="caption" sx={{ color: "#aaa" }}>
														{badge.hint}
													</Typography>
												)}
											</Box>
										</Box>
									);
								})}
```

Note the `.map((badge) => {` now has a function body and an explicit `return`, where before it was a concise arrow returning JSX directly.

- [ ] **Step 4: Run the tests to verify they pass**

Run from `frontend/`:
```bash
CI=true npm test -- BadgesDialog.test.js
```
Expected: PASS, 6 tests.

- [ ] **Step 5: Run the whole frontend suite, lint, build**

Run from `frontend/`:
```bash
CI=true npm test && npm run lint && npm run build
```
Expected: all suites green, no lint errors, build succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/BadgesDialog.js frontend/src/BadgesDialog.test.js
git commit -m "Colour the badges dialog by tier"
```

---

### Task 3: Per-friend fylke counts, and the active-course count bug

**Files:**
- Modify: `backend/src/main/java/fritids/norskgolf/dto/FriendDto.java`
- Modify: `backend/src/main/java/fritids/norskgolf/service/FriendService.java` (`getLeaderboard` and `mapToDto`, around lines 133–165)
- Test: `backend/src/test/java/fritids/norskgolf/service/FriendServiceTest.java`

**Interfaces:**
- Consumes: nothing from earlier tasks.
- Produces: two new fields on the `/api/friends` JSON — `fylkerComplete` and `fylkerTotal`, both `int`. Task 4 reads them. `totalCourses` changes meaning: it now counts **active** courses only, matching `totalPlayed` on `/api/overview`.

Background the implementer needs: `mapToDto` is private with exactly two call sites, both inside `getLeaderboard`. `searchUsers` and `getPendingRequests` build `FriendDto` through the other constructors and are not affected.

- [ ] **Step 1: Write the failing tests**

`FriendServiceTest` currently mocks only `UserRepository` and `FriendshipRepository` — the leaderboard needs three more. Add these mock fields next to the existing two:

```java
    @Mock private PlayedCourseRepository playedCourseRepository;
    @Mock private RoundRepository roundRepository;
    @Mock private CourseRepository courseRepository;
```

and these imports:

```java
import fritids.norskgolf.entities.Course;
import fritids.norskgolf.repository.CourseRepository;
import fritids.norskgolf.repository.PlayedCourseRepository;
import fritids.norskgolf.repository.RoundRepository;
```

Then add to the class:

```java
    private static Course course(long id, String county) {
        Course c = new Course();
        c.setId(id);
        c.setCounty(county);
        c.setActive(true);
        return c;
    }

    @Test
    void leaderboardCountsOnlyActiveCoursesSoAFriendsTotalMatchesTheirOwnDashboard() {
        // Course 99 is not in findByActiveTrue: the club reconciler deactivated it.
        // Counting it would inflate the friend's BANER figure above what /oversikt shows.
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(course(1L, "Oslo")));

        User me = user(1);
        when(friendshipRepository.findAllFriends(1L)).thenReturn(List.of());
        when(playedCourseRepository.findCourseIdsByUserId(1L)).thenReturn(List.of(1L, 99L));
        when(roundRepository.countByUserId(1L)).thenReturn(7L);

        FriendDto meRow = friendService.getLeaderboard(me).get(0);

        assertEquals(1, meRow.getTotalCourses());
        assertEquals(7, meRow.getTotalRounds());
    }

    @Test
    void leaderboardReportsHowManyFylkerEachRowHasFinished() {
        // Oslo is fully played, Vestfold is not. Two fylker exist in total.
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(
                course(1L, "Oslo"), course(2L, "Oslo"),
                course(3L, "Vestfold"), course(4L, "Vestfold")));

        User me = user(1);
        when(friendshipRepository.findAllFriends(1L)).thenReturn(List.of());
        when(playedCourseRepository.findCourseIdsByUserId(1L)).thenReturn(List.of(1L, 2L, 3L));
        when(roundRepository.countByUserId(1L)).thenReturn(3L);

        FriendDto meRow = friendService.getLeaderboard(me).get(0);

        assertEquals(1, meRow.getFylkerComplete());
        assertEquals(2, meRow.getFylkerTotal());
    }

    @Test
    void leaderboardLoadsTheCourseListOnceRatherThanPerRow() {
        // One query for the whole leaderboard, not one per friend.
        when(courseRepository.findByActiveTrue()).thenReturn(List.of(course(1L, "Oslo")));

        User me = user(1);
        User friend = user(2);
        Friendship f = new Friendship();
        ReflectionTestUtils.setField(f, "id", 10L);
        f.setRequester(me);
        f.setReceiver(friend);
        when(friendshipRepository.findAllFriends(1L)).thenReturn(List.of(f));
        when(playedCourseRepository.findCourseIdsByUserId(anyLong())).thenReturn(List.of(1L));
        when(roundRepository.countByUserId(anyLong())).thenReturn(1L);

        assertEquals(2, friendService.getLeaderboard(me).size());
        verify(courseRepository, times(1)).findByActiveTrue();
    }
```

Add the two static imports the last test needs, alongside the existing Mockito imports:

```java
import static org.mockito.Mockito.times;
```

(`anyLong`, `verify` and `when` are already imported.)

If `Friendship` exposes a public `setId`, use it instead of `ReflectionTestUtils` — check the entity first and prefer the setter.

- [ ] **Step 2: Run the tests to verify they fail**

Run from `backend/`:
```bash
./mvnw test -Dtest=FriendServiceTest
```
Expected: COMPILATION FAILURE — `cannot find symbol: method getFylkerComplete()`.

- [ ] **Step 3: Add the two fields to `FriendDto`**

In `FriendDto.java`, add the fields after `avatar`:

```java
    private int fylkerComplete;
    private int fylkerTotal;
```

Extend the full constructor to take them (it becomes ten parameters — the class has no builder and adding one is out of scope here):

```java
    public FriendDto(String id, String displayName, String email, String status, Long friendshipId,
                     int totalCourses, int totalRounds, String avatar,
                     int fylkerComplete, int fylkerTotal) {
        this.id = id;
        this.displayName = displayName;
        this.email = email;
        this.status = status;
        this.friendshipId = friendshipId;
        this.totalCourses = totalCourses;
        this.totalRounds = totalRounds;
        this.avatar = avatar;
        this.fylkerComplete = fylkerComplete;
        this.fylkerTotal = fylkerTotal;
    }
```

Update the short convenience constructor to match:

```java
    public FriendDto(String id, String displayName, String status, Long friendshipId) {
        this(id, displayName, null, status, friendshipId, 0, 0, null, 0, 0);
    }
```

Add the getters beside the others:

```java
    public int getFylkerComplete() { return fylkerComplete; }
    public int getFylkerTotal() { return fylkerTotal; }
```

Then fix any other call site the compiler flags in `FriendService` (`searchUsers` and `getPendingRequests` may use the 8-argument form — pass `0, 0` for the new fields there; a search result does not display badges).

- [ ] **Step 4: Rework `getLeaderboard` and `mapToDto`**

Add the repository to `FriendService`'s injected fields, beside the existing ones:

```java
    @Autowired private CourseRepository courseRepository;
```

Add these imports:

```java
import fritids.norskgolf.entities.Course;
import fritids.norskgolf.repository.CourseRepository;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
```

Replace `getLeaderboard` and `mapToDto` with:

```java
    // --- 6. LEADERBOARD ---
    public List<FriendDto> getLeaderboard(User me) {
        // Load the active-course picture ONCE for the whole leaderboard, then reuse it for
        // every row. Doing it per row would be a query per friend for data identical on
        // each one.
        List<Course> activeCourses = courseRepository.findByActiveTrue();
        Set<Long> activeIds = activeCourses.stream()
                .map(Course::getId)
                .collect(Collectors.toSet());
        // Null counties bucket under "Unknown", exactly as GolfService.getDashboardStats
        // does, so a fylke count means the same thing on both screens.
        Map<String, Set<Long>> coursesByCounty = activeCourses.stream()
                .collect(Collectors.groupingBy(
                        c -> c.getCounty() != null ? c.getCounty() : "Unknown",
                        Collectors.mapping(Course::getId, Collectors.toSet())));

        List<FriendDto> leaderboard = friendshipRepository.findAllFriends(me.getId()).stream()
                .map(f -> {
                    User friend = f.getRequester().getId().equals(me.getId()) ? f.getReceiver() : f.getRequester();
                    return mapToDto(friend, "ACCEPTED", f.getId(), activeIds, coursesByCounty);
                })
                .collect(Collectors.toList());

        // Add Me
        leaderboard.add(mapToDto(me, "ME", null, activeIds, coursesByCounty));

        // Sort
        leaderboard.sort(Comparator.comparingInt(FriendDto::getTotalCourses).reversed());
        return leaderboard;
    }

    // --- HELPERS ---

    private FriendDto mapToDto(User user, String status, Long friendshipId,
                               Set<Long> activeIds, Map<String, Set<Long>> coursesByCounty) {
        // One played-ids query per row, and it answers both questions below. Counting
        // played courses with countByUserId instead would include courses the club
        // reconciler has since deactivated, inflating this row above what the same user
        // sees on /oversikt — GolfService counts active only.
        // ponytail: still a query per row. If a friend list ever gets long, fetch every
        // row's played ids in one "where user_id in (...)" query instead.
        Set<Long> played = new HashSet<>(playedCourseRepository.findCourseIdsByUserId(user.getId()));

        int activePlayed = (int) activeIds.stream().filter(played::contains).count();
        int fylkerComplete = (int) coursesByCounty.values().stream()
                .filter(played::containsAll)
                .count();

        return new FriendDto(
                user.getPublicId(),
                resolveDisplayName(user),
                user.getEmail(),
                status,
                friendshipId,
                activePlayed,
                (int) roundRepository.countByUserId(user.getId()),
                user.getAvatar(),
                fylkerComplete,
                coursesByCounty.size()
        );
    }
```

- [ ] **Step 5: Run the tests to verify they pass**

Run from `backend/`:
```bash
./mvnw test -Dtest=FriendServiceTest
```
Expected: PASS, 8 tests.

- [ ] **Step 6: Run the whole backend suite**

Run from `backend/`:
```bash
./mvnw test
```
Expected: BUILD SUCCESS, 52 tests.

- [ ] **Step 7: Commit**

```bash
git add backend/src/main/java/fritids/norskgolf/dto/FriendDto.java \
        backend/src/main/java/fritids/norskgolf/service/FriendService.java \
        backend/src/test/java/fritids/norskgolf/service/FriendServiceTest.java
git commit -m "Give the leaderboard fylke counts, and count only active courses"
```

---

### Task 4: Titles and tier rings on the friends list

**Files:**
- Modify: `frontend/src/SocialView.js` (the leaderboard row, around lines 415–520)
- Test: `frontend/src/SocialView.test.js` (create — the file does not exist)

**Interfaces:**
- Consumes: `computeBadges(counts)` and `badgeTitle(badges)` from Task 1, and `fylkerComplete` / `fylkerTotal` on each friend from Task 3. `badgeTitle` returns a badge object whose `tier` carries `{ name, color, Icon, gradient? }`, or `null`.
- Produces: nothing.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/SocialView.test.js`:

```js
import { render, screen } from "@testing-library/react";
import SocialView from "./SocialView";

const friend = (over) => ({
	id: "public-2",
	displayName: "Kari Nordmann",
	email: "kari@example.com",
	status: "ACCEPTED",
	friendshipId: 10,
	totalCourses: 0,
	totalRounds: 0,
	fylkerComplete: 0,
	fylkerTotal: 15,
	avatar: null,
	...over,
});

// SocialView fetches /api/friends and /api/friends/requests together on mount.
// Anything else answers with an empty list.
const stubFriends = (rows) => {
	global.fetch = jest.fn((url) =>
		Promise.resolve({
			ok: true,
			json: () => Promise.resolve(url === "/api/friends" ? rows : []),
		}),
	);
};

test("shows a friend's rarest badge as their title", async () => {
	// 182 clubs and every fylke: Hele Norge outranks 100 klubber.
	stubFriends([
		friend({
			totalCourses: 182,
			totalRounds: 300,
			fylkerComplete: 15,
			fylkerTotal: 15,
		}),
	]);

	render(<SocialView user={{ name: "Meg" }} />);

	expect(await screen.findByText("Kari Nordmann")).toBeInTheDocument();
	expect(screen.getByText("Hele Norge")).toBeInTheDocument();
});

test("a friend who has earned nothing gets no title", async () => {
	stubFriends([friend()]);

	render(<SocialView user={{ name: "Meg" }} />);

	expect(await screen.findByText("Kari Nordmann")).toBeInTheDocument();
	expect(screen.queryByText("Første klubb")).not.toBeInTheDocument();
});

test("a friend with clubs but no finished fylke still gets a title", async () => {
	// The friends path must not be all-or-nothing: 25 clubs earns a title even
	// though every Fylker badge is still locked.
	stubFriends([friend({ totalCourses: 25, totalRounds: 4, fylkerComplete: 0 })]);

	render(<SocialView user={{ name: "Meg" }} />);

	expect(await screen.findByText("25 klubber")).toBeInTheDocument();
});
```

`SocialView` takes a single `user` prop (`SocialView.js:34`) and calls both endpoints with `{ credentials: "include" }` as a second argument, which the stub above ignores.

- [ ] **Step 2: Run the test to verify it fails**

Run from `frontend/`:
```bash
CI=true npm test -- SocialView.test.js
```
Expected: FAIL — "Hele Norge" is not rendered, because the row shows no title yet.

- [ ] **Step 3: Add the title and the tier ring**

In `frontend/src/SocialView.js`, add the import beside the other local imports:

```js
import { badgeTitle, computeBadges } from "./badges";
```

Add `Box` to the MUI imports if it is not already there (it is used below).

Inside the `friends.map((friend, index) => {` callback, immediately after the existing `const isMe = friend.status === "ME";`, add:

```js
										const title = badgeTitle(
											computeBadges({
												clubs: friend.totalCourses,
												rounds: friend.totalRounds,
												fylkerComplete: friend.fylkerComplete,
												fylkerTotal: friend.fylkerTotal,
											}),
										);
										const TierIcon = title?.tier.Icon;
```

Replace the `<ListItemAvatar>` block with:

```js
													<ListItemAvatar>
														<Box
															sx={{ position: "relative", width: 40, height: 40 }}
														>
															<Avatar
																src={
																	friend.avatar ||
																	`https://ui-avatars.com/api/?name=${friend.displayName}`
																}
																sx={{
																	width: 40,
																	height: 40,
																	// The green "this is you" marker is the row's
																	// Paper border, not this one, so a tier ring
																	// here collides with nothing.
																	border: title
																		? `2px solid ${title.tier.color}`
																		: "2px solid white",
																	boxShadow: "0 2px 4px rgba(0,0,0,0.1)",
																}}
															/>
															{title && (
																<Box
																	sx={{
																		position: "absolute",
																		bottom: -3,
																		right: -4,
																		width: 19,
																		height: 19,
																		borderRadius: "50%",
																		bgcolor: "#fff",
																		display: "flex",
																		alignItems: "center",
																		justifyContent: "center",
																		boxShadow: "0 1px 3px rgba(0,0,0,0.25)",
																	}}
																>
																	<TierIcon
																		sx={{ fontSize: 13, color: title.tier.color }}
																	/>
																</Box>
															)}
														</Box>
													</ListItemAvatar>
```

Then, inside the `<Box sx={{ flex: 1 }}>` that holds the display name, add the title directly after the name `<Typography>` and before the `{isMe && (` chip:

```js
															{title && (
																<Typography
																	variant="caption"
																	sx={{
																		display: "block",
																		fontWeight: 700,
																		lineHeight: 1.3,
																		...(title.tier.gradient
																			? {
																					background: title.tier.gradient,
																					backgroundClip: "text",
																					WebkitBackgroundClip: "text",
																					color: "transparent",
																				}
																			: { color: title.tier.color }),
																	}}
																>
																	{title.label}
																</Typography>
															)}
```

- [ ] **Step 4: Run the test to verify it passes**

Run from `frontend/`:
```bash
CI=true npm test -- SocialView.test.js
```
Expected: PASS, 3 tests.

- [ ] **Step 5: Run the whole frontend suite, lint, build**

Run from `frontend/`:
```bash
CI=true npm test && npm run lint && npm run build
```
Expected: all suites green, no lint errors, build succeeds.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/SocialView.js frontend/src/SocialView.test.js
git commit -m "Show each friend's title and tier on the leaderboard"
```

---

### Task 5: Verify against a running app

Manual, produces no commit. Tests cannot catch a gradient that renders as invisible text or a 19px gem that lands off the avatar.

**Files:** none.

- [ ] **Step 1: Start both halves**

From `backend/`:
```bash
./mvnw spring-boot:run
```
From `frontend/`, in a second shell:
```bash
npm start
```

- [ ] **Step 2: Check the dialog**

At `http://localhost:3000`, logged in: avatar → **Merker**. Confirm four distinct icons down the list, each earned badge in its tier colour, a tinted row behind earned gull and legendarisk badges, and — only if you have earned one — a gradient label with a `LEGENDARISK` caption. Locked badges stay grey with their `n / m` hint.

Gradient text is the specific risk: if a label renders invisible, `WebkitBackgroundClip` is not applying and the `color: transparent` is showing through against white.

- [ ] **Step 3: Check the friends list**

Go to `/venner`. Confirm each friend with a badge has a tier-coloured avatar ring, a gem in the bottom-right of the avatar, and their title under their name. Confirm a friend with no badges looks exactly as before. Narrow to a phone viewport and confirm the extra title line does not push the `BANER` / `RUNDER` numbers out of the row.

- [ ] **Step 4: Cross-check the numbers**

Your own `BANER` figure on `/venner` must now equal `SPILTE BANER` on `/oversikt`. Before this change they could differ, because the leaderboard counted deactivated courses. If they still differ, that is a real bug in Task 3 — report it rather than adjusting either number to match.

- [ ] **Step 5: Report**

Report what you saw, including any tier that reads badly against the app's green.

---

## Not in this plan

Everything in the spec's *Not doing* section: no user-selectable titles, no flavour titles distinct from labels, no badges for non-friends, no fix for the per-row query pattern, no unlock animation.
