import Box from "@mui/material/Box";
import Button from "@mui/material/Button";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogActions from "@mui/material/DialogActions";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Typography from "@mui/material/Typography";
import { useEffect, useState } from "react";
import { computeBadges, fromDashboard } from "./badges";

// ponytail: fetches its own stats instead of lifting Overview's state into App.
// One extra request, only when the dialog is actually opened.
function BadgesDialog({ open, onClose }) {
	const [badges, setBadges] = useState(null);

	useEffect(() => {
		if (!open) return;

		setBadges(null);
		let cancelled = false;
		fetch("/api/overview")
			.then((res) => (res.ok ? res.json() : Promise.reject()))
			.then((stats) => {
				if (!cancelled) setBadges(computeBadges(fromDashboard(stats)));
			})
			.catch(() => {
				if (!cancelled) setBadges([]);
			});

		return () => {
			cancelled = true;
		};
	}, [open]);

	const earned = badges?.filter((b) => b.earned).length ?? 0;
	const groups = [...new Set(badges?.map((b) => b.group) ?? [])];

	return (
		<Dialog
			open={open}
			onClose={onClose}
			fullWidth
			maxWidth="xs"
			slotProps={{ paper: { sx: { borderRadius: 3 } } }}
		>
			<DialogTitle sx={{ fontWeight: 700, color: "#2E7D32", pb: 0.5 }}>
				Merker
				{badges?.length > 0 && (
					<Typography
						component="div"
						variant="body2"
						sx={{ color: "#888", fontWeight: 400 }}
					>
						{earned} av {badges.length} låst opp
					</Typography>
				)}
			</DialogTitle>

			<DialogContent sx={{ pb: 3 }}>
				{!badges ? (
					<Box sx={{ display: "flex", justifyContent: "center", py: 4 }}>
						<CircularProgress color="success" size={28} />
					</Box>
				) : badges.length === 0 ? (
					<Typography variant="body2" color="text.secondary">
						Kunne ikke hente merkene dine.
					</Typography>
				) : (
					groups.map((group) => (
						<Box key={group} sx={{ mb: 2 }}>
							<Typography
								variant="overline"
								sx={{ fontWeight: 700, letterSpacing: 1, color: "#888" }}
							>
								{group}
							</Typography>
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
						</Box>
					))
				)}
			</DialogContent>

			<DialogActions sx={{ p: 2 }}>
				<Button onClick={onClose} color="inherit">
					Lukk
				</Button>
			</DialogActions>
		</Dialog>
	);
}

export default BadgesDialog;
