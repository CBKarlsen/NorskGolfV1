import React, { useEffect, useState } from "react";
import { MapContainer, TileLayer, Marker, Popup, Circle } from "react-leaflet";
import "leaflet/dist/leaflet.css";
import { apiUrl } from "./api";

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

    useEffect(() => {
        fetch("/api/courses")
            .then(res => res.json())
            .then(setCourses);

        fetch(`/api/users/${user.userId}/played-courses`)
            .then(res => res.json())
            .then(data => setPlayed(Array.isArray(data) ? data : []));
    }, [user]);

    const playedIds = new Set(played.map(c => c.id));


    const handleMarkPlayed = async (course) => {
        try {
            await fetch(apiUrl(`/api/users/${user.userId}/mark-played`), {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                credentials: "include",
                body: JSON.stringify({ courseExternalId: course.externalId }),
            });

            setPlayed(prev => {
                if (prev.some(c => c.id === course.id)) return prev;
                return [...prev, course];
            });
        } catch (err) {
            console.error("Failed to mark course as played:", err);
        }
    };

    return (
        <MapContainer center={[60.2, 5.5]} zoom={8} style={{ height: "80vh", width: "100%" }}>
            <TileLayer url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png" />

            {courses.map(course => {
                const isPlayed = playedIds.has(course.id);
                return (
                    <React.Fragment key={course.id}>
                        <Circle
                            key={`${course.id}-${isPlayed}`} // force redraw when played changes
                            center={[course.latitude, course.longitude]}
                            radius={1000}
                            pathOptions={{
                                color: isPlayed ? "green" : "red",
                                fillColor: isPlayed ? "green" : "red",
                                fillOpacity: 0.3,
                            }}
                        />
                        <Marker
                            key={`${course.id}-marker-${isPlayed}`} // force redraw
                            position={[course.latitude, course.longitude]}
                            opacity={isPlayed ? 1 : 0.5}
                        >
                            <Popup>
                                {course.name}
                                {isPlayed ? " (Played)" : (
                                    <button onClick={() => handleMarkPlayed(course)}>
                                        Mark as Played
                                    </button>
                                )}
                            </Popup>
                        </Marker>
                    </React.Fragment>
                );
            })}
        </MapContainer>
    );
}

export default MapView;