import React, { useEffect, useState } from "react";
import { MapContainer, TileLayer, Marker, Popup, Circle } from "react-leaflet";
import "leaflet/dist/leaflet.css";
// import { apiUrl } from "./api"; // Keep this if you use it for other things
import LogRoundModal from "./LogRoundModal"; // <--- 1. Import the Modal

import L from "leaflet";
import markerIcon2x from "leaflet/dist/images/marker-icon-2x.png";
import markerIcon from "leaflet/dist/images/marker-icon.png";
import markerShadow from "leaflet/dist/images/marker-shadow.png";

L.Icon.Default.mergeOptions({
    iconRetinaUrl: markerIcon2x,
    iconUrl: markerIcon,
    shadowUrl: markerShadow,
});

function MapView({ user }) {
    const [courses, setCourses] = useState([]);
    const [played, setPlayed] = useState([]);

    // --- 2. New State for Modal ---
    const [modalOpen, setModalOpen] = useState(false);
    const [selectedCourse, setSelectedCourse] = useState(null);

    useEffect(() => {
        fetch("/api/courses")
            .then(res => res.json())
            .then(setCourses);

        fetch(`/api/users/${user.userId}/played-courses`)
            .then(res => res.json())
            .then(data => setPlayed(Array.isArray(data) ? data : []));
    }, [user]);

    const playedIds = new Set(played.map(c => c.id));

    // --- 3. Modal Helpers ---
    const openLogRoundModal = (course) => {
        setSelectedCourse(course);
        setModalOpen(true);
    };

    const handleSaveRound = async (roundData) => {
        try {
            const response = await fetch("/api/rounds", {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(roundData),
            });

            if (response.ok) {
                setModalOpen(false);
                setPlayed(prev => {
                    if (prev.some(c => c.id === selectedCourse.id)) return prev;
                    return [...prev, selectedCourse];
                });
                alert("Round saved!");
            } else {
                // Throw error so the Modal catches it and resets the button
                throw new Error("Server responded with error");
            }
        } catch (err) {
            console.error("Failed to save round:", err);
            throw err; // Re-throw to Modal
        }
    };

    return (
        // Use a Fragment to hold both Map and Modal without breaking layout
        <>
            <MapContainer center={[60.2, 5.5]} zoom={8} style={{ height: "80vh", width: "100%" }}>
                <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />

                {courses.map(course => {
                    const isPlayed = playedIds.has(course.id);
                    return (
                        <React.Fragment key={course.id}>
                            <Circle
                                key={`${course.id}-${isPlayed}`}
                                center={[course.latitude, course.longitude]}
                                radius={1000}
                                pathOptions={{
                                    color: isPlayed ? "green" : "red",
                                    fillColor: isPlayed ? "green" : "red",
                                    fillOpacity: 0.3,
                                }}
                            />
                            <Marker
                                key={`${course.id}-marker-${isPlayed}`}
                                position={[course.latitude, course.longitude]}
                                opacity={isPlayed ? 1 : 0.5}
                            >
                                <Popup>
                                    <div style={{ textAlign: "center" }}>
                                        <b>{course.name}</b>
                                        <br />

                                        {/* 1. Status Line */}
                                        <div style={{ margin: "8px 0" }}>
                                            {isPlayed ? (
                                                <span style={{ color: "green", fontWeight: "bold" }}>
                    ✅ Played
                </span>
                                            ) : (
                                                <span style={{ color: "grey", fontStyle: "italic" }}>
                    Not yet played
                </span>
                                            )}
                                        </div>

                                        {/* 2. Button (Always Visible) */}
                                        <button
                                            onClick={() => openLogRoundModal(course)}
                                            style={{
                                                cursor: "pointer",
                                                padding: "6px 12px",
                                                background: isPlayed ? "#4CAF50" : "#2196F3", // Green if played, Blue if new
                                                color: "white",
                                                border: "none",
                                                borderRadius: "4px",
                                                fontSize: "0.9em",
                                                width: "100%"
                                            }}
                                        >
                                            {isPlayed ? "Log Another Round" : "Log Round"}
                                        </button>
                                    </div>
                                </Popup>
                            </Marker>
                        </React.Fragment>
                    );
                })}
            </MapContainer>

            {/* --- 5. Render Modal Outside Map --- */}
            <LogRoundModal
                isOpen={modalOpen}
                course={selectedCourse}
                onClose={() => setModalOpen(false)}
                onSubmit={handleSaveRound}
            />
        </>
    );
}

export default MapView;