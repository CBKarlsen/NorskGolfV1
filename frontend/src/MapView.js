import React, { useState, useEffect } from "react";
import { MapContainer, TileLayer, Marker, Popup, useMap, ZoomControl } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import { UnplayedIcon, PlayedIcon } from "./MapIcons";

// MUI Imports
import Skeleton from "@mui/material/Skeleton";
import Button from "@mui/material/Button";
import Dialog from "@mui/material/Dialog";
import DialogTitle from "@mui/material/DialogTitle";
import DialogContent from "@mui/material/DialogContent";
import DialogActions from "@mui/material/DialogActions";
import TextField from "@mui/material/TextField";
import Typography from "@mui/material/Typography";
import Box from "@mui/material/Box";

import Paper from "@mui/material/Paper";

// Icons
import AddCircleIcon from '@mui/icons-material/AddCircle';
import EmojiEventsIcon from '@mui/icons-material/EmojiEvents';

// 1. Map Controller (Kept same)
function MapController({ focus, onFocusComplete, closePopupTrigger }) {
    const map = useMap();
    useEffect(() => {
        if (focus) {
            map.flyTo([focus.lat, focus.lng], focus.zoom, { animate: true, duration: 1.5 });
            onFocusComplete();
        }
    }, [focus, map, onFocusComplete]);
    useEffect(() => {
        if (closePopupTrigger > 0) map.closePopup();
    }, [closePopupTrigger, map]);
    return null;
}

function MapView({ user, focus, onFocusComplete }) {
    // --- Data State ---
    const [courses, setCourses] = useState([]);
    const [rounds, setRounds] = useState([]);
    const [loading, setLoading] = useState(true);

    // --- Modal State ---
    const [modalOpen, setModalOpen] = useState(false);
    const [selectedCourse, setSelectedCourse] = useState(null);
    const [score, setScore] = useState("");
    const [date, setDate] = useState(new Date().toISOString().split("T")[0]);
    const [submitting, setSubmitting] = useState(false);

    const [closeTrigger, setCloseTrigger] = useState(0);

    // --- Initial Fetch ---
    useEffect(() => {
        setLoading(true);
        const fetchCourses = fetch("/api/courses").then(res => res.json());
        const fetchRounds = user
            ? fetch("/api/rounds").then(res => res.json())
            : Promise.resolve([]);

        Promise.all([fetchCourses, fetchRounds])
            .then(([coursesData, roundsData]) => {
                setCourses(coursesData || []);
                setRounds(Array.isArray(roundsData) ? roundsData : []);
            })
            .catch(err => console.error("Failed to load map data", err))
            .finally(() => setLoading(false));
    }, [user]);

    // --- Actions ---
    const openLogRoundModal = (course) => {
        setSelectedCourse(course);
        setScore("");
        setModalOpen(true);
    };

    const handleClose = () => {
        setModalOpen(false);
        setSelectedCourse(null);
        setCloseTrigger(t => t + 1); // Closes the map popup behind it
    };

    const handleSubmit = async (e) => {
        e.preventDefault(); // Prevent default form submit

        // Validation
        const inputYear = parseInt(date.split("-")[0]);
        const currentYear = new Date().getFullYear();

        if (inputYear > currentYear) {
            alert(`Du kan ikke velge årstallet ${inputYear}. Det er i fremtiden!`);
            return;
        }
        if (!score || !selectedCourse) return;

        setSubmitting(true);

        const payload = {
            courseId: selectedCourse.id,
            date: date,
            score: parseInt(score)
        };

        try {
            const res = await fetch("/api/rounds", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(payload)
            });

            if (res.ok) {
                const newRound = await res.json();
                setRounds(prev => [...prev, newRound]);
                handleClose();
            } else {
                alert("Kunne ikke lagre runden.");
            }
        } catch (err) {
            console.error(err);
        } finally {
            setSubmitting(false);
        }
    };

    // --- LOADING SKELETON ---
    if (loading) {
        return (
            <Box sx={{ width: "100%", height: "100%", position: "relative", bgcolor: "#e0e0e0" }}>
                <Skeleton variant="rectangular" width="100%" height="100%" animation="wave" />
                {/* Fake UI elements to look like map controls */}
                <Skeleton variant="rectangular" width={40} height={80} sx={{ position: "absolute", top: 20, left: 20, borderRadius: 1 }} />
            </Box>
        );
    }

    // --- RENDER MAP ---
    return (<>
        <MapContainer
            center={[60.39, 5.32]}
            zoom={10}
            zoomControl={false} // Disable default top-left zoom
            style={{ height: "100%", width: "100%", background: "#cad2d3" }}
        >
            <MapController focus={focus} onFocusComplete={onFocusComplete} closePopupTrigger={closeTrigger} />

            {/* Move Zoom Control to bottom right or keep top left */}
            <ZoomControl position="topleft" />

            <TileLayer
                url="https://cartodb-basemaps-{s}.global.ssl.fastly.net/light_all/{z}/{x}/{y}.png"
                attribution='&copy; OpenStreetMap &copy; CartoDB'
            />

            {courses.map(course => {
                if (!course) return null;

                const courseRounds = rounds.filter(r => r.courseId === course.id);
                const isPlayed = courseRounds.length > 0;

                const bestScore = isPlayed
                    ? Math.min(...courseRounds.map(r => r.score))
                    : null;

                return (
                    <Marker
                        key={course.id}
                        position={[course.latitude, course.longitude]}
                        icon={isPlayed ? PlayedIcon : UnplayedIcon}
                    >
                        {/* --- MUI STYLED POPUP --- */}
                        <Popup className="mui-popup-override" maxWidth={250} minWidth={200}>
                            <Box sx={{ textAlign: "center", p: 1 }}>
                                <Typography variant="subtitle1" sx={{ fontWeight: 700, lineHeight: 1.2, mb: 1 }}>
                                    {course.name || "Ukjent Bane"}
                                </Typography>

                                {isPlayed ? (
                                    <Paper elevation={0} sx={{
                                        bgcolor: "#e8f5e9",
                                        border: "1px solid #c8e6c9",
                                        borderRadius: 2,
                                        p: 1.5, mb: 2
                                    }}>
                                        <Box sx={{ display: "flex", alignItems: "center", justifyContent: "center", gap: 1, color: "#2E7D32", mb: 0.5 }}>
                                            <EmojiEventsIcon fontSize="small" />
                                            <Typography variant="caption" fontWeight="bold" sx={{ textTransform: "uppercase" }}>
                                                Beste Runde
                                            </Typography>
                                        </Box>
                                        <Typography variant="h4" sx={{ fontWeight: 800, color: "#2E7D32" }}>
                                            {bestScore}
                                        </Typography>
                                    </Paper>
                                ) : (
                                    <Typography variant="body2" sx={{ color: "#757575", fontStyle: "italic", mb: 2 }}>
                                        Ingen runder registrert
                                    </Typography>
                                )}

                                <Button
                                    variant="contained"
                                    size="small"
                                    onClick={() => openLogRoundModal(course)}
                                    startIcon={!isPlayed && <AddCircleIcon />}
                                    sx={{
                                        bgcolor: isPlayed ? "#333" : "#2E7D32",
                                        width: "100%",
                                        boxShadow: "none",
                                        "&:hover": {
                                            bgcolor: isPlayed ? "#000" : "#1b5e20",
                                            boxShadow: "none"
                                        }
                                    }}
                                >
                                    {isPlayed ? "Ny Runde" : "Registrer"}
                                </Button>
                            </Box>
                        </Popup>
                    </Marker>
                );
            })}
        </MapContainer>

        {/* --- MUI DIALOG (MODAL) --- */}
        <Dialog
            open={modalOpen}
            onClose={handleClose}
            fullWidth
            maxWidth="xs"


            slotProps={{
                paper: {
                    sx: { borderRadius: 3, p: 1 }
                }
            }}
        >
            <form onSubmit={handleSubmit}>
                <DialogTitle sx={{ pb: 1 }}>
                    <Typography variant="h5" fontWeight="700">
                        Ny Runde ⛳
                    </Typography>
                    <Typography variant="body2" color="text.secondary">
                        {selectedCourse?.name}
                    </Typography>
                </DialogTitle>

                <DialogContent>
                    <Box sx={{ display: "flex", flexDirection: "column", gap: 2.5, mt: 1 }}>

                        {/* DATE PICKER (Native type="date" but styled by MUI) */}
                        <TextField
                            label="Dato"
                            type="date"
                            fullWidth
                            value={date}
                            onChange={e => setDate(e.target.value)}
                            required

                            slotProps={{
                                inputLabel: {
                                    shrink: true
                                },
                                htmlInput: {
                                    max: new Date().toISOString().split("T")[0],
                                    min: "2000-01-01"
                                }
                            }}

                            sx={{
                                "& .MuiOutlinedInput-root": { borderRadius: 2 }
                            }}
                        />

                        {/* SCORE INPUT */}
                        <TextField
                            label="Score (Slag)"
                            type="number"
                            fullWidth
                            value={score}
                            onChange={e => setScore(e.target.value)}
                            placeholder="f.eks. 82"
                            required
                            sx={{
                                "& .MuiOutlinedInput-root": { borderRadius: 2 }
                            }}
                        />
                    </Box>
                </DialogContent>

                <DialogActions sx={{ px: 3, pb: 2 }}>
                    <Button onClick={handleClose} color="inherit" sx={{ fontWeight: 600 }}>
                        Avbryt
                    </Button>
                    <Button
                        type="submit"
                        variant="contained"
                        disabled={submitting}
                        sx={{
                            bgcolor: "#2E7D32",
                            borderRadius: 2,
                            px: 3,
                            "&:hover": { bgcolor: "#1b5e20" }
                        }}
                    >
                        {submitting ? "Lagrer..." : "Lagre"}
                    </Button>
                </DialogActions>
            </form>
        </Dialog>
    </>);
}

export default MapView;