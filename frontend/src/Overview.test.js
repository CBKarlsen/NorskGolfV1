import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import Overview, { splitRegions } from "./Overview";

const fylke = (playedCount, totalCount) => ({
	playedCount,
	totalCount,
	courses: [],
});

test("ranks unfinished fylker by how close they are, untouched ones last and A-Å", () => {
	const { inProgress } = splitRegions({
		Nordland: fylke(0, 9),
		Vestfold: fylke(12, 14),
		Troms: fylke(0, 1),
		Agder: fylke(8, 11),
		Innlandet: fylke(5, 16),
	});

	// Troms needs only one club but has no progress, so it must not leapfrog Vestfold.
	expect(inProgress.map((r) => r.name)).toEqual([
		"Vestfold",
		"Agder",
		"Innlandet",
		"Nordland",
		"Troms",
	]);
	expect(inProgress[0].remaining).toBe(2);
});

test("breaks a percentage tie by fewest clubs left", () => {
	const { inProgress } = splitRegions({
		Vestfold: fylke(12, 14),
		Agder: fylke(6, 7), // same 85.7%, one club left instead of two
	});

	expect(inProgress.map((r) => r.name)).toEqual(["Agder", "Vestfold"]);
});

test("moves finished fylker into their own alphabetical group", () => {
	const { inProgress, completed } = splitRegions({
		Oslo: fylke(3, 3),
		Vestfold: fylke(12, 14),
		Svalbard: fylke(1, 1),
	});

	expect(completed.map((r) => r.name)).toEqual(["Oslo", "Svalbard"]);
	expect(completed[0].progress).toBe(100);
	expect(inProgress.map((r) => r.name)).toEqual(["Vestfold"]);
});

test("renders both goal sections with the remaining-club counts", async () => {
	global.fetch = jest.fn(() =>
		Promise.resolve({
			ok: true,
			json: () =>
				Promise.resolve({
					displayName: "Test",
					email: "test@example.com",
					totalPlayed: 15,
					totalCourses: 18,
					percentageComplete: 83,
					recentRounds: [],
					regionStats: { Oslo: fylke(3, 3), Vestfold: fylke(12, 14) },
				}),
		}),
	);

	render(
		<MemoryRouter>
			<Overview user={{ name: "Test" }} onNavigate={() => {}} />
		</MemoryRouter>,
	);

	expect(await screen.findByText("Nærmest mål")).toBeInTheDocument();
	expect(screen.getByText("Fullført (1)")).toBeInTheDocument();
	expect(screen.getByText("1 av 2 fylker fullført")).toBeInTheDocument();
	expect(screen.getByText("12 / 14 · 2 igjen")).toBeInTheDocument();
});
