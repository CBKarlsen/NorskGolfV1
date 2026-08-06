import { computeBadges } from "./badges";

const fylke = (playedCount, totalCount) => ({
	playedCount,
	totalCount,
	courses: [],
});

const find = (badges, label) => badges.find((b) => b.label === label);

test("a brand new account earns nothing and reports honest progress", () => {
	const badges = computeBadges({
		totalPlayed: 0,
		roundCount: 0,
		regionStats: { Oslo: fylke(0, 3), Vestfold: fylke(0, 14) },
	});

	expect(badges.every((b) => !b.earned)).toBe(true);
	expect(find(badges, "Første klubb").hint).toBe("0 / 1 klubb");
});

test("earns a tier the moment the threshold is reached exactly", () => {
	const badges = computeBadges({
		totalPlayed: 10,
		roundCount: 0,
		regionStats: {},
	});

	expect(find(badges, "Ti klubber").earned).toBe(true);
	expect(find(badges, "25 klubber").earned).toBe(false);
	expect(find(badges, "25 klubber").hint).toBe("10 / 25 klubber");
	// No fylker in the data at all must not read as "you have finished them all".
	expect(find(badges, "Hele Norge").earned).toBe(false);
});

test("Hele Norge targets the fylker in the data, not a hardcoded 15", () => {
	const badges = computeBadges({
		totalPlayed: 15,
		roundCount: 0,
		regionStats: { Oslo: fylke(3, 3), Vestfold: fylke(12, 14) },
	});

	expect(find(badges, "Hele Norge").target).toBe(2);
	expect(find(badges, "Hele Norge").earned).toBe(false);
	expect(find(badges, "Første fylke").earned).toBe(true);

	const finished = computeBadges({
		totalPlayed: 17,
		roundCount: 0,
		regionStats: { Oslo: fylke(3, 3), Vestfold: fylke(14, 14) },
	});

	expect(find(finished, "Hele Norge").earned).toBe(true);
});

test("counts rounds separately from clubs", () => {
	const badges = computeBadges({
		totalPlayed: 4,
		roundCount: 50,
		regionStats: {},
	});

	expect(find(badges, "Femti runder").earned).toBe(true);
	expect(find(badges, "Hundre runder").hint).toBe("50 / 100 runder");
	expect(find(badges, "Ti klubber").earned).toBe(false);
});

test("caps the hint so an overshoot does not read as 37 / 10", () => {
	const badges = computeBadges({
		totalPlayed: 37,
		roundCount: 0,
		regionStats: {},
	});

	expect(find(badges, "Ti klubber").hint).toBe("10 / 10 klubber");
});

test("has all eleven badges, grouped Klubber, then Fylker, then Runder", () => {
	const badges = computeBadges({
		totalPlayed: 0,
		roundCount: 0,
		regionStats: {},
	});

	expect(badges.length).toBe(11);
	expect(badges.map((b) => b.group)).toEqual([
		"Klubber",
		"Klubber",
		"Klubber",
		"Klubber",
		"Klubber",
		"Fylker",
		"Fylker",
		"Fylker",
		"Runder",
		"Runder",
		"Runder",
	]);
});

test("survives an old backend response with no fields at all", () => {
	const badges = computeBadges({});

	expect(badges.every((b) => !b.earned)).toBe(true);
	expect(badges.some((b) => b.hint.includes("NaN"))).toBe(false);
});
