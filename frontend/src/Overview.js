// JavaScript
import React, { useState, useEffect } from "react";
import "./SocialView.css";


function Overview({onNavigate}) {
    const [data, setData] = useState(null);
    const [loading, setLoading] = useState(true);
    const [roundToDelete, setRoundToDelete] = useState(null);
    const [expandedRegion, setExpandedRegion] = useState(true);

    function getCookie(name) {
        const match = document.cookie.match(new RegExp('(^|; )' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[2]) : null;
    }

    // --- 1. REUSABLE FETCH FUNCTION ---
    const loadOverview = () => {
        setLoading(true);
        fetch('/api/overview')
            .then(res => {
                if (!res.ok) throw new Error("Failed to load");
                return res.json();
            })
            .then(dt => {
                setData(dt);
                setLoading(false);
            })
            .catch(err => {
                console.error(err);
                setLoading(false);
            });
    };

    // Initial Load
    useEffect(() => {
        loadOverview();
    }, []);


    const confirmDelete = (roundId) => {
        setRoundToDelete(roundId); // This triggers the modal to show
    };


    const performDelete = async () => {
        if (!roundToDelete) return;

        try {
            const csrf = getCookie('XSRF-TOKEN');
            const res = await fetch(`/api/rounds/${roundToDelete}`, { method: 'DELETE', credentials: 'include', headers:{ ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {})}
            });
            if (res.ok) {
                loadOverview(); // Refresh data
                setRoundToDelete(null); // Close modal
            } else {
                alert("Kunne ikke slette runden.");
            }
        } catch (error) {
            console.error("Error deleting round:", error);
        }
    };

    if (loading && !data) return <div style={{textAlign:"center", marginTop:"50px"}}>Laster...</div>;
    if (!data) return <div style={{textAlign:"center", marginTop:"50px"}}>Feil ved innlasting av profil.</div>;

    const regions = Object.entries(data.regionStats || {}).map(([name, stat]) => ({
        name: name,
        played: stat.playedCount,
        total: stat.totalCount,
        percent: stat.totalCount > 0 ? (stat.playedCount / stat.totalCount) * 100 : 0,
        courses: stat.courses || []
    })).sort((a, b) => b.played - a.played);

    const toggleRegion = (regionName) => {
        setExpandedRegion(prev => prev === regionName ? null : regionName);
    };

    return (
        <div className="social-container" style={{ position: "relative" }}>

            {/* --- CUSTOM MODAL OVERLAY --- */}
            {roundToDelete && (
                <div className="modal-overlay">
                    <div className="modal-content">
                        <h3>Slette runde?</h3>
                        <p>Dette vil fjerne runden og oppdatere statistikken din. Dette kan ikke angres.</p>
                        <div style={{ display: "flex", gap: "10px", justifyContent: "flex-end", marginTop: "20px" }}>
                            <button
                                onClick={() => setRoundToDelete(null)}
                                className="btn btn-outline"
                            >
                                Avbryt
                            </button>
                            <button
                                onClick={performDelete}
                                className="btn btn-reject"
                            >
                                Ja, slett
                            </button>
                        </div>
                    </div>
                </div>
            )}

            {/* HERO PROFILE CARD */}
            <div className="player-card" style={{ marginBottom: "20px", padding: "30px", background: "linear-gradient(135deg, #2E7D32 0%, #4CAF50 100%)", color: "white" }}>
                <div style={{ display: "flex", alignItems: "center", gap: "20px" }}>
                    <img
                        src={data.avatar || `https://ui-avatars.com/api/?name=${data.displayName}`}
                        alt="profil"
                        style={{ width: "80px", height: "80px", borderRadius: "50%", border: "4px solid rgba(255,255,255,0.3)" }}
                    />
                    <div>
                        <h2 style={{ margin: 0, fontSize: "1.8rem" }}>{data.displayName}</h2>
                        <p style={{ margin: "5px 0 0 0", opacity: 0.9 }}>{data.email}</p>
                    </div>
                </div>
            </div>

            {/* STATS */}
            <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr 1fr", gap: "15px", marginBottom: "30px" }}>
                <StatCard label="Spilte baner" value={data.totalPlayed} icon="⛳" />
                <StatCard label="Totalt antall baner" value={data.totalCourses} icon="🌍" />
                <StatCard label="Fremgang" value={data.percentageComplete.toFixed(1) + "%"} icon="📈" />
            </div>

            {/* COUNTY PROGRESS */}
            <div className="section-header">📍 Fremgang per fylke</div>
            <div className="region-grid">
                {regions.map((region) => {
                    const isOpen = expandedRegion === region.name;

                    return (
                        <div
                            key={region.name}
                            className={`region-card ${region.played === 0 ? "inactive" : ""}`}
                            // Make the whole card clickable to toggle
                            onClick={() => toggleRegion(region.name)}
                            style={{ cursor: "pointer", transition: "all 0.3s" }}
                        >
                            <div className="region-header">
                                <span>{region.name} {isOpen ? "▼" : "▶"}</span>
                                <span style={{color: region.played > 0 ? "#2E7D32" : "#999"}}>
                                    {region.played} / {region.total}
                                </span>
                            </div>
                            <div className="progress-track">
                                <div className="progress-fill" style={{ width: `${region.percent}%`, background: region.played > 0 ? "#4CAF50" : "#ccc" }}></div>
                            </div>

                            {/* --- DROP DOWN LIST --- */}
                            {isOpen && (
                                <div style={{ marginTop: "15px", borderTop: "1px solid #eee", paddingTop: "10px" }}>
                                    {region.courses.map(course => (
                                        <div
                                            key={course.id}
                                            onClick={(e) => {
                                                e.stopPropagation(); // Don't close the accordion
                                                // CALL THE NAVIGATION FUNCTION
                                                onNavigate(course.latitude, course.longitude);
                                            }}
                                            style={{
                                                padding: "8px 0",
                                                display: "flex",
                                                alignItems: "center",
                                                gap: "8px",
                                                cursor: "pointer",
                                                color: course.played ? "#2E7D32" : "#555",
                                                fontSize: "0.9rem"
                                            }}
                                            onMouseOver={(e) => e.currentTarget.style.background = "#f9f9f9"}
                                            onMouseOut={(e) => e.currentTarget.style.background = "transparent"}
                                        >
                                            <span>{course.played ? "✅" : "⬜"}</span>
                                            <span style={{ fontWeight: course.played ? "bold" : "normal" }}>
                                                {course.name}
                                            </span>
                                        </div>
                                    ))}
                                </div>
                            )}
                        </div>
                    );
                })}
            </div>

            {/* --- 3. RECENT ACTIVITY (WITH DELETE BUTTON) --- */}
            <div className="section-header">📅 Siste aktivitet</div>

            {(!data.recentRounds || data.recentRounds.length === 0) ? (
                <div className="empty-state">Du har ikke spilt noen runder ennå.</div>
            ) : (
                <div className="leaderboard-list">
                    {data.recentRounds.map((round) => (
                        <div key={round.id} className="player-card">
                            <div className="player-info">
                                <div style={{ background: "#e8f5e9", width: "40px", height: "40px", borderRadius: "8px", display: "flex", alignItems: "center", justifyContent: "center", fontSize: "1.2rem" }}>
                                    🏌️
                                </div>
                                <div>
                                    <div className="player-name">{round.courseName}</div>
                                    <div style={{ fontSize: "0.85rem", color: "#888" }}>{round.date}</div>
                                </div>
                            </div>

                            {/* Stats + Delete Button Wrapper */}
                            <div style={{ display: "flex", alignItems: "center", gap: "20px" }}>
                                <div className="stats">
                                    <div className="stat-item">
                                        <span className="stat-val" style={{color: getScoreColor(round.score)}}>{round.score}</span>
                                        <span className="stat-label">Poeng</span>
                                    </div>
                                </div>

                                {/* DELETE BUTTON */}
                                <button
                                    onClick={() => confirmDelete(round.id)}
                                    style={{
                                        background: "transparent",
                                        border: "none",
                                        cursor: "pointer",
                                        color: "#999",
                                        fontSize: "1.2rem",
                                        padding: "8px",
                                        borderRadius: "50%",
                                        transition: "background 0.2s"
                                    }}
                                    onMouseOver={(e) => e.currentTarget.style.background = "#ffebee"}
                                    onMouseOut={(e) => e.currentTarget.style.background = "transparent"}
                                    title="Slett runde"
                                >
                                    🗑️
                                </button>
                            </div>
                        </div>
                    ))}
                </div>
            )}
        </div>
    );
}

// Helpers
const StatCard = ({ label, value, icon }) => (
    <div style={{ background: "white", padding: "20px", borderRadius: "12px", boxShadow: "0 2px 4px rgba(0,0,0,0.05)", textAlign: "center" }}>
        <div style={{ fontSize: "2rem", marginBottom: "5px" }}>{icon}</div>
        <div style={{ fontSize: "1.5rem", fontWeight: "800", color: "#333" }}>{value}</div>
        <div style={{ fontSize: "0.8rem", color: "#888", textTransform: "uppercase", letterSpacing: "1px" }}>{label}</div>
    </div>
);

const getScoreColor = (score) => {
    if (score < 72) return "#d32f2f";
    if (score < 80) return "#2E7D32";
    return "#333";
};

export default Overview;