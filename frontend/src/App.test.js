import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import App from "./App";

// react-leaflet ships ESM that CRA's jest 27 won't transform. This test renders
// /venner, so the map never mounts anyway.
jest.mock("./MapView", () => () => null);

test("viser app-skallet når innloggingssjekken er ferdig", async () => {
	// Not logged in: /api/auth/me answers 401
	global.fetch = jest.fn(() =>
		Promise.resolve({ ok: false, json: () => Promise.resolve(null) }),
	);

	// ponytail: /venner as guest renders without hitting the map or extra fetches
	render(
		<MemoryRouter initialEntries={["/venner"]}>
			<App />
		</MemoryRouter>,
	);

	expect(await screen.findByText(/GolfJakten/)).toBeInTheDocument();
	expect(screen.getByText("Kart")).toBeInTheDocument();
	expect(global.fetch).toHaveBeenCalledWith("/api/auth/me");
});
