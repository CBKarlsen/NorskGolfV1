import EmojiEventsIcon from "@mui/icons-material/EmojiEvents";
import Box from "@mui/material/Box";
import CircularProgress from "@mui/material/CircularProgress";
import Dialog from "@mui/material/Dialog";
import DialogContent from "@mui/material/DialogContent";
import DialogTitle from "@mui/material/DialogTitle";
import Typography from "@mui/material/Typography";
import { useEffect, useState } from "react";
import { computeBadges } from "./badges";

// ponytail: fetches its own stats instead of lifting Overview's state into App.
// One extra request, only when the dialog is actually opened.
function BadgesDialog({ open, onClose }) {
	const [badges, setBadges] = useState(null);

	useEffect(() => {
		if (!open) return;

		let cancelled = false;
		fetch("/api/overview")
			.then((res) => (res.ok ? res.json() : Promise.reject()))
			.then((stats) => {
				if (!cancelled) setBadges(computeBadges(stats));
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
				{badges && (
					<Typography variant="body2" sx={{ color: "#888", fontWeight: 400 }}>
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
								.map((badge) => (
									<Box
										key={badge.label}
										sx={{
											display: "flex",
											alignItems: "center",
											gap: 1.5,
											py: 0.75,
										}}
									>
										<EmojiEventsIcon
											sx={{ color: badge.earned ? "#FFD700" : "#dcdcdc" }}
										/>
										<Box>
											<Typography
												variant="body2"
												sx={{
													fontWeight: badge.earned ? 700 : 400,
													color: badge.earned ? "#333" : "#999",
												}}
											>
												{badge.label}
											</Typography>
											{/* Seeing what is next is the point, so locked badges
											    keep their progress instead of being hidden. */}
											{!badge.earned && (
												<Typography variant="caption" sx={{ color: "#aaa" }}>
													{badge.hint}
												</Typography>
											)}
										</Box>
									</Box>
								))}
						</Box>
					))
				)}
			</DialogContent>
		</Dialog>
	);
}

export default BadgesDialog;
