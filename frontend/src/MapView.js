import React, { useState, useEffect } from "react";
import { MapContainer, TileLayer, Marker, Popup, useMap } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import { UnplayedIcon, PlayedIcon } from "./MapIcons";

// 1. Controller for "Fly To" animation and Closing Popups
function MapController({ focus, onFocusComplete, closePopupTrigger }) {
    const map = useMap();

    // Fly to location
    useEffect(() => {
        if (focus) {
            map.flyTo([focus.lat, focus.lng], focus.zoom, { animate: true, duration: 1.5 });
            onFocusComplete();
        }
    }, [focus, map, onFocusComplete]);

    // Close popup when trigger changes
    useEffect(() => {
        if (closePopupTrigger > 0) {
            map.closePopup();
        }
    }, [closePopupTrigger, map]);

    return null;
}

function MapView({ user, focus, onFocusComplete }) {
    // --- Data State ---
    const [courses, setCourses] = useState([]);
    const [rounds, setRounds] = useState([]);

    // --- Modal State ---
    const [modalOpen, setModalOpen] = useState(false);
    const [selectedCourse, setSelectedCourse] = useState(null);
    const [score, setScore] = useState("");
    const [date, setDate] = useState(new Date().toISOString().split("T")[0]);
    const [submitting, setSubmitting] = useState(false);

    // Trigger to close map popups
    const [closeTrigger, setCloseTrigger] = useState(0);

    // --- Initial Fetch ---
    useEffect(() => {
        fetch("/api/courses")
            .then(res => res.json())
            .then(setCourses);

        if (user) {
            fetch("/api/rounds")
                .then(res => res.json())
                .then(data => setRounds(Array.isArray(data) ? data : []));
        }
    }, [user]);

    function getCookie(name){
        const match = document.cookie.match(new RegExp('(^|; )' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[2]) : null;
    }

    // --- Modal Actions ---
    const openLogRoundModal = (course) => {
        setSelectedCourse(course);
        setScore("");
        setModalOpen(true);
    };

    const handleClose = () => {
        setModalOpen(false);
        setSelectedCourse(null);
        setCloseTrigger(t => t + 1);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();

        // --- VALIDATION START ---
        const inputYear = parseInt(date.split("-")[0]);
        const currentYear = new Date().getFullYear();

        if (inputYear > currentYear) {
            alert(`Du kan ikke velge årstallet ${inputYear}. Det er i fremtiden!`);
            return;
        }
        if (inputYear < 2000) {
            alert("Årstallet må være 2000 eller senere.");
            return;
        }
        // --- VALIDATION END ---

        if (!score || !selectedCourse) return;

        setSubmitting(true);

        const payload = {
            courseId: selectedCourse.id,
            date: date,
            score: parseInt(score)
        };

        try {
            const csrf = getCookie('XSRF-TOKEN')
            const res = await fetch("/api/rounds", {
                method: "POST",
                credentials:'include',
                headers: {
                    'Content-Type': 'application/json',
                    ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}) // <-- include CSRF token if present
                },
                body: JSON.stringify(payload)
            });

            if (res.ok) {
                const newRound = await res.json();
                setRounds(prev => [...prev, newRound]);
                handleClose();
            } else {
                alert("Kunne ikke lagre runden. Vennligst prøv igjen.");
            }
        } catch (err) {
            console.error(err);
        } finally {
            setSubmitting(false);
        }
    };

    return (<>
        <MapContainer center={[60.39, 5.32]} zoom={10} style={{ height: "100%", width: "100%" }}>
            <MapController focus={focus} onFocusComplete={onFocusComplete} closePopupTrigger={closeTrigger} />
            <TileLayer
                url="https://cartodb-basemaps-{s}.global.ssl.fastly.net/light_all/{z}/{x}/{y}.png"
                attribution='&copy; OpenStreetMap &copy; CartoDB'
            />

            {courses.map(course => {
                // SIKKERHETSSJEKK: Hopp over hvis banen er ugyldig
                if (!course) return null;

                const courseRounds = rounds.filter(r => r.courseId === course.id);
                const isPlayed = courseRounds.length > 0;

                const bestScore = isPlayed
                    ? Math.min(...courseRounds.map(r => r.score))
                    : null;

                return (<Marker
                    key={course.id}
                    position={[course.latitude, course.longitude]}
                    icon={isPlayed ? PlayedIcon : UnplayedIcon}
                >
                    <Popup className="golf-popup">
                        <div style={{textAlign: "center", minWidth: "160px"}}>

                            {/* 1. Clean Title (Fjernet selectedCourse herfra!) */}
                            <h3 style={styles.popupHeader}>{course.name || "Ukjent Bane"}</h3>

                            {/* 2. Dynamic Content Area */}
                            {isPlayed ? (
                                <div style={styles.scoreContainer}>
                                    <div style={styles.scoreLabel}>Beste Runde</div>
                                    <div style={styles.scoreValue}>{bestScore}</div>
                                </div>
                            ) : (
                                <p style={styles.unplayedText}>Ingen runder registrert</p>
                            )}

                            {/* 3. Action Button */}
                            <button
                                onClick={() => openLogRoundModal(course)}
                                style={styles.popupBtn}
                            >
                                {isPlayed ? "+ Ny Runde" : "Registrer Runde"}
                            </button>
                        </div>
                    </Popup>
                </Marker>);
            })}
        </MapContainer>

        {/* --- MODAL OVERLAY --- */}
        {modalOpen && selectedCourse && (
            <div style={styles.modalOverlay}>
                <div style={styles.modalContent}>
                    {/* Her er det trygt å bruke selectedCourse fordi vi sjekker && selectedCourse over */}
                    <h3 style={{ color: "#333", marginTop: 0, fontSize: "1.5rem" }}>
                        Ny Runde ⛳
                    </h3>
                    <p style={{ color: "#666", marginTop: "-10px", marginBottom: "20px" }}>
                        {selectedCourse.name}
                    </p>

                    <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "15px" }}>
                        <div>
                            <label style={styles.label}>Dato</label>
                            <input
                                type="date"
                                value={date}
                                max={new Date().toISOString().split("T")[0]}
                                min={"2000-01-01"}
                                onChange={e => setDate(e.target.value)}
                                style={styles.input}
                                required
                            />
                        </div>
                        <div>
                            <label style={styles.label}>Score (Slag)</label>
                            <input
                                type="number"
                                value={score}
                                onChange={e => setScore(e.target.value)}
                                placeholder="f.eks. 82"
                                style={styles.input}
                                required
                            />
                        </div>

                        <div style={{ display: "flex", gap: "10px", marginTop: "10px" }}>
                            <button type="button" onClick={handleClose} style={styles.btnCancel}>Avbryt</button>
                            <button type="submit" disabled={submitting} style={styles.btnSubmit}>
                                {submitting ? "Lagrer..." : "Lagre runde"}
                            </button>
                        </div>
                    </form>
                </div>
            </div>
        )}
    </>);
}


const styles = {


    modalOverlay: {
        position: "fixed",
        top: 0, left: 0, right: 0, bottom: 0,
        background: "rgba(0,0,0,0.5)", // Darker, cleaner dim
        zIndex: 9999,
        display: "flex", alignItems: "center", justifyContent: "center",
        backdropFilter: "blur(4px)" // Adds a nice modern blur
    },
    modalContent: {
        background: "white",
        padding: "30px",
        borderRadius: "16px", // Softer corners
        width: "90%",
        maxWidth: "380px",
        boxShadow: "0 20px 40px rgba(0,0,0,0.2)",
        animation: "popIn 0.2s ease-out",
        fontFamily: "-apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
    },

    // --- POPUP STYLES ---
    popupHeader: {
        margin: "0 0 10px 0",
        fontSize: "1.1rem",
        fontWeight: "700",
        color: "#333"
    },
    scoreContainer: {
        background: "#f1f8e9", // Very light green background
        borderRadius: "8px",
        padding: "10px",
        marginBottom: "15px",
        border: "1px solid #c8e6c9"
    },
    scoreLabel: {
        fontSize: "0.75rem",
        textTransform: "uppercase",
        letterSpacing: "1px",
        color: "#558b2f",
        marginBottom: "2px"
    },
    scoreValue: {
        fontSize: "1.8rem",
        fontWeight: "800",
        color: "#2e7d32",
        lineHeight: "1"
    },
    unplayedText: {
        color: "#757575",
        fontStyle: "italic",
        marginBottom: "15px",
        fontSize: "0.9rem"
    },
    popupBtn: {
        background: "#222", // Black/Dark Grey is trendy for primary actions
        color: "white",
        border: "none",
        padding: "10px 20px",
        borderRadius: "8px",
        cursor: "pointer",
        fontWeight: "600",
        fontSize: "0.85rem",
        width: "100%",
        transition: "background 0.2s"
    },

    // --- FORM STYLES ---
    input: {
        width: "100%",
        padding: "12px",
        borderRadius: "8px",
        border: "1px solid #ddd",
        fontSize: "1rem",
        background: "#f9f9f9"
    },
    label: {
        display: "block",
        marginBottom: "6px",
        fontWeight: "600",
        fontSize: "0.85rem",
        color: "#444"
    },
    btnCancel: {
        flex: 1, padding: "12px", background: "transparent",
        border: "1px solid #ddd", borderRadius: "8px",
        cursor: "pointer", fontWeight: "600", color: "#666"
    },
    btnSubmit: {
        flex: 1, padding: "12px", background: "#2e7d32",
        color: "white", border: "none", borderRadius: "8px",
        cursor: "pointer", fontWeight: "600"
    }
};

export default MapView;