import React, { useEffect, useState } from "react";
import { MapContainer, TileLayer, Marker, Popup } from "react-leaflet";
import "leaflet/dist/leaflet.css";

function MapView({ user }) {
    const [courses, setCourses] = useState([]);
    const [played, setPlayed] = useState([]);

    useEffect(() => {
        fetch("http://localhost:8080/api/courses")
            .then(res => res.json())
            .then(setCourses);

        fetch(`http://localhost:8080/api/users/${user.userId}/played-courses`)
            .then(res => res.json())
            .then(data => setPlayed(Array.isArray(data) ? data : []));
    }, [user]);

    const playedIds = new Set(played.map(c => c.id));

    return (
        <MapContainer center={[60.472, 8.4689]} zoom={6} style={{ height: "80vh", width: "100%" }}>
            <TileLayer
                url="https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png"
            />
            {courses.map(course => (
                <Marker
                    key={course.id}
                    position={[course.latitude, course.longitude]}
                    // Change color if played (requires custom icon for full effect)
                    opacity={playedIds.has(course.id) ? 1 : 0.5}
                >
                    <Popup>
                        {course.name}
                        {playedIds.has(course.id) ? " (Played)" : ""}
                    </Popup>
                </Marker>
            ))}
        </MapContainer>
    );
}

export default MapView;