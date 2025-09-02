import React, { useEffect, useState } from "react";
import { MapContainer, TileLayer, Marker, Popup, Circle } from "react-leaflet";
import "leaflet/dist/leaflet.css";

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
                <React.Fragment key={course.id}>
                    <Circle
                        center={[course.latitude, course.longitude]}
                        radius={1000} // Adjust radius as needed (in meters)
                        pathOptions={{
                            color: playedIds.has(course.id) ? "green" : "red",
                            fillColor: playedIds.has(course.id) ? "green" : "red",
                            fillOpacity: 0.3
                        }}
                    />
                    <Marker
                        position={[course.latitude, course.longitude]}
                        opacity={playedIds.has(course.id) ? 1 : 0.5}
                    >
                        <Popup>
                            {course.name}
                            {playedIds.has(course.id) ? " (Played)" : (
                                <button
                                    onClick={async () => {
                                        await fetch(`http://localhost:8080/api/users/${user.userId}/played-courses`, {
                                            method: "POST",
                                            headers: { "Content-Type": "application/json" },
                                            body: JSON.stringify({ courseExternalId: course.externalId })
                                        });
                                        // Refresh played courses
                                        fetch(`http://localhost:8080/api/users/${user.userId}/played-courses`)
                                            .then(res => res.json())
                                            .then(data => setPlayed(Array.isArray(data) ? data : []));
                                    }}
                                >
                                    Mark as Played
                                </button>
                            )}
                        </Popup>
                    </Marker>
                </React.Fragment>
            ))}
        </MapContainer>
    );
}

export default MapView;