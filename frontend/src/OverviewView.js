import React, { useState, useEffect } from "react";
import { Doughnut } from "react-chartjs-2";
import { Chart as ChartJS, ArcElement, Tooltip, Legend } from "chart.js";

// Register Chart.js components
ChartJS.register(ArcElement, Tooltip, Legend);

function OverviewView({ user }) {
    const [stats, setStats] = useState(null);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState(null);

    // --- THE FIX: Fetch data when this component mounts ---
    useEffect(() => {
        setLoading(true);
        fetch('/api/overview')
            .then(response => {
                if (!response.ok) {
                    throw new Error("Failed to fetch stats (Are you logged in?)");
                }
                return response.json();
            })
            .then(data => {
                setStats(data);
                setLoading(false);
            })
            .catch(err => {
                console.error("Error fetching overview:", err);
                setError(err.message);
                setLoading(false);
            });
    }, []); // Empty dependency array [] means run once on load

    // --- Loading / Error States ---
    if (loading) return <div style={{ padding: 20 }}>Loading stats...</div>;
    if (error) return <div style={{ padding: 20, color: "red" }}>Error: {error}</div>;
    if (!stats) return null;

    // --- Prepare Chart Data ---
    const played = stats.totalPlayed || 0;
    const total = stats.totalCourses || 1;
    const unplayed = total - played;

    const chartData = {
        labels: ['Played', 'Unplayed'],
        datasets: [{
            data: [played, unplayed],
            backgroundColor: ['#4CAF50', '#E0E0E0'],
            borderWidth: 1,
        }],
    };

    return (
        <div style={{ padding: 20, overflowY: "auto", height: "100%" }}>
            <h2>Overview</h2>

            {/* Stats Summary */}
            <div style={{ display: "flex", gap: "20px", alignItems: "center", marginBottom: "30px" }}>
                <div style={{ width: "180px", height: "180px" }}>
                    <Doughnut data={chartData} options={{ plugins: { legend: { position: 'bottom' } } }} />
                </div>
                <div>
                    <div style={{ fontSize: "1.5em", fontWeight: "bold" }}>
                        {stats.totalPlayed} / {stats.totalCourses}
                    </div>
                    <div>Courses Played</div>
                    <div style={{ fontWeight: "bold", marginTop: 5, color: "#4CAF50" }}>
                        {stats.percentageComplete.toFixed(1)}% Completed
                    </div>
                </div>
            </div>

            <hr />

            {/* Region List */}
            <h3 style={{ marginTop: 20 }}>By County</h3>
            <div style={{ display: "grid", gridTemplateColumns: "repeat(auto-fill, minmax(200px, 1fr))", gap: "10px" }}>
                {stats.regionStats && Object.entries(stats.regionStats).map(([regionName, regionData]) => (
                    <div key={regionName} style={{
                        border: "1px solid #ddd",
                        padding: "10px",
                        borderRadius: "8px",
                        backgroundColor: regionData.played > 0 ? "#f0fdf4" : "#fff"
                    }}>
                        <strong>{regionName}</strong>
                        <div style={{ fontSize: "0.9em", color: "#555" }}>
                            {regionData.played} / {regionData.total}
                        </div>
                        {/* Progress Bar */}
                        <div style={{ width: "100%", height: "4px", background: "#eee", marginTop: "5px" }}>
                            <div style={{
                                width: `${regionData.percentage}%`,
                                height: "100%",
                                background: "#4CAF50"
                            }}></div>
                        </div>
                    </div>
                ))}
            </div>
        </div>
    );
}

export default OverviewView;