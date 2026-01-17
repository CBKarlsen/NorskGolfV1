import React, { useState, useEffect } from "react";
import Skeleton from "@mui/material/Skeleton";
import Avatar from "@mui/material/Avatar";
import Box from "@mui/material/Box";
import Typography from "@mui/material/Typography";
import Paper from "@mui/material/Paper";
import Tabs from "@mui/material/Tabs";
import Tab from "@mui/material/Tab";
import Button from "@mui/material/Button";
import TextField from "@mui/material/TextField";
import Chip from "@mui/material/Chip";
import List from "@mui/material/List";
import ListItem from "@mui/material/ListItem";
import ListItemAvatar from "@mui/material/ListItemAvatar";
import InputAdornment from "@mui/material/InputAdornment";
import IconButton from "@mui/material/IconButton";

// Icons
import SearchIcon from '@mui/icons-material/Search';
import PersonAddIcon from '@mui/icons-material/PersonAdd';
import EmojiEventsIcon from '@mui/icons-material/EmojiEvents';
import NotificationsActiveIcon from '@mui/icons-material/NotificationsActive';
import CheckCircleIcon from '@mui/icons-material/CheckCircle';
import CancelIcon from '@mui/icons-material/Cancel';

function SocialView() {
    const [activeTab, setActiveTab] = useState(0);
    const [friends, setFriends] = useState([]);
    const [requests, setRequests] = useState([]);
    const [searchQuery, setSearchQuery] = useState("");
    const [searchResults, setSearchResults] = useState([]);

    // Loading States
    const [initialLoading, setInitialLoading] = useState(true);
    const [searchLoading, setSearchLoading] = useState(false);

    // --- HELPER: Read CSRF Cookie ---
    function getCookie(name) {
        const match = document.cookie.match(new RegExp('(^|; )' + name + '=([^;]*)'));
        return match ? decodeURIComponent(match[2]) : null;
    }

    useEffect(() => {

        // Fetch both on mount
        const loadData = async () => {
            setInitialLoading(true);
            try {
                const [friendsRes, requestsRes] = await Promise.all([
                    fetch('/api/friends', { credentials: 'include' }),
                    fetch('/api/friends/requests', { credentials: 'include' })
                ]);
                const friendsData = await friendsRes.json();
                const requestsData = await requestsRes.json();

                setFriends(friendsData);
                setRequests(requestsData);
            } catch (error) {
                console.error("Error loading social data", error);
            } finally {
                setInitialLoading(false);
            }
        };
        loadData();
    }, []);

    const handleSearch = (e) => {
        e.preventDefault();
        if (!searchQuery.trim()) return;
        setSearchLoading(true);
        fetch(`/api/friends/search?query=${searchQuery}`, { credentials: 'include' })
            .then(res => res.json())
            .then(data => { setSearchResults(data); setSearchLoading(false); });
    };

    const sendRequest = async (userId) => {
        const csrf = getCookie('XSRF-TOKEN');
        const res = await fetch(`/api/friends/request/${userId}`, {
            method: 'POST',
            credentials: 'include',
            headers: { ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}) }
        });
        if (res.ok) setSearchResults(prev => prev.map(u => u.id === userId ? { ...u, status: "SENT" } : u));
    };

    const respondToRequest = async (friendshipId, action) => {
        const csrf = getCookie('XSRF-TOKEN');
        const res = await fetch(`/api/friends/respond/${friendshipId}?action=${action}`, {
            method: 'POST',
            credentials: 'include',
            headers: { ...(csrf ? { 'X-XSRF-TOKEN': csrf } : {}) }
        });
        if (res.ok) {
            // Refresh data locally
            setRequests(prev => prev.filter(r => r.friendshipId !== friendshipId));
            if (action === "ACCEPT") {
                // Re-fetch friends to update leaderboard
                fetch('/api/friends', { credentials: 'include' }).then(res => res.json()).then(setFriends);
            }
        }
    };

    // Helper: Render Trophy for Top 3
    const getRankDisplay = (index) => {
        if (index === 0) return <EmojiEventsIcon sx={{ color: "#FFD700", fontSize: 32, filter: "drop-shadow(0 2px 4px rgba(0,0,0,0.2))" }} />; // Gold
        if (index === 1) return <EmojiEventsIcon sx={{ color: "#C0C0C0", fontSize: 28 }} />; // Silver
        if (index === 2) return <EmojiEventsIcon sx={{ color: "#CD7F32", fontSize: 26 }} />; // Bronze
        return <Typography variant="h6" sx={{ color: "#888", fontWeight: "bold", width: 30, textAlign: "center" }}>#{index + 1}</Typography>;
    };

    // Helper: Skeleton List
    const renderSkeletonList = () => (
        <Box sx={{ mt: 2 }}>
            {[1, 2, 3, 4].map((i) => (
                <Paper key={i} elevation={0} sx={{ p: 2, mb: 2, display: "flex", alignItems: "center", borderRadius: 3, border: "1px solid #f0f0f0" }}>
                    <Skeleton variant="circular" width={40} height={40} sx={{ mr: 2 }} />
                    <Box sx={{ flex: 1 }}>
                        <Skeleton variant="text" width="60%" height={24} />
                        <Skeleton variant="text" width="40%" height={16} />
                    </Box>
                    <Skeleton variant="rectangular" width={50} height={30} sx={{ borderRadius: 1 }} />
                </Paper>
            ))}
        </Box>
    );

    return (
        <div className="social-container">

            {/* --- MUI TABS --- */}
            <Box sx={{ borderBottom: 1, borderColor: 'divider', mb: 3 }}>
                <Tabs
                    value={activeTab}
                    onChange={(e, val) => setActiveTab(val)}
                    variant="fullWidth"
                    textColor="primary"
                    indicatorColor="primary"
                    sx={{
                        '& .MuiTab-root': { textTransform: 'none', fontWeight: 600, fontSize: '1rem' },
                        '& .Mui-selected': { color: '#2E7D32' },
                        '& .MuiTabs-indicator': { backgroundColor: '#2E7D32' }
                    }}
                >
                    <Tab label={`Toppliste (${friends.length})`} />
                    <Tab label="Finn golfere" icon={<SearchIcon fontSize="small" />} iconPosition="start" />
                </Tabs>
            </Box>

            {/* --- TAB 1: FRIENDS & LEADERBOARD --- */}
            {activeTab === 0 && (
                <Box>
                    {/* LOADING STATE */}
                    {initialLoading && renderSkeletonList()}

                    {!initialLoading && (
                        <>
                            {/* PENDING REQUESTS ALERT */}
                            {requests.length > 0 && (
                                <Paper
                                    elevation={0}
                                    sx={{
                                        mb: 3, p: 2,
                                        background: '#fff3e0',
                                        border: '1px solid #ffe0b2',
                                        borderRadius: 3
                                    }}
                                >
                                    <Box sx={{ display: 'flex', alignItems: 'center', mb: 1, color: '#e65100', gap: 1 }}>
                                        <NotificationsActiveIcon fontSize="small" />
                                        <Typography variant="subtitle2" fontWeight="bold">
                                            Nye venneforespørsler
                                        </Typography>
                                    </Box>

                                    {requests.map(req => (
                                        <Box key={req.id} sx={{ display: "flex", justifyContent: "space-between", alignItems: "center", mt: 1, background: 'white', p: 1.5, borderRadius: 2 }}>
                                            <Box sx={{ display: 'flex', alignItems: 'center', gap: 1.5 }}>
                                                <Avatar src={req.avatar} sx={{ width: 32, height: 32 }} />
                                                <Typography variant="body2" fontWeight="600">{req.displayName}</Typography>
                                            </Box>
                                            <Box sx={{ display: 'flex', gap: 1 }}>
                                                <IconButton size="small" onClick={() => respondToRequest(req.friendshipId, "ACCEPT")} sx={{ color: 'green', bgcolor: '#e8f5e9' }}>
                                                    <CheckCircleIcon fontSize="small" />
                                                </IconButton>
                                                <IconButton size="small" onClick={() => respondToRequest(req.friendshipId, "REJECT")} sx={{ color: 'red', bgcolor: '#ffebee' }}>
                                                    <CancelIcon fontSize="small" />
                                                </IconButton>
                                            </Box>
                                        </Box>
                                    ))}
                                </Paper>
                            )}

                            {/* LEADERBOARD */}
                            {friends.length === 0 ? (
                                <Box sx={{ textAlign: 'center', mt: 5, color: '#888' }}>
                                    <PersonAddIcon sx={{ fontSize: 60, color: '#e0e0e0', mb: 2 }} />
                                    <Typography>Ingen venner ennå.</Typography>
                                    <Button onClick={() => setActiveTab(1)} sx={{ mt: 1, color: '#2E7D32' }}>Finn golfere</Button>
                                </Box>
                            ) : (
                                <List sx={{ width: '100%', padding: 0 }}>
                                    {friends.map((friend, index) => {
                                        const isMe = friend.status === "ME";
                                        return (
                                            <Paper
                                                key={friend.id}
                                                elevation={0}
                                                sx={{
                                                    mb: 1.5,
                                                    borderRadius: 3,
                                                    border: isMe ? '2px solid #4CAF50' : '1px solid #f0f0f0',
                                                    background: isMe ? '#f1f8e9' : 'white',
                                                    overflow: 'hidden',
                                                    transition: 'transform 0.2s',
                                                    '&:hover': { transform: 'translateY(-2px)', boxShadow: '0 4px 12px rgba(0,0,0,0.05)' }
                                                }}
                                            >
                                                <ListItem sx={{ py: 2 }}>
                                                    {/* RANK ICON */}
                                                    <Box sx={{ mr: 2, display: 'flex', justifyContent: 'center', width: 40 }}>
                                                        {getRankDisplay(index)}
                                                    </Box>

                                                    {/* AVATAR */}
                                                    <ListItemAvatar>
                                                        <Avatar
                                                            src={friend.avatar || `https://ui-avatars.com/api/?name=${friend.displayName}`}
                                                            sx={{ border: '2px solid white', boxShadow: '0 2px 4px rgba(0,0,0,0.1)' }}
                                                        />
                                                    </ListItemAvatar>

                                                    {/* NAME & ME TAG */}
                                                    <Box sx={{ flex: 1 }}>
                                                        <Typography variant="subtitle1" fontWeight="700" color="text.primary">
                                                            {friend.displayName}
                                                        </Typography>
                                                        {isMe && <Chip label="Deg" size="small" color="success" sx={{ height: 20, fontSize: '0.65rem' }} />}
                                                    </Box>

                                                    {/* STATS */}
                                                    <Box sx={{ textAlign: 'right', display: 'flex', gap: 3 }}>
                                                        <Box>
                                                            <Typography variant="h6" sx={{ lineHeight: 1, fontWeight: 700, color: '#333' }}>{friend.totalCourses}</Typography>
                                                            <Typography variant="caption" sx={{ color: '#999', fontSize: '0.7rem' }}>BANER</Typography>
                                                        </Box>
                                                        <Box>
                                                            <Typography variant="h6" sx={{ lineHeight: 1, fontWeight: 700, color: '#333' }}>{friend.totalRounds}</Typography>
                                                            <Typography variant="caption" sx={{ color: '#999', fontSize: '0.7rem' }}>RUNDER</Typography>
                                                        </Box>
                                                    </Box>
                                                </ListItem>
                                            </Paper>
                                        );
                                    })}
                                </List>
                            )}
                        </>
                    )}
                </Box>
            )}

            {/* --- TAB 2: SEARCH --- */}
            {activeTab === 1 && (
                <Box>
                    <form onSubmit={handleSearch}>
                        <TextField
                            fullWidth
                            placeholder="Søk etter e-post eller brukernavn..."
                            value={searchQuery}
                            onChange={(e) => setSearchQuery(e.target.value)}
                            variant="outlined"
                            sx={{
                                mb: 3,
                                '& .MuiOutlinedInput-root': { borderRadius: 3, background: 'white' },
                                '& .Mui-focused .MuiOutlinedInput-notchedOutline': { borderColor: '#2E7D32' }
                            }}
                            slotProps={{
                                input: {
                                    startAdornment: (
                                        <InputAdornment position="start">
                                            <SearchIcon color="action" />
                                        </InputAdornment>
                                    ),
                                    endAdornment: (
                                            <InputAdornment position="end">
                                                <Button
                                                    type="submit"
                                                    variant="contained"
                                                    disableElevation
                                                    sx={{ borderRadius: 2, bgcolor: '#2E7D32', textTransform: 'none' }}
                                                >
                                                    Søk
                                                </Button>
                                            </InputAdornment>
                                        )}}
                        }
                        />
                    </form>

                    {searchLoading && renderSkeletonList()}

                    <List>
                        {searchResults.map(user => (
                            <Paper
                                key={user.id}
                                elevation={0}
                                sx={{ mb: 1.5, borderRadius: 3, border: '1px solid #eee' }}
                            >
                                <ListItem>
                                    <ListItemAvatar>
                                        <Avatar src={user.avatar || `https://ui-avatars.com/api/?name=${user.displayName}`} />
                                    </ListItemAvatar>

                                    <Box sx={{ flex: 1 }}>
                                        <Typography variant="subtitle1" fontWeight="600">{user.displayName}</Typography>
                                        <Typography variant="body2" color="text.secondary" sx={{ fontSize: '0.85rem' }}>
                                            {user.email ? user.email : "Golfer"}
                                        </Typography>
                                    </Box>

                                    {/* STATUS ACTIONS */}
                                    <Box>
                                        {user.status === "NONE" && (
                                            <Button
                                                variant="outlined"
                                                size="small"
                                                startIcon={<PersonAddIcon />}
                                                onClick={() => sendRequest(user.id)}
                                                sx={{ color: '#2E7D32', borderColor: '#2E7D32', borderRadius: 2 }}
                                            >
                                                Legg til
                                            </Button>
                                        )}
                                        {user.status === "SENT" && <Chip label="Sendt" size="small" sx={{ bgcolor: '#fff3e0', color: '#e65100', fontWeight: 600 }} />}
                                        {user.status === "RECEIVED" && <Chip label="Venter" size="small" color="error" variant="outlined" />}
                                        {user.status === "FRIENDS" && <Chip icon={<CheckCircleIcon />} label="Venner" size="small" color="success" variant="outlined" />}
                                        {user.status === "ME" && <Typography variant="caption" sx={{ fontStyle: 'italic', color: '#999' }}>Deg</Typography>}
                                    </Box>
                                </ListItem>
                            </Paper>
                        ))}
                    </List>

                    {searchResults.length === 0 && searchQuery && !searchLoading && (
                        <Box sx={{ textAlign: 'center', mt: 5, color: '#999' }}>
                            <SearchIcon sx={{ fontSize: 40, mb: 1, opacity: 0.3 }} />
                            <Typography>Ingen golfere funnet.</Typography>
                        </Box>
                    )}
                </Box>
            )}
        </div>
    );
}

export default SocialView;