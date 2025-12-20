import React from "react";

function OverviewView({ stats, friends = [] }) {
    return (
        <div style={{ padding: 16, overflowY: "auto", height: "100%" }}>
            <h2>Overview</h2>

            <div>
                <strong>Rounds played:</strong> {stats?.roundsCount ?? "1000"}
            </div>
            <div>
                <strong>Average score:</strong> {stats?.averageScore ?? "—"}
            </div>
            <div>
                <strong>Last round:</strong>{" "}
                {stats?.lastRoundDate ? new Date(stats.lastRoundDate).toLocaleDateString() : "—"}
            </div>

            <h3 style={{ marginTop: 16 }}>Friends comparison</h3>
            <ul>
                {friends.length === 0 && <li>No friends data</li>}
                {friends.map((f) => (
                    <li key={f.playerId}>
                        {f.name} — Rounds: {f.roundsCount ?? "—"} — Avg: {f.averageScore ?? "—"}
                    </li>
                ))}
            </ul>
        </div>
    );
}

export default OverviewView;