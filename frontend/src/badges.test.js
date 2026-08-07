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
