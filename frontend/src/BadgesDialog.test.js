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
