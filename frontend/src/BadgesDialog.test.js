import { render, screen } from "@testing-library/react";
import BadgesDialog from "./BadgesDialog";

test("marks earned badges and shows progress only on the locked ones", async () => {
	global.fetch = jest.fn(() =>
		Promise.resolve({
			ok: true,
			json: () =>
				Promise.resolve({
					totalPlayed: 12,
					roundCount: 3,
					regionStats: {
						Oslo: { playedCount: 3, totalCount: 3, courses: [] },
					},
				}),
		}),
	);

	render(<BadgesDialog open onClose={() => {}} />);

	expect(await screen.findByText("Ti klubber")).toBeInTheDocument();
	// Locked: 12 of 25 clubs.
	expect(screen.getByText("12 / 25 klubber")).toBeInTheDocument();
	// Earned badges show no progress caption.
	expect(screen.queryByText("10 / 10 klubber")).not.toBeInTheDocument();
	expect(global.fetch).toHaveBeenCalledWith("/api/overview");
});

test("does not fetch until it is opened", () => {
	global.fetch = jest.fn();

	render(<BadgesDialog open={false} onClose={() => {}} />);

	expect(global.fetch).not.toHaveBeenCalled();
});

test("shows the error copy when the fetch fails, instead of hanging on the spinner", async () => {
	global.fetch = jest.fn(() => Promise.reject(new Error("network down")));

	render(<BadgesDialog open onClose={() => {}} />);

	expect(
		await screen.findByText("Kunne ikke hente merkene dine."),
	).toBeInTheDocument();
	expect(screen.queryByText(/låst opp/)).not.toBeInTheDocument();
});

test("reopening after a failed fetch shows the spinner again, not the stale error", async () => {
	global.fetch = jest.fn(() => Promise.reject(new Error("network down")));
	const { rerender } = render(<BadgesDialog open onClose={() => {}} />);
	await screen.findByText("Kunne ikke hente merkene dine.");

	// Close, then reopen with a retry that hasn't resolved yet.
	global.fetch = jest.fn(() => new Promise(() => {}));
	rerender(<BadgesDialog open={false} onClose={() => {}} />);
	rerender(<BadgesDialog open onClose={() => {}} />);

	expect(screen.getByRole("progressbar")).toBeInTheDocument();
	expect(
		screen.queryByText("Kunne ikke hente merkene dine."),
	).not.toBeInTheDocument();
});

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
	// 182 clubs and every fylke earns both legendaries — 100 klubber (rarity 10)
	// and Hele Norge (rarity 11) — and each labels its own tier.
	expect(screen.getAllByText("LEGENDARISK")).toHaveLength(2);
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
