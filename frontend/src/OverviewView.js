import React, { useState, useEffect, useMemo } from "react";
import { Doughnut } from "react-chartjs-2";
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from "chart.js";

// Register Chart.js components
ChartJS.register(ArcElement, Tooltip, Legend);

function OverviewView({ user }) {
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);
    const [selectedRegion, setSelectedRegion] = useState("All");

    // --- 1. Fetch Logic ---
    const loadStats = () => {
        setLoading(true);
        fetch('/api/overview')
            .then(res => res.ok ? res.json() : Promise.reject(res))
            .then(data => {
                setStats(data);
                setLoading(false);
            })
            .catch(err => {
                setError("Failed to load");
                setLoading(false);
            });
    };

    useEffect(() => {
        loadStats();
    }, []);

    // --- 2. Delete Handler ---
    const handleDelete = async (roundId) => {
        if (!window.confirm("Are you sure you want to delete this round?")) return;

        try {
            const res = await fetch(`/api/rounds/${roundId}`, { method: 'DELETE' });
            if (res.ok) {
                loadStats(); // Re-fetch data without reloading page
            } else {
                alert("Failed to delete round.");
            }
        } catch (err) {
            console.error("Error deleting round:", err);
        }
    };

    // --- 3. Chart Data Calculation ---
    const chartData = useMemo(() => {
        if (!stats) return null;

        let played, total;

        if (selectedRegion === "All") {
            played = stats.totalPlayed;
            total = stats.totalCourses;
        } else {
            const region = stats.regionStats[selectedRegion] || { played: 0, total: 1 };
            played = region.played;
            total = region.total;
        }

        const unplayed = total - played;
        const percentage = total > 0 ? ((played / total) * 100).toFixed(1) : 0;

        return {
            labels: ['Played', 'Unplayed'],
            datasets: [{
                data: [played, unplayed],
                backgroundColor: ['#4CAF50', '#E0E0E0'],
                borderWidth: 1,
            }],
            displayTotal: total,
            displayPlayed: played,
            displayPercentage: percentage
        };
    }, [stats, selectedRegion]); // Recalculates when these change

    // --- Loading / Error States ---
    if (loading) return <div style={{ padding: 20 }}>Loading stats...</div>;
    if (error) return <div style={{ padding: 20, color: "red" }}>{error}</div>;
    if (!stats || !chartData) return null;

    return (
        <div style={{ padding: 20, overflowY: "auto", height: "100%" }}>

            {/* Header & Region Selector */}
            <div style={{ display: "flex", justifyContent: "space-between", alignItems: "center" }}>
                <h2>Overview</h2>
                <select
                    value={selectedRegion}
                    onChange={(e) => setSelectedRegion(e.target.value)}
                    style={{ padding: "8px", borderRadius: "5px", border: "1px solid #ccc" }}
                >
                    <option value="All">All of Norway</option>
                    {Object.keys(stats.regionStats).sort().map(region => (
                        <option key={region} value={region}>{region}</option>
                    ))}
                </select>
            </div>

            {/* Stats Summary & Chart */}
            <div style={{ display: "flex", gap: "30px", alignItems: "center", marginBottom: "30px", marginTop: "20px" }}>
                <div style={{ width: "180px", height: "180px" }}>
                    {/* THIS WAS MISSING -> The Doughnut Chart */}
                    <Doughnut
                        data={chartData}
                        options={{ plugins: { legend: { display: false } } }}
                    />
                </div>

                <div style={{ display: "flex", gap: "40px" }}>
                    {/* Progress Stats */}
                    <div>
                        <div style={{ fontSize: "2em", fontWeight: "bold" }}>
                            {chartData.displayPlayed} / {chartData.displayTotal}
                        </div>
                        <div style={{ color: "#666" }}>Courses Played</div>
                        <div style={{ fontWeight: "bold", marginTop: 5, color: "#4CAF50" }}>
                            {chartData.displayPercentage}% Completed
                        </div>
                    </div>
                </div>
            </div>

            <hr />

            {/* Recent Rounds Table (Only shown if "All" is selected) */}
            {selectedRegion === "All" && stats.recentRounds && stats.recentRounds.length > 0 && (
                <div style={{ marginBottom: "30px" }}>
                    <h3>Recent Rounds</h3>
                    <table style={{ width: "100%", borderCollapse: "collapse", fontSize: "0.9em" }}>
                        <thead>
                        <tr style={{ textAlign: "left", borderBottom: "2px solid #eee", color: "#666" }}>
                            <th style={{ padding: "8px" }}>Date</th>
                            <th style={{ padding: "8px" }}>Course</th>
                            <th style={{ padding: "8px", textAlign: "right" }}>Score</th>
                            <th style={{ padding: "8px" }}></th>
                        </tr>
                        </thead>
                        <tbody>
                        {stats.recentRounds.map(round => (
                            <tr key={round.id} style={{ borderBottom: "1px solid #f0f0f0" }}>
                                <td style={{ padding: "10px" }}>{round.date}</td>
                                <td style={{ padding: "10px", fontWeight: "500" }}>{round.courseName}</td>
                                <td style={{ padding: "10px", textAlign: "right", fontWeight: "bold" }}>
                                    {round.score}
                                </td>
                                <td style={{ padding: "10px", textAlign: "right" }}>
                                    <button
                                        onClick={() => handleDelete(round.id)}
                                        style={{ background: "none", border: "none", cursor: "pointer", fontSize: "1.2em", opacity: 0.7 }}
                                        title="Delete Round"
                                    >
                                        🗑️
                                    </button>
                                </td>
                            </tr>
                        ))}
                        </tbody>
                    </table>
                </div>
            )}

            {/* Region List OR Detailed Course List */}
            {selectedRegion === "All" ? (
                <>
                    <h3>By County</h3>
                    <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: "10px" }}>
                        {Object.entries(stats.regionStats).map(([regionName, regionData]) => (
                            <div key={regionName} onClick={() => setSelectedRegion(regionName)} style={{
                                cursor: "pointer", border: "1px solid #ddd", padding: "10px", borderRadius: "8px",
                                backgroundColor: regionData.played > 0 ? "#f0fdf4" : "#fff"
                            }}>
                                <strong>{regionName}</strong>
                                <div style={{ fontSize: "0.9em", color: "#555" }}>
                                    {regionData.played} / {regionData.total}
                                </div>
                                <div style={{ width: "100%", height: "4px", background: "#eee", marginTop: "5px" }}>
                                    <div style={{ width: `${regionData.percentage}%`, height: "100%", background: "#4CAF50" }}></div>
                                </div>
                            </div>
                        ))}
                    </div>
                </>
            ) : (
                <div>
                    <button onClick={() => setSelectedRegion("All")} style={{ marginBottom: "20px", cursor: "pointer" }}>← Back to Overview</button>

                    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "20px" }}>
                        {/* Played Column */}
                        <div>
                            <h3 style={{ color: "#2e7d32" }}>✅ Played ({
                                stats.regionStats[selectedRegion].courses.filter(c => c.played).length
                            })</h3>
                            <ul style={{ listStyle: "none", padding: 0 }}>
                                {stats.regionStats[selectedRegion].courses.filter(c => c.played).map(c => (
                                    <li key={c.id} style={{ padding: "8px", borderBottom: "1px solid #eee" }}>{c.name}</li>
                                ))}
                            </ul>
                        </div>
                        {/* Missing Column */}
                        <div>
                            <h3 style={{ color: "#d32f2f" }}>❌ Missing ({
                                stats.regionStats[selectedRegion].courses.filter(c => !c.played).length
                            })</h3>
                            <ul style={{ listStyle: "none", padding: 0 }}>
                                {stats.regionStats[selectedRegion].courses.filter(c => !c.played).map(c => (
                                    <li key={c.id} style={{ padding: "8px", borderBottom: "1px solid #eee", color: "#666" }}>{c.name}</li>
                                ))}
                            </ul>
                        </div>
                    </div>
                </div>
            )}
        </div>
    );
}

export default OverviewView;