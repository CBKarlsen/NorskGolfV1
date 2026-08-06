// Badges are derived, never stored: each one is a threshold over the /api/overview
// response, recomputed on every open. See
// docs/superpowers/specs/2026-08-06-badges-design.md for why there is no table.

// Counts fylker where playedCount === totalCount. Matches splitRegions in Overview.js
// for all fylker the backend produces (GolfService groups by actual courses, so every
// fylke has at least one). The totalCount > 0 guard rejects the degenerate zero-course
// case; unreachable now, but a safer invariant than silently counting empty fylker complete.
const completedFylker = (regionStats) =>
	Object.values(regionStats ?? {}).filter(
		(s) => s.totalCount > 0 && s.playedCount === s.totalCount,
	).length;

// A tier target is a number, or a function of the stats when the goal moves.
// ponytail: no per-badge objects — three sources cover all eleven badges.
const GROUPS = [
	{
		name: "Klubber",
		unit: "klubber",
		unitSingular: "klubb",
		value: (s) => s.totalPlayed ?? 0,
		tiers: [
			[1, "Første klubb"],
			[10, "Ti klubber"],
			[25, "25 klubber"],
			[50, "50 klubber"],
			[100, "100 klubber"],
		],
	},
	{
		name: "Fylker",
		unit: "fylker",
		unitSingular: "fylke",
		value: (s) => completedFylker(s.regionStats),
		tiers: [
			[1, "Første fylke"],
			[5, "Fem fylker"],
			// golf_clubs.json is hand-edited: 15 fylker today, not necessarily
			// tomorrow. Read the goal out of the data instead of hardcoding it.
			[(s) => Object.keys(s.regionStats ?? {}).length, "Hele Norge"],
		],
	},
	{
		name: "Runder",
		unit: "runder",
		unitSingular: "runde",
		value: (s) => s.roundCount ?? 0,
		tiers: [
			[10, "Ti runder"],
			[50, "Femti runder"],
			[100, "Hundre runder"],
		],
	},
];

export function computeBadges(stats) {
	return GROUPS.flatMap((group) => {
		const current = group.value(stats);

		return group.tiers.map(([tier, label]) => {
			const target = typeof tier === "function" ? tier(stats) : tier;
			const unit = target === 1 ? group.unitSingular : group.unit;

			return {
				group: group.name,
				label,
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
