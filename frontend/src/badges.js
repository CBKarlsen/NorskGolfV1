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
			// Labels are numeric throughout, so a title reads consistently next to a
			// name on the leaderboard. "Hele Norge" is the one exception: its target
			// is data-driven, so a number would be a lie.
			{ target: 1, label: "1 klubb", rarity: 1 },
			{ target: 10, label: "10 klubber", rarity: 4 },
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
			{ target: 1, label: "1 fylke", rarity: 3 },
			{ target: 5, label: "5 fylker", rarity: 7 },
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
			{ target: 10, label: "10 runder", rarity: 2 },
			{ target: 50, label: "50 runder", rarity: 5 },
			{ target: 100, label: "100 runder", rarity: 8 },
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
