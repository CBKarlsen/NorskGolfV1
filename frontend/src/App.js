// frontend/src/App.js
import React, { useState, useEffect } from "react";
import Login from "./Login";
import MapView from "./MapView";
import "./App.css";

function App() {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);

    useEffect(() => {
        fetch("http://localhost:8080/api/auth/me", {
            credentials: "include"
        })
            .then(res => {
                if (res.ok) return res.json();
                throw new Error("Not authenticated");
            })
            .then(data => {
                setUser(data);
                console.log("Logged-in user:", data);
                setLoading(false);
            })
            .catch(() => setLoading(false));
    }, []);

    if (loading) return <div>Loading...</div>;
    if (!user) return <Login onLogin={setUser} />;

    return (
        <div className="App">
            <div className="profile-bar">
                <img src={user.photo} alt="Profile" style={{ width: 40, borderRadius: "50%" }} />
                <span>{user.name || user.username}</span>
            </div>
            <MapView user={user} />
        </div>
    );
}

export default App;