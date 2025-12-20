import React, { useState, useEffect } from "react";
import Login from "./Login";
import MapView from "./MapView";
import OverviewView from "./OverviewView";
import "./App.css";

function App() {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [view, setView] = useState("map"); // "map" or "overview"

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

    // safe player id extraction so children don't access undefined properties
    const playerId = user?.userId ?? user?.id ?? null;

    return (
        <div className="App" style={{ minHeight: "100vh", paddingBottom: 72 }}>
            <div className="profile-bar" style={{ display: "flex", alignItems: "center", gap: 8, padding: 8 }}>
                <img src={user?.photo} alt="Profile" style={{ width: 40, height: 40, borderRadius: "50%" }} />
                <span>{user?.name || user?.username || "User"}</span>
            </div>

            <div style={{ height: "calc(100vh - 72px)", display: "flex", flexDirection: "column" }}>
                <div style={{ flex: 1, overflow: "hidden" }}>
                    {view === "map" ? (
                        <MapView user={user} playerId={playerId} />
                    ) : (
                        <OverviewView user={user} playerId={playerId} />
                    )}
                </div>
            </div>

            <div
                style={{
                    position: "fixed",
                    left: 0,
                    right: 0,
                    bottom: 0,
                    display: "flex",
                    justifyContent: "center",
                    gap: 12,
                    padding: 12,
                    background: "rgba(255,255,255,0.98)",
                    boxShadow: "0 -1px 6px rgba(0,0,0,0.12)",
                    zIndex: 1000
                }}
                role="tablist"
                aria-label="Map or Overview"
            >
                <button
                    onClick={() => setView("map")}
                    aria-pressed={view === "map"}
                    style={{
                        padding: "8px 16px",
                        fontWeight: view === "map" ? "600" : "400"
                    }}
                >
                    Map
                </button>
                <button
                    onClick={() => setView("overview")}
                    aria-pressed={view === "overview"}
                    style={{
                        padding: "8px 16px",
                        fontWeight: view === "overview" ? "600" : "400"
                    }}
                >
                    Overview
                </button>
            </div>
        </div>
    );
}

export default App;