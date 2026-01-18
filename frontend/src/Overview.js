import React, { useState, useEffect, useCallback } from "react";
import { useNavigate } from "react-router-dom";
import Skeleton from "@mui/material/Skeleton";
import Avatar from "@mui/material/Avatar";
import Accordion from '@mui/material/Accordion';
import AccordionSummary from '@mui/material/AccordionSummary';
import AccordionDetails from '@mui/material/AccordionDetails';
import Typography from '@mui/material/Typography';
import LinearProgress from '@mui/material/LinearProgress';
import Box from '@mui/material/Box';
import Paper from '@mui/material/Paper';
import List from '@mui/material/List';
import ListItem from '@mui/material/ListItem';
import ListItemAvatar from '@mui/material/ListItemAvatar';
import ListItemText from '@mui/material/ListItemText';
import IconButton from '@mui/material/IconButton';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Dialog from '@mui/material/Dialog';
import DialogActions from '@mui/material/DialogActions';
import DialogContent from '@mui/material/DialogContent';
import DialogContentText from '@mui/material/DialogContentText';
import DialogTitle from '@mui/material/DialogTitle';

// Icons
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import FlagIcon from '@mui/icons-material/Flag';
import PublicIcon from '@mui/icons-material/Public';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import CalendarTodayIcon from '@mui/icons-material/CalendarToday';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline';
import SportsGolfIcon from '@mui/icons-material/SportsGolf';

function Overview({ user, onNavigate }) {

    const navigate = useNavigate();

    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);

    // Delete State
    const [deleteDialogOpen, setDeleteDialogOpen] = useState(false);
    const [roundToDelete, setRoundToDelete] = useState(null);

    // Helper: Get CSRF Token
    function getCookie(name) {
        const match = document.cookie.match(new RegExp('(^|; )' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[2]) : null;
    }

    const loadOverview = useCallback(() => {
        setLoading(true);

        if (user) {
            // --- LOGGED IN USER ---
            fetch('/api/overview')
                .then(res => (res.ok ? res.json() : Promise.reject()))
                .then(dt => {
                    setData(dt);
                    setLoading(false);
                })
                .catch(() => setLoading(false));
        } else {
            // --- GUEST USER ---
            fetch('/api/courses')
                .then(res => res.json())
                .then(courses => {
                    const guestStats = {};
                    let totalCourses = 0;
                    courses.forEach(course => {
                        const region = course.county || "Ukjent";
                        if (!guestStats[region]) guestStats[region] = { playedCount: 0, totalCount: 0, courses: [] };
                        guestStats[region].totalCount += 1;
                        guestStats[region].courses.push({ ...course, played: false });
                        totalCourses++;
                    });
                    setData({
                        displayName: "Gjest",
                        email: "Logg inn for å lagre stats",
                        avatar: null,
                        totalPlayed: 0,
                        totalCourses: totalCourses,
                        percentageComplete: 0,
                        recentRounds: [],
                        regionStats: guestStats
                    });
                    setLoading(false);
                })
                .catch(() => setLoading(false));
        }
    }, [user]);

    useEffect(() => {
        loadOverview();
    }, [loadOverview]);

    // --- DELETE LOGIC ---
    const confirmDelete = (roundId) => {
        setRoundToDelete(roundId);
        setDeleteDialogOpen(true);
    };

    const performDelete = async () => {
        if (!roundToDelete) return;

        try {
            const csrf = getCookie('XSRF-TOKEN');

            // ✅ credentials: 'include' sends the JSESSIONID cookie
            const res = await fetch(`/api/rounds/${roundToDelete}`, {
                method: 'DELETE',
                credentials: 'include',
                headers:{
                    ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {})
                }
            });

            if (res.ok) {
                // Remove locally to avoid full reload
                setData(prev => ({
                    ...prev,
                    recentRounds: prev.recentRounds.filter(r => r.id !== roundToDelete)
                }));
                // Optionally reload full stats to update progress bars
                loadOverview();
            } else {
                alert("Kunne ikke slette runden.");
            }
        } catch (error) {
            console.error("Error deleting round:", error);
        } finally {
            setDeleteDialogOpen(false);
            setRoundToDelete(null);
        }
    };

    if (loading) {
        return (
            <Box sx={{
                p: { xs: 1.5, sm: 3 },
                maxWidth: 'md',
                margin: '0 auto',
                pb: 10
            }}>
                {/* 1. Hero Card Skeleton */}
                <Skeleton
                    variant="rectangular"
                    height={140}
                    sx={{ borderRadius: 4, mb: 4 }}
                />

                {/* 2. Stats Grid Skeleton (3 boxes) */}
                <Box sx={{ display: 'flex', gap: { xs: 1.5, sm: 2 }, mb: 4 }}>
                    <Skeleton variant="rectangular" height={100} sx={{ flex: 1, borderRadius: 3 }} />
                    <Skeleton variant="rectangular" height={100} sx={{ flex: 1, borderRadius: 3 }} />
                    <Skeleton variant="rectangular" height={100} sx={{ flex: 1, borderRadius: 3 }} />
                </Box>

                {/* 3. Title Skeleton */}
                <Skeleton width="50%" height={32} sx={{ mb: 2 }} />

                {/* 4. County List Skeleton (Simulate Accordions) */}
                {[1, 2, 3, 4, 5, 6].map((i) => (
                    <Skeleton
                        key={i}
                        variant="rectangular"
                        height={56}
                        sx={{ borderRadius: 3, mb: 1.5 }}
                    />
                ))}
            </Box>
        );
    }

    if (!data) return null;


    const sortedRegions = Object.entries(data.regionStats).sort((a, b) => {
        // 1. Sort by Played Count (High to Low)
        const countDiff = b[1].playedCount - a[1].playedCount;
        if (countDiff !== 0) return countDiff;

        // 2. If tied, sort Alphabetically (A-Z)
        return a[0].localeCompare(b[0]);
    });

    return (
        <Box sx={{
            p: { xs: 1.5, sm: 3 },
            pb: 10,
            maxWidth: 'md',
            margin: '0 auto'
        }}>

            {/* --- 1. HERO CARD --- */}
            <Paper
                elevation={3}
                sx={{
                    p: { xs: 2.5, sm: 4 },
                    mb: 3,
                    borderRadius: 4,
                    background: 'linear-gradient(135deg, #2E7D32 30%, #43A047 90%)',
                    color: 'white',
                    display: 'flex',
                    alignItems: 'center',
                    gap: { xs: 2, sm: 3 },
                    position: 'relative',
                    overflow: 'hidden'
                }}
            >
                <Box sx={{ position: 'absolute', top: -20, right: -20, width: 120, height: 120, bgcolor: 'rgba(255,255,255,0.1)', borderRadius: '50%' }} />
                <Avatar
                    src={user?.photo || data.avatar}
                    sx={{
                        width: { xs: 65, sm: 90 },
                        height: { xs: 65, sm: 90 },
                        border: '3px solid rgba(255,255,255,0.3)',
                        boxShadow: '0 4px 15px rgba(0,0,0,0.2)'
                    }}
                />
                <Box sx={{ zIndex: 1, overflow: 'hidden' }}>
                    <Typography variant="h5" sx={{ fontWeight: 800, fontSize: { xs: '1.4rem', sm: '1.8rem' } }}>
                        {user ? user.name : data.displayName}
                    </Typography>
                    <Typography variant="body2" sx={{ opacity: 0.9, fontSize: { xs: '0.8rem', sm: '1rem' }, textOverflow: 'ellipsis', overflow: 'hidden', whiteSpace: 'nowrap' }}>
                        {user ? user.email : data.email}
                    </Typography>
                </Box>
            </Paper>

            {/* --- 2. STATS GRID --- */}
            <Box sx={{ display: 'flex', gap: { xs: 1.5, sm: 2 }, mb: 4 }}>
                <Paper sx={{ flex: 1, p: { xs: 1.5, sm: 3 }, borderRadius: 3, textAlign: 'center', boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                    <Box sx={{ bgcolor: '#e8f5e9', width: 40, height: 40, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', mx: 'auto', mb: 1 }}>
                        <FlagIcon sx={{ color: '#2E7D32' }} />
                    </Box>
                    <Typography variant="h4" sx={{ fontWeight: 800, color: '#333', fontSize: { xs: '1.5rem', sm: '2.125rem' } }}>
                        {data.totalPlayed}
                    </Typography>
                    <Typography variant="caption" sx={{ color: '#888', fontWeight: 600, fontSize: { xs: '0.65rem', sm: '0.75rem' }, display: 'block', lineHeight: 1.2 }}>
                        SPILTE BANER
                    </Typography>
                </Paper>

                <Paper sx={{ flex: 1, p: { xs: 1.5, sm: 3 }, borderRadius: 3, textAlign: 'center', boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                    <Box sx={{ bgcolor: '#e8f5e9', width: 40, height: 40, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', mx: 'auto', mb: 1 }}>
                        <PublicIcon sx={{ color: '#2E7D32' }} />
                    </Box>
                    <Typography variant="h4" sx={{ fontWeight: 800, color: '#333', fontSize: { xs: '1.5rem', sm: '2.125rem' } }}>
                        {data.totalCourses}
                    </Typography>
                    <Typography variant="caption" sx={{ color: '#888', fontWeight: 600, fontSize: { xs: '0.65rem', sm: '0.75rem' }, display: 'block', lineHeight: 1.2 }}>
                        TOTALT ANTALL
                    </Typography>
                </Paper>

                <Paper sx={{ flex: 1, p: { xs: 1.5, sm: 3 }, borderRadius: 3, textAlign: 'center', boxShadow: '0 2px 8px rgba(0,0,0,0.05)' }}>
                    <Box sx={{ bgcolor: '#e8f5e9', width: 40, height: 40, borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', mx: 'auto', mb: 1 }}>
                        <TrendingUpIcon sx={{ color: '#2E7D32' }} />
                    </Box>
                    <Typography variant="h4" sx={{ fontWeight: 800, color: '#333', fontSize: { xs: '1.5rem', sm: '2.125rem' } }}>
                        {data.percentageComplete}%
                    </Typography>
                    <Typography variant="caption" sx={{ color: '#888', fontWeight: 600, fontSize: { xs: '0.65rem', sm: '0.75rem' }, display: 'block', lineHeight: 1.2 }}>
                        FREMGANG
                    </Typography>
                </Paper>
            </Box>

            {/* --- 3. PROGRESS SECTION --- */}
            <Typography variant="h6" sx={{ mb: 2, fontWeight: 700, color: '#2E7D32', pl: 0.5 }}>
                Fremgang per fylke
            </Typography>

            <Box>
                {sortedRegions.map(([region, stats]) => {
                    const progress = (stats.playedCount / stats.totalCount) * 100;
                    return (
                        <Accordion
                            key={region}
                            disableGutters
                            elevation={0}
                            sx={{ mb: 1.5, borderRadius: '12px !important', border: '1px solid #f0f0f0', '&:before': { display: 'none' } }}
                        >
                            <AccordionSummary expandIcon={<ExpandMoreIcon />} sx={{ borderRadius: 3, px: 2 }}>
                                <Box sx={{ width: '100%' }}>
                                    <Box sx={{ display: 'flex', justifyContent: 'space-between', mb: 0.5 }}>
                                        <Typography variant="subtitle2" fontWeight={600}>{region}</Typography>
                                        <Typography variant="caption" sx={{ color: '#666' }}>{stats.playedCount} / {stats.totalCount}</Typography>
                                    </Box>
                                    <LinearProgress variant="determinate" value={progress} sx={{ height: 6, borderRadius: 3, bgcolor: '#e0e0e0', '& .MuiLinearProgress-bar': { bgcolor: progress === 100 ? '#FFD700' : '#2E7D32' } }} />
                                </Box>
                            </AccordionSummary>
                            <AccordionDetails sx={{ pt: 0, px: 2, pb: 2 }}>
                                <List dense>
                                    {stats.courses.map((course) => (
                                        <ListItem
                                            key={course.id}
                                            sx={{ pl: 0, borderBottom: '1px dashed #eee', cursor: 'pointer', '&:hover': { bgcolor: '#f9f9f9' } }}
                                            onClick={() => onNavigate(course.latitude, course.longitude)}
                                        >
                                            <Box sx={{ mr: 1.5, display: 'flex', alignItems: 'center' }}>
                                                {course.played ? <CheckCircleIcon fontSize="small" color="success" /> : <RadioButtonUncheckedIcon fontSize="small" color="disabled" />}
                                            </Box>
                                            <ListItemText primary={<Typography variant="body2">{course.name}</Typography>} />
                                            {course.played && <Chip label="Spilt" size="small" color="success" variant="outlined" sx={{ height: 20, fontSize: '0.65rem' }} />}
                                        </ListItem>
                                    ))}
                                </List>
                            </AccordionDetails>
                        </Accordion>
                    );
                })}
            </Box>

            {/* --- 4. RECENT ACTIVITY (Logged in only) --- */}
            {user ? (
                <>
                    <Typography variant="h6" sx={{ mt: 4, mb: 2, fontWeight: 700, color: '#2E7D32', display: 'flex', alignItems: 'center', gap: 1 }}>
                        <CalendarTodayIcon fontSize="small" /> Siste aktivitet
                    </Typography>

                    {!data.recentRounds || data.recentRounds.length === 0 ? (
                        <Paper sx={{ p: 3, textAlign: 'center', bgcolor: '#f9f9f9', borderRadius: 4 }} elevation={0}>
                            <Typography variant="body2" color="textSecondary">Ingen runder ennå.</Typography>
                        </Paper>
                    ) : (
                        <List sx={{ width: '100%', padding: 0 }}>
                            {data.recentRounds.map((round) => (
                                <Paper key={round.id} elevation={0} sx={{ mb: 1.5, borderRadius: 3, border: '1px solid #f0f0f0' }}>
                                    <ListItem
                                        secondaryAction={
                                            <IconButton edge="end" aria-label="delete" onClick={() => confirmDelete(round.id)} sx={{ color: '#bdbdbd', '&:hover': { color: '#d32f2f', bgcolor: '#ffebee' } }}>
                                                <DeleteOutlineIcon />
                                            </IconButton>
                                        }
                                    >
                                        <ListItemAvatar>
                                            <Avatar sx={{ bgcolor: '#e8f5e9', color: '#2E7D32', width: 35, height: 35 }}>
                                                <SportsGolfIcon fontSize="small" />
                                            </Avatar>
                                        </ListItemAvatar>
                                        <ListItemText
                                            primary={<Typography variant="subtitle2" fontWeight={600}>{round.courseName}</Typography>}
                                            secondary={<Typography variant="caption" color="text.secondary">{round.date}</Typography>}
                                        />
                                        <Typography variant="h6" sx={{ fontWeight: 700, color: '#333', mr: 2 }}>{round.score}</Typography>
                                    </ListItem>
                                </Paper>
                            ))}
                        </List>
                    )}
                </>
            ) : (
                <Paper sx={{ p: 3, mt: 4, textAlign: 'center', bgcolor: '#f1f8e9', borderRadius: 4, border: '1px dashed #2E7D32' }} elevation={0}>
                    <Typography variant="subtitle1" sx={{ color: '#2E7D32', fontWeight: 600, mb: 1 }}>Vil du logge dine runder?</Typography>
                    <Button variant="contained" onClick={() => navigate("/login")} size="small" sx={{ bgcolor: '#2E7D32', borderRadius: 2, textTransform: 'none' }}>Logg inn nå</Button>
                </Paper>
            )}

            {/* --- DELETE CONFIRMATION DIALOG --- */}
            <Dialog
                open={deleteDialogOpen}
                onClose={() => setDeleteDialogOpen(false)}
                slotProps={{
                    paper: {
                        sx: { borderRadius: 3 }
                    }
                }}
            >
                <DialogTitle>Slette runden?</DialogTitle>
                <DialogContent>
                    <DialogContentText>
                        Er du sikker på at du vil slette denne runden? Dette kan ikke angres.
                    </DialogContentText>
                </DialogContent>
                <DialogActions sx={{ p: 2 }}>
                    <Button onClick={() => setDeleteDialogOpen(false)} color="inherit">Avbryt</Button>
                    <Button onClick={performDelete} color="error" variant="contained" disableElevation>Slett</Button>
                </DialogActions>
            </Dialog>

        </Box>
    );
}

export default Overview;