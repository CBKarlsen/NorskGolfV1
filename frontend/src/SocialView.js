// javascript
import React, { useState, useEffect } from "react";
import "./SocialView.css";

function SocialView() {
    const [activeTab, setActiveTab] = useState("friends");
    const [friends, setFriends] = useState([]);
    const [requests, setRequests] = useState([]);
    const [searchQuery, setSearchQuery] = useState("");
    const [searchResults, setSearchResults] = useState([]);
    const [loading, setLoading] = useState(false);

    // --- HELPER: Read CSRF Cookie ---
    function getCookie(name) {
        const match = document.cookie.match(new RegExp('(^|; )' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[2]) : null;
    }

    useEffect(() => {
        fetchFriends();
        fetchRequests();
    }, []);

    // Note: Added credentials: 'include' to GET requests ensuring the user session is recognized
    const fetchFriends = () => fetch('/api/friends', { credentials: 'include' }).then(res => res.json()).then(setFriends);
    const fetchRequests = () => fetch('/api/friends/requests', { credentials: 'include' }).then(res => res.json()).then(setRequests);

    const handleSearch = (e) => {
        e.preventDefault();
        if (!searchQuery.trim()) return;
        setLoading(true);
        fetch(`/api/friends/search?query=${searchQuery}`, { credentials: 'include' })
            .then(res => res.json())
            .then(data => { setSearchResults(data); setLoading(false); });
    };

    // --- UPDATED POST REQUESTS ---
    const sendRequest = async (userId) => {
        const csrf = getCookie('XSRF-TOKEN');
        const res = await fetch(`/api/friends/request/${userId}`, {
            method: 'POST',
            credentials: 'include', // 1. Allow Session Cookie
            headers: {
                ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}) // 2. Send CSRF Token
            }
        });
        if (res.ok) setSearchResults(prev => prev.map(u => u.id === userId ? { ...u, status: "SENT" } : u));
    };

    const respondToRequest = async (friendshipId, action) => {
        const csrf = getCookie('XSRF-TOKEN');
        const res = await fetch(`/api/friends/respond/${friendshipId}?action=${action}`, {
            method: 'POST',
            credentials: 'include',
            headers: {
                ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {})
            }
        });
        if (res.ok) { fetchRequests(); if (action === "ACCEPT") fetchFriends(); }
    };

    // Helper for Rank Icons
    const getRankIcon = (index) => {
        if (index === 0) return <span className="rank gold">1.</span>;
        if (index === 1) return <span className="rank silver">2.</span>;
        if (index === 2) return <span className="rank bronze">3.</span>;
        return <span className="rank">#{index + 1}</span>;
    };

    return (
        <div className="social-container">

            {/* --- TOGGLE TABS --- */}
            <div className="tabs">
                <div className={`tab ${activeTab === "friends" ? "active" : ""}`} onClick={() => setActiveTab("friends")}>
                    Toppliste ({friends.length})
                </div>
                <div className={`tab ${activeTab === "search" ? "active" : ""}`} onClick={() => setActiveTab("search")}>
                    Finn golfere
                </div>
            </div>

            {/* --- TAB: LEADERBOARD --- */}
            {activeTab === "friends" && (
                <div>
                    {/* PENDING REQUESTS */}
                    {requests.length > 0 && (
                        <div className="pending-box">
                            <h4 style={{ margin: "0 0 10px 0", color: "#F57F17" }}>🔔 Nye venneforespørsler</h4>
                            {requests.map(req => (
                                <div key={req.id} style={{ display: "flex", justifyContent: "space-between", alignItems: "center", marginTop: "10px" }}>
                                    <div style={{display:"flex", alignItems:"center", gap:"10px"}}>
                                        <img
                                            src={req.avatar || `https://ui-avatars.com/api/?name=${req.displayName}&background=random`}
                                            alt="profilbilde" className="avatar" style={{width:"32px", height:"32px"}}
                                        />
                                        <span><strong>{req.displayName}</strong></span>
                                    </div>
                                    <div>
                                        <button onClick={() => respondToRequest(req.friendshipId, "ACCEPT")} className="btn btn-accept">Godta</button>
                                        <button onClick={() => respondToRequest(req.friendshipId, "REJECT")} className="btn btn-reject">Avslå</button>
                                    </div>
                                </div>
                            ))}
                        </div>
                    )}

                    {/* LEADERBOARD LIST */}
                    <div className="section-header">🏆 Toppliste</div>

                    {friends.length === 0 ? (
                        <div className="empty-state">
                            <p>Ingen venner ennå. Gå til fanen "Finn golfere" for å legge til venner!</p>
                        </div>
                    ) : (
                        <div className="leaderboard-list">
                            {friends.map((friend, index) => {
                                const isMe = friend.status === "ME";
                                return (
                                    <div key={friend.id} className={`player-card ${isMe ? "is-me" : ""}`}>

                                        {/* 1. Rank */}
                                        <div style={{ width: "50px", textAlign: "center" }}>
                                            {getRankIcon(index)}
                                        </div>

                                        {/* 2. Avatar & Name */}
                                        <div className="player-info">
                                            <img
                                                src={friend.avatar || `https://ui-avatars.com/api/?name=${friend.displayName}&background=random`}
                                                alt="profilbilde"
                                                className="avatar"
                                            />
                                            <div>
                                                <div className="player-name">
                                                    {friend.displayName} {isMe && "(Du)"}
                                                </div>
                                            </div>
                                        </div>

                                        {/* 3. Stats */}
                                        <div className="stats">
                                            <div className="stat-item">
                                                <span className="stat-val">{friend.totalCourses}</span>
                                                <span className="stat-label">Baner</span>
                                            </div>
                                            <div className="stat-item">
                                                <span className="stat-val">{friend.totalRounds}</span>
                                                <span className="stat-label">Runder</span>
                                            </div>
                                        </div>
                                    </div>
                                );
                            })}
                        </div>
                    )}
                </div>
            )}

            {/* --- TAB: SEARCH --- */}
            {activeTab === "search" && (
                <div>
                    <form onSubmit={handleSearch} className="search-bar">
                        <input
                            type="text"
                            className="search-input"
                            placeholder="Skriv inn e-post eller brukernavn..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                        />
                        <button type="submit" className="btn btn-primary">Søk</button>
                    </form>

                    {loading && <p style={{textAlign:"center", color:"#888"}}>Søker...</p>}

                    <div className="leaderboard-list">
                        {searchResults.map(user => (
                            <div key={user.id} className="player-card">
                                <div className="player-info">
                                    <img
                                        src={user.avatar || `https://ui-avatars.com/api/?name=${user.displayName}&background=random`}
                                        alt="profilbilde"
                                        className="avatar"
                                    />
                                    <div className="player-name">{user.displayName}</div>
                                </div>

                                <div>
                                    {user.status === "NONE" && (
                                        <button onClick={() => sendRequest(user.id)} className="btn btn-primary">Legg til venn</button>
                                    )}
                                    {user.status === "SENT" && <span style={{ color: "#888", fontWeight:"600" }}>Forespørsel sendt ⏳</span>}
                                    {user.status === "RECEIVED" && <span style={{ color: "#d32f2f", fontWeight:"600" }}>Sjekk forespørsler!</span>}
                                    {user.status === "FRIENDS" && <span style={{ color: "#2e7d32", fontWeight:"600" }}>Venner ✅</span>}
                                    {user.status === "ME" && <span style={{ color: "#888", fontStyle:"italic" }}>Det er deg</span>}
                                </div>
                            </div>
                        ))}
                    </div>

                    {searchResults.length === 0 && searchQuery && !loading && (
                        <div className="empty-state">Ingen golfere funnet. Prøv å søke med e-postadresse.</div>
                    )}
                </div>
            )}
        </div>
    );
}

export default SocialView;
