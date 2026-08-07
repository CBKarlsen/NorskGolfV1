import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
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

	render(
		<MemoryRouter>
			<SocialView user={{ name: "Meg" }} />
		</MemoryRouter>,
	);

	expect(await screen.findByText("Kari Nordmann")).toBeInTheDocument();
	expect(screen.getByText("Hele Norge")).toBeInTheDocument();
});

test("a friend who has earned nothing gets no title", async () => {
	stubFriends([friend()]);

	render(
		<MemoryRouter>
			<SocialView user={{ name: "Meg" }} />
		</MemoryRouter>,
	);

	expect(await screen.findByText("Kari Nordmann")).toBeInTheDocument();
	expect(screen.queryByText("1 klubb")).not.toBeInTheDocument();
});

test("a friend with clubs but no finished fylke still gets a title", async () => {
	// The friends path must not be all-or-nothing: 25 clubs earns a title even
	// though every Fylker badge is still locked.
	stubFriends([
		friend({ totalCourses: 25, totalRounds: 4, fylkerComplete: 0 }),
	]);

	render(
		<MemoryRouter>
			<SocialView user={{ name: "Meg" }} />
		</MemoryRouter>,
	);

	expect(await screen.findByText("25 klubber")).toBeInTheDocument();
});
