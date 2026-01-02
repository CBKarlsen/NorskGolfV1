import React, { useState, useEffect } from "react";
import Login from "./Login";
import MapView from "./MapView";
import OverviewView from "./OverviewView";
import "./App.css";

function App() {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [view, setView] = useState("map");

    useEffect(() => {
        fetch("/api/auth/me")
            .then(res => (res.ok ? res.json() : Promise.reject()))
            .then(data => { setUser(data); setLoading(false); })
            .catch(() => setLoading(false));
    }, []);

    if (loading) return <div>Loading...</div>;
    if (!user) return <Login onLogin={setUser} />;

    const playerId = user?.userId ?? user?.id ?? null;

    return (
        <div className="App app-with-bottom-bar">
            <div className="profile-bar" style={{ display: "flex", alignItems: "center", gap: 8, padding: 8 }}>
                <img src={user?.photo} alt="Profile" style={{ width: 40, height: 40, borderRadius: "50%" }} />
                <span>{user?.name || user?.username || "User"}</span>
            </div>

            <div style={{ height: "calc(100vh - 72px)", display: "flex", flexDirection: "column" }}>
                <div style={{ flex: 1, overflow: "hidden" }}>
                    {view === "map" ? <MapView user={user} playerId={playerId} /> : <OverviewView user={user} playerId={playerId} />}
                </div>
            </div>

            <div className="bottom-bar" role="tablist" aria-label="Map or Overview">
                <button
                    className="toggle-btn"
                    onClick={() => setView("map")}
                    aria-pressed={view === "map"}
                >
                    Map
                </button>
                <button
                    className="toggle-btn"
                    onClick={() => setView("overview")}
                    aria-pressed={view === "overview"}
                >
                    Overview
                </button>
            </div>
        </div>
    );
}

export default App;