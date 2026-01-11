import React, {useState, useEffect} from "react";
import Login from "./Login";
import MapView from "./MapView";
import Overview from "./Overview";
import SocialView from "./SocialView";
import "./Navigation.css"
import "./App.css";

function App() {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [view, setView] = useState("map");
    const [mapFocus, setMapFocus] = useState(null);
    const [showProfileMenu, setShowProfileMenu] = useState(false);

    useEffect(() => {
        fetch("/api/auth/me")
            .then(res => (res.ok ? res.json() : Promise.reject()))
            .then(data => {
                setUser(data);
                setLoading(false);
            })
            .catch(() => setLoading(false));
    }, []);
    const handleCourseClick = (lat, lng) => {
        setMapFocus({ lat, lng, zoom: 15 });
        setView("map");
    };

    const handleLogout = () => {
        window.location.href = "/logout";
    };

    if (loading) return <div>Laster...</div>;
    if (!user) return <Login onLogin={setUser} />;

    const playerId = user?.userId ?? user?.id ?? null;

    return (
        <div className="App">

            {/* 1. NEW TOP HEADER */}
            {/* TOP HEADER */}
            <header className="app-header">
                {/* LEFT: Logo */}
                <div className="app-brand" style={{ fontFamily: "'Playball', cursive", fontSize: "1.8rem" }}>
                    <span style={{ fontSize: "1.5rem" }}>⛳</span> GolfJakten
                </div>

                {/* RIGHT: Profile Section (UPDATED) */}
                <div
                    className="profile-section"
                    onClick={() => setShowProfileMenu(!showProfileMenu)} // Toggle menu
                    title="Kontoinnstillinger"
                >
                    <span className="desktop-user-name">{user?.name || "Golfspiller"}</span>
                    <img
                        src={user?.photo || "https://ui-avatars.com/api/?name=Bruker"}
                        alt="Profil"
                        className="header-avatar"
                    />

                    {/* 3. THE DROPDOWN MENU */}
                    {showProfileMenu && (
                        <div className="profile-dropdown">
                            {/* Optional: Add 'Profile' or 'Settings' links here later */}

                            <button onClick={handleLogout} className="dropdown-item danger">
                                🚪 Logg ut
                            </button>
                        </div>
                    )}
                </div>
            </header>

            {/* 2. MAIN CONTENT AREA */}
            <div style={{
                position: "fixed",
                top: "60px",
                bottom: "80px",
                left: 0,
                right: 0,
                display: "flex",
                flexDirection: "column"
            }}>
                <div style={{ flex: 1, overflowY: view === "map" ? "hidden" : "auto" }}>
                    {view === "map" && (
                        <MapView
                            user={user}
                            playerId={playerId}
                            focus={mapFocus}
                            onFocusComplete={() => setMapFocus(null)}
                        />
                    )}
                    {view === "overview" && (
                        <Overview user={user} onNavigate={handleCourseClick} />
                    )}
                    {view === "social" && <SocialView user={user} />}
                </div>
            </div>

            {/* 3. NEW BOTTOM TAB BAR */}
            <nav className="bottom-nav">
                <NavItem
                    label="Kart"
                    active={view === "map"}
                    onClick={() => setView("map")}
                    icon={<MapIcon />}
                />
                <NavItem
                    label="Oversikt"
                    active={view === "overview"}
                    onClick={() => setView("overview")}
                    icon={<ChartIcon />}
                />
                <NavItem
                    label="Venner"
                    active={view === "social"}
                    onClick={() => setView("social")}
                    icon={<FriendsIcon />}
                />
            </nav>
        </div>
    );
}

// --- HELPER COMPONENTS (Put these at bottom of App.js or in separate files) ---

const NavItem = ({ label, active, onClick, icon }) => (
    <button className={`nav-item ${active ? "active" : ""}`} onClick={onClick}>
        {icon}
        <span className="nav-label">{label}</span>
    </button>
);

// Simple SVG Icons so you don't need to install a library
const MapIcon = () => (
    <svg className="nav-icon" viewBox="0 0 24 24">
        <path d="M20.5 3l-6 2-6-2-5.5 1.83v15.17l6 2 6-2 6 2V3zM14 18l-6 2v-13l6-2v13zm6-3l-4-1.33V4.67l4 1.33v10z"/>
    </svg>
);

const ChartIcon = () => (
    <svg className="nav-icon" viewBox="0 0 24 24">
        <path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zM9 17H7v-7h2v7zm4 0h-2V7h2v10zm4 0h-2v-4h2v4z"/>
    </svg>
);

const FriendsIcon = () => (
    <svg className="nav-icon" viewBox="0 0 24 24">
        <path d="M16 11c1.66 0 2.99-1.34 2.99-3S17.66 5 16 5c-1.66 0-3 1.34-3 3s1.34 3 3 3zm-8 0c1.66 0 2.99-1.34 2.99-3S9.66 5 8 5C6.34 5 5 6.34 5 8s1.34 3 3 3zm0 2c-2.33 0-7 1.17-7 3.5V19h14v-2.5c0-2.33-4.67-3.5-7-3.5zm8 0c-.29 0-.62.02-.97.05 1.16.84 1.97 1.97 1.97 3.45V19h6v-2.5c0-2.33-4.67-3.5-7-3.5z"/>
    </svg>
);

export default App;