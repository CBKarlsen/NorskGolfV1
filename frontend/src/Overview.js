import React, { useState, useEffect } from "react";
import "./SocialView.css";


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

// Icons
import ExpandMoreIcon from '@mui/icons-material/ExpandMore';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import RadioButtonUncheckedIcon from '@mui/icons-material/RadioButtonUnchecked';
import MapIcon from '@mui/icons-material/Map';
import GolfCourseIcon from '@mui/icons-material/GolfCourse';
import PublicIcon from '@mui/icons-material/Public';
import TrendingUpIcon from '@mui/icons-material/TrendingUp';
import SportsGolfIcon from '@mui/icons-material/SportsGolf';       // Missing
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutline'; // Missing
import CalendarTodayIcon from '@mui/icons-material/CalendarToday'; // Missing


function Overview({onNavigate}) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [roundToDelete, setRoundToDelete] = useState(null);
    const [expandedRegion, setExpandedRegion] = useState(true);

    function getCookie(name) {
        const match = document.cookie.match(new RegExp('(^|; )' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[2]) : null;
    }

    // --- 1. REUSABLE FETCH FUNCTION ---
    const loadOverview = () => {


        setLoading(true);

        fetch('/api/overview')
            .then(res => {
                if (!res.ok) throw new Error("Failed to load");
                return res.json();
            })
            .then(dt => {
                setData(dt);
                setLoading(false);
            })
            .catch(err => {
                console.error(err);
                setLoading(false);
            });
    };

    // Initial Load
    useEffect(() => {
        loadOverview();
    }, []);


    const confirmDelete = (roundId) => {
        setRoundToDelete(roundId);
    };


    const performDelete = async () => {
        if (!roundToDelete) return;

        try {
            const csrf = getCookie('XSRF-TOKEN');
            const res = await fetch(`/api/rounds/${roundToDelete}`, { method: 'DELETE', credentials: 'include', headers:{ ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {})}
            });
            if (res.ok) {
                loadOverview(); // Refresh data
                setRoundToDelete(null); // Close modal
            } else {
                alert("Kunne ikke slette runden.");
            }
        } catch (error) {
            console.error("Error deleting round:", error);
        }
    };

    if (loading && !data) {
        return (
            <div className="social-container" style={{ position: "relative" }}>
                {/* 1. HERO PROFILE SKELETON */}
                <div style={{ marginBottom: "20px", padding: "30px", background: "white", borderRadius: "12px", boxShadow: "0 2px 5px rgba(0,0,0,0.05)" }}>
                    <div style={{ display: "flex", alignItems: "center", gap: "20px" }}>
                        <Skeleton variant="circular" width={80} height={80} />
                        <div style={{ width: "50%" }}>
                            <Skeleton variant="text" width="60%" height={40} />
                            <Skeleton variant="text" width="40%" height={20} />
                        </div>
                    </div>
                </div>

                {/* 2. STATS GRID SKELETON */}
                <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "15px", marginBottom: "30px" }}>
                    {[1, 2, 3].map((i) => (
                        <div key={i} style={{ background: "white", padding: "20px", borderRadius: "12px", height: "120px" }}>
                            <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", height: "100%" }}>
                                <Skeleton variant="circular" width={40} height={40} style={{ marginBottom: "10px" }} />
                                <Skeleton variant="text" width="50%" height={30} />
                            </div>
                        </div>
                    ))}
                </div>

                {/* 3. REGIONS SKELETON */}
                <div className="section-header"><Skeleton width={150} /></div>
                <div className="region-grid" style={{
                    display: "grid",
                    gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))",
                    gap: "15px",
                    alignItems: "start"
                }}>
                    {[1, 2, 3, 4].map((i) => (
                        <div key={i} style={{ background: "white", padding: "15px", borderRadius: "8px", marginBottom: "10px" }}>
                            <div style={{ display: "flex", justifyContent: "space-between", marginBottom: "10px" }}>
                                <Skeleton variant="text" width="40%" />
                                <Skeleton variant="text" width="10%" />
                            </div>
                            <Skeleton variant="rectangular" height={8} style={{ borderRadius: "4px" }} />
                        </div>
                    ))}
                </div>

                {/* 4. RECENT ACTIVITY SKELETON */}
                <div className="section-header"><Skeleton width={120} /></div>
                <div className="leaderboard-list">
                    {[1, 2, 3].map((i) => (
                        <div key={i} className="player-card" style={{ padding: "15px" }}>
                            <div style={{ display: "flex", alignItems: "center", width: "100%" }}>
                                <Skeleton variant="rounded" width={40} height={40} style={{ marginRight: "15px" }} />
                                <div style={{ flex: 1 }}>
                                    <Skeleton variant="text" width="60%" height={20} />
                                    <Skeleton variant="text" width="40%" height={15} />
                                </div>
                                <Skeleton variant="circular" width={30} height={30} />
                            </div>
                        </div>
                    ))}
                </div>
            </div>
        );
    }
    if (!data) return <div style={{textAlign:"center", marginTop:"50px"}}>Feil ved innlasting av profil.</div>;

    const regions = Object.entries(data.regionStats || {}).map(([name, stat]) => ({
        name: name,
        played: stat.playedCount,
        total: stat.totalCount,
        percent: stat.totalCount > 0 ? (stat.playedCount / stat.totalCount) * 100 : 0,
        courses: stat.courses || []
    })).sort((a, b) => b.played - a.played);

    const toggleRegion = (regionName) => {
        setExpandedRegion(prev => prev === regionName ? null : regionName);
    };

    return (
        <div className="social-container" style={{ position: "relative" }}>

            {/* --- CUSTOM MODAL OVERLAY --- */}
            {roundToDelete && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>Slette runde?</h3>
                        <p>Dette vil fjerne runden og oppdatere statistikken din. Dette kan ikke angres.</p>
                        <div style={{ display: "flex", gap: "10px", justifyContent: "flex-end", marginTop: "20px" }}>
                            <button
                                onClick={() => setRoundToDelete(null)}
                                className="btn btn-outline"
                            >
                                Avbryt
                            </button>
                            <button
                                onClick={performDelete}
                                className="btn btn-reject"
                            >
                                Ja, slett
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* HERO PROFILE CARD */}
            <Paper
                elevation={3}
                sx={{
                    marginBottom: "30px",
                    padding: "30px",
                    background: "linear-gradient(135deg, #2E7D32 0%, #4CAF50 100%)",
                    color: "white",
                    borderRadius: "16px",
                    display: "flex",
                    alignItems: "center",
                    position: "relative",
                    overflow: "hidden"
                }}
            >
                <Box sx={{ display: "flex", alignItems: "center", gap: 3 }}>
                    <Avatar
                        src={data.avatar || `https://ui-avatars.com/api/?name=${data.displayName}`}
                        alt="profil"
                        sx={{
                            width: 80,
                            height: 80,
                            border: "4px solid rgba(255,255,255,0.3)",
                            boxShadow: "0 4px 12px rgba(0,0,0,0.2)"
                        }}
                    />
                    <Box>
                        <Typography variant="h4" sx={{ fontSize: "1.8rem", fontWeight: 700, lineHeight: 1.2 }}>
                            {data.displayName}
                        </Typography>
                        <Typography variant="body1" sx={{ opacity: 0.9, marginTop: 0.5, fontSize: "1rem" }}>
                            {data.email}
                        </Typography>
                    </Box>
                </Box>
                <Box sx={{
                    position: "absolute",
                    top: -20, right: -20,
                    width: "150px", height: "150px",
                    background: "rgba(255,255,255,0.1)",
                    borderRadius: "50%",
                    pointerEvents: "none",
                    marginBottom: "0px"
                }} />
            </Paper>

            {/* STATS */}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "15px", marginBottom: "30px" }}>
                <StatCard
                    label="Spilte baner"
                    value={data.totalPlayed}
                    icon={<GolfCourseIcon sx={{ fontSize: 28 }} />}
                />
                <StatCard
                    label="Totalt antall"
                    value={data.totalCourses}
                    icon={<PublicIcon sx={{ fontSize: 28 }} />}
                />
                <StatCard
                    label="Fremgang"
                    value={data.percentageComplete.toFixed(1) + "%"}
                    icon={<TrendingUpIcon sx={{ fontSize: 28 }} />}
                />
            </div>

            {/* COUNTY PROGRESS */}
            <Typography
                variant="h5"
                sx={{
                    mt: 7,
                    mb: 2,
                    fontWeight: 600,
                    color: '#2E7D32',
                    display: 'flex',
                    alignItems: 'center',
                    gap: 1
                }}
            >
                Fremgang per fylke
            </Typography>
            <div className="region-grid" style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fit, minmax(300px, 1fr))",
                gap: "15px",
                alignItems: "start"
            }}>
                {regions.map((region) => {
                    const progressVal = region.total > 0 ? (region.played / region.total) * 100 : 0;
                    return (
                        <Accordion
                            key={region.name}
                            expanded={expandedRegion === region.name}
                            onChange={() => toggleRegion(region.name)}
                            disableGutters
                            elevation={0}
                            sx={{
                                background: 'white',
                                border: '1px solid #eee',
                                borderRadius: '12px !important',
                                marginBottom: '10px',
                                '&:before': { display: 'none' },
                            }}
                        >
                            <AccordionSummary
                                expandIcon={<ExpandMoreIcon />}
                                sx={{
                                    flexDirection: 'row-reverse',
                                    '& .MuiAccordionSummary-content': { alignItems: 'center' }
                                }}
                            >
                                <Box sx={{ width: '100%', display: 'flex', flexDirection: 'column', ml: 2, mt: 0.5 }}>
                                    <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 1 }}>
                                        <Typography sx={{ fontWeight: 600, fontSize: '1rem' }}>
                                            {region.name}
                                        </Typography>
                                        <Typography sx={{ fontSize: '0.85rem', color: region.played > 0 ? '#2E7D32' : '#999', fontWeight: 600 }}>
                                            {region.played} / {region.total}
                                        </Typography>
                                    </Box>
                                    <LinearProgress
                                        variant="determinate"
                                        value={progressVal}
                                        sx={{
                                            height: 6,
                                            borderRadius: 5,
                                            backgroundColor: '#f0f0f0',
                                            '& .MuiLinearProgress-bar': {
                                                backgroundColor: region.played > 0 ? '#4CAF50' : '#bdbdbd'
                                            }
                                        }}
                                    />
                                </Box>
                            </AccordionSummary>

                            <AccordionDetails sx={{ borderTop: '1px solid #f5f5f5', padding: '10px 16px' }}>
                                {region.courses.map(course => (
                                    <Box
                                        key={course.id}
                                        onClick={(e) => {
                                            e.stopPropagation();
                                            onNavigate(course.latitude, course.longitude);
                                        }}
                                        sx={{
                                            display: 'flex',
                                            alignItems: 'center',
                                            justifyContent: 'space-between',
                                            padding: '12px 8px',
                                            borderBottom: '1px solid #f9f9f9',
                                            cursor: 'pointer',
                                            transition: 'background 0.2s',
                                            '&:hover': { backgroundColor: '#f5f9f6' },
                                            '&:last-child': { borderBottom: 'none' }
                                        }}
                                    >
                                        <Box sx={{ display: 'flex', alignItems: 'center', gap: 2 }}>
                                            {course.played ? (
                                                <CheckCircleIcon sx={{ color: '#2E7D32', fontSize: '1.4rem' }} />
                                            ) : (
                                                <RadioButtonUncheckedIcon sx={{ color: '#e0e0e0', fontSize: '1.4rem' }} />
                                            )}
                                            <Typography sx={{ color: course.played ? '#333' : '#757575', fontWeight: course.played ? 600 : 400, fontSize: '0.95rem' }}>
                                                {course.name}
                                            </Typography>
                                        </Box>
                                        <MapIcon sx={{ fontSize: '1rem', color: '#ddd' }} />
                                    </Box>
                                ))}
                            </AccordionDetails>
                        </Accordion>
                    );
                })}
            </div>

            {/* --- RECENT ACTIVITY --- */}
            <Typography
                variant="h5"
                sx={{
                    mt: 1,        // Changed from 5 to 7 (matches top header)
                    mb: 2,
                    fontWeight: 600,
                    color: '#2E7D32', // Changed from #333 to Green (matches top header)
                    display: 'flex',
                    alignItems: 'center',
                    gap: 1
                }}
            >
                {/* Icon will now inherit the green color automatically */}
                <CalendarTodayIcon />
                Siste aktivitet
            </Typography>

            {(!data.recentRounds || data.recentRounds.length === 0) ? (
                <Paper sx={{ p: 4, textAlign: 'center', bgcolor: '#f9f9f9', borderRadius: 4 }} elevation={0}>
                    <Typography variant="body1" color="textSecondary">
                        Du har ikke spilt noen runder ennå. 🏌️‍♂️
                    </Typography>
                </Paper>
            ) : (
                <List sx={{ width: '100%', padding: 0 }}>
                    {data.recentRounds.map((round) => (
                        <Paper
                            key={round.id}
                            elevation={0}
                            sx={{
                                mb: 1.5,
                                borderRadius: 3,
                                border: '1px solid #f0f0f0',
                                overflow: 'hidden',
                                transition: 'box-shadow 0.2s',
                                '&:hover': { boxShadow: '0 4px 12px rgba(0,0,0,0.08)' }
                            }}
                        >
                            <ListItem
                                secondaryAction={
                                    <Box sx={{ display: 'flex', alignItems: 'center', gap: 1 }}>
                                        <Box sx={{ textAlign: 'right', mr: 1 }}>
                                            <Typography variant="h6" sx={{ fontWeight: 700, color: getScoreColor(round.score), lineHeight: 1 }}>
                                                {round.score}
                                            </Typography>
                                            <Typography variant="caption" sx={{ color: '#999', fontSize: '0.7rem' }}>
                                                SLAG
                                            </Typography>
                                        </Box>
                                        <IconButton
                                            edge="end"
                                            aria-label="delete"
                                            onClick={() => confirmDelete(round.id)}
                                            sx={{ color: '#bdbdbd', '&:hover': { color: '#d32f2f', bgcolor: '#ffebee' } }}
                                        >
                                            <DeleteOutlineIcon />
                                        </IconButton>
                                    </Box>
                                }
                            >
                                <ListItemAvatar>
                                    <Avatar sx={{ bgcolor: '#e8f5e9', color: '#2E7D32' }}>
                                        <SportsGolfIcon />
                                    </Avatar>
                                </ListItemAvatar>
                                <ListItemText
                                    primary={<Typography variant="subtitle1" sx={{ fontWeight: 600 }}>{round.courseName}</Typography>}
                                    secondary={<Typography variant="body2" sx={{ color: '#888' }}>{round.date}</Typography>}
                                />
                            </ListItem>
                        </Paper>
                    ))}
                </List>
            )}
        </div>
    );
}

// Helpers
const StatCard = ({ label, value, icon }) => (
    <div style={{
        background: "white",
        padding: "20px",
        borderRadius: "16px",
        boxShadow: "0 2px 8px rgba(0,0,0,0.05)",
        display: "flex",
        flexDirection: "column",
        alignItems: "center",
        justifyContent: "center",
        height: "100%"
    }}>
        <div style={{
            background: "#e8f5e9",
            color: "#2E7D32",
            width: "50px",
            height: "50px",
            borderRadius: "50%",
            display: "flex",
            alignItems: "center",
            justifyContent: "center",
            marginBottom: "10px"
        }}>
            {icon}
        </div>
        <div style={{ fontSize: "1.8rem", fontWeight: "800", color: "#333", lineHeight: 1.2 }}>
            {value}
        </div>
        <div style={{ fontSize: "0.8rem", color: "#888", textTransform: "uppercase", letterSpacing: "1px", marginTop: "5px", fontWeight: 600 }}>
            {label}
        </div>
    </div>
);

const getScoreColor = (score) => {
    if (score < 72) return "#d32f2f";
    if (score < 80) return "#2E7D32";
    return "#333";
};

export default Overview;