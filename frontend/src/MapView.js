import React, { useState, useEffect } from "react";
import { MapContainer, TileLayer, Marker, Popup, useMap } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import { UnplayedIcon, PlayedIcon } from "./MapIcons";

// 1. Controller for "Fly To" animation
function MapController({ focus, onFocusComplete }) {
    const map = useMap();
    useEffect(() => {
        if (focus) {
            map.flyTo([focus.lat, focus.lng], focus.zoom, { animate: true, duration: 1.5 });
            onFocusComplete();
        }
    }, [focus, map, onFocusComplete]);
    return null;
}

function MapView({ user, focus, onFocusComplete }) {
    // --- Data State ---
    const [courses, setCourses] = useState([]);
    const [played, setPlayed] = useState([]);

    // --- Modal State ---
    const [modalOpen, setModalOpen] = useState(false);
    const [selectedCourse, setSelectedCourse] = useState(null);
    const [score, setScore] = useState("");
    const [date, setDate] = useState(new Date().toISOString().split("T")[0]);
    const [submitting, setSubmitting] = useState(false);

    // --- Initial Fetch ---
    useEffect(() => {
        fetch("/api/courses")
            .then(res => res.json())
            .then(setCourses);

        if (user) {
            fetch(`/api/users/${user.userId}/played-courses`)
                .then(res => res.json())
                .then(data => setPlayed(Array.isArray(data) ? data : []));
        }
    }, [user]);

    // Calculate played IDs for the map markers
    const playedIds = new Set(played.map(c => c.id));

    // --- Modal Actions ---
    const openLogRoundModal = (course) => {
        setSelectedCourse(course);
        setScore(""); // Reset form
        setModalOpen(true);
    };

    const handleClose = () => {
        setModalOpen(false);
        setSelectedCourse(null);
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        if (!score || !selectedCourse) return;

        setSubmitting(true);

        const payload = {
            courseId: selectedCourse.id,
            date: date,
            score: parseInt(score)
        };

        try {
            const res = await fetch("/api/rounds", {
                method: "POST", headers: { "Content-Type": "application/json" }, body: JSON.stringify(payload)
            });

            if (res.ok) {
                // Success!
                // 1. Update the 'played' list locally so the flag turns GREEN instantly
                setPlayed(prev => [...prev, selectedCourse]);
                // 2. Close Modal
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
            <MapController focus={focus} onFocusComplete={onFocusComplete} />
            <TileLayer
                url="https://cartodb-basemaps-{s}.global.ssl.fastly.net/light_all/{z}/{x}/{y}.png"
                attribution='&copy; OpenStreetMap &copy; CartoDB'
            />

            {courses.map(course => {
                const isPlayed = playedIds.has(course.id);

                return (<Marker
                    key={course.id}
                    position={[course.latitude, course.longitude]}
                    icon={isPlayed ? PlayedIcon : UnplayedIcon}
                >
                    <Popup className="golf-popup">
                        <div style={{ textAlign: "center" }}>
                            <h3 style={{ margin: "0 0 5px 0", color: "#2E7D32" }}>{course.name}</h3>
                            <p style={{ margin: "0 0 10px 0", fontSize: "0.9rem", color: "#666" }}>
                                {isPlayed ? "✅ Bane spilt" : "❌ Ikke spilt"}
                            </p>

                            <button
                                onClick={() => openLogRoundModal(course)}
                                style={styles.popupBtn}
                            >
                                📝 Registrer runde
                            </button>
                        </div>
                    </Popup>
                </Marker>);
            })}
        </MapContainer>

        {/* --- MODAL OVERLAY (Now Translated) --- */}
        {modalOpen && selectedCourse && (
            <div style={styles.modalOverlay}>
                <div style={styles.modalContent}>
                    <h3 style={{ color: "#2E7D32", marginTop: 0 }}>Registrer runde ⛳</h3>
                    <p><strong>Bane:</strong> {selectedCourse.name}</p>

                    <form onSubmit={handleSubmit} style={{ display: "flex", flexDirection: "column", gap: "15px" }}>
                        <div>
                            <label style={styles.label}>Dato</label>
                            <input
                                type="date"
                                value={date}
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

// --- STYLES ---
const styles = {
    popupBtn: {
        background: "#4CAF50",
        color: "white",
        border: "none",
        padding: "8px 16px",
        borderRadius: "20px",
        cursor: "pointer",
        fontWeight: "bold",
        fontSize: "0.85rem",
        boxShadow: "0 2px 5px rgba(0,0,0,0.2)"
    },
    modalOverlay: {
        position: "fixed",
        top: 0,
        left: 0,
        right: 0,
        bottom: 0,
        background: "rgba(0,0,0,0.6)",
        zIndex: 9999,
        display: "flex",
        alignItems: "center",
        justifyContent: "center",
        backdropFilter: "blur(3px)"
    },
    modalContent: {
        background: "white",
        padding: "25px",
        borderRadius: "12px",
        width: "90%",
        maxWidth: "350px",
        boxShadow: "0 10px 25px rgba(0,0,0,0.3)",
        animation: "popIn 0.2s ease-out"
    },
    label: { display: "block", marginBottom: "5px", fontWeight: "600", fontSize: "0.9rem", color: "#555" },
    input: { width: "100%", padding: "10px", borderRadius: "6px", border: "1px solid #ccc", fontSize: "1rem" },
    btnCancel: {
        flex: 1,
        padding: "12px",
        background: "white",
        border: "1px solid #ccc",
        borderRadius: "6px",
        cursor: "pointer",
        fontWeight: "600"
    },
    btnSubmit: {
        flex: 1,
        padding: "12px",
        background: "#2E7D32",
        color: "white",
        border: "none",
        borderRadius: "6px",
        cursor: "pointer",
        fontWeight: "600"
    }
};

export default MapView;