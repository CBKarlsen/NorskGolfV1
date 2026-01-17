import React, { useState, useEffect } from "react";
import Login from "./Login";
import MapView from "./MapView";
import Overview from "./Overview";
import SocialView from "./SocialView";
import "./App.css";

// --- MUI IMPORTS ---
import Box from "@mui/material/Box";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import Avatar from "@mui/material/Avatar";
import Menu from "@mui/material/Menu";
import MenuItem from "@mui/material/MenuItem";
import BottomNavigation from "@mui/material/BottomNavigation";
import BottomNavigationAction from "@mui/material/BottomNavigationAction";
import Paper from "@mui/material/Paper";
import CircularProgress from "@mui/material/CircularProgress";
import CssBaseline from "@mui/material/CssBaseline";

// --- ICONS ---
import MapIcon from '@mui/icons-material/Map';
import BarChartIcon from '@mui/icons-material/BarChart';
import GroupIcon from '@mui/icons-material/Group';
import LogoutIcon from '@mui/icons-material/Logout';

function App() {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [view, setView] = useState("map");
    const [mapFocus, setMapFocus] = useState(null);

    // Menu State
    const [anchorEl, setAnchorEl] = useState(null);
    const openMenu = Boolean(anchorEl);

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


    // --- MENU HANDLERS ---
    const handleMenuClick = (event) => {
        setAnchorEl(event.currentTarget);
    };
    const handleMenuClose = () => {
        setAnchorEl(null);
    };
    const handleLogout = () => {
        window.location.href = "/logout";
    };

    // --- LOADING STATE ---
    if (loading) {
        return (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', bgcolor: '#f5f5f5' }}>
                <CircularProgress color="success" />
            </Box>
        );
    }

    if (!user) return <Login onLogin={setUser} />;

    return (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
            <CssBaseline />

            {/* 1. TOP APP BAR */}
            <AppBar position="static" elevation={4} sx={{ bgcolor: '#2E7D32' }}>
                <Toolbar>
                    {/* LOGO */}
                    <Typography
                        variant="h5"
                        component="div"
                        sx={{ flexGrow: 1, fontFamily: "'Playball', cursive", display: 'flex', alignItems: 'center', gap: 1 }}
                    >
                        <span style={{ fontSize: "1.8rem" }}>⛳</span> GolfJakten
                    </Typography>

                    {/* PROFILE SECTION */}
                    <Box>
                        {/* Avatar Trigger */}
                        <Box
                            onClick={handleMenuClick}
                            sx={{ display: 'flex', alignItems: 'center', cursor: 'pointer' }}
                        >
                            <Avatar
                                alt="Profil"
                                src={user?.photo}
                                sx={{
                                    border: '2px solid rgba(255,255,255,0.8)',
                                    width: 40,
                                    height: 40,
                                    transition: 'transform 0.2s',
                                    '&:hover': { transform: 'scale(1.1)' }
                                }}
                            />
                        </Box>

                        {/* Dropdown Menu */}
                        <Menu
                            anchorEl={anchorEl}
                            open={openMenu}
                            onClose={handleMenuClose}

                            slotProps={{
                                paper: {
                                    elevation: 3,
                                    sx: { mt: 1.5, minWidth: 150, borderRadius: 2 }
                                }
                            }}

                            transformOrigin={{ horizontal: 'right', vertical: 'top' }}
                            anchorOrigin={{ horizontal: 'right', vertical: 'bottom' }}
                        >
                            <MenuItem onClick={handleLogout} sx={{ color: '#d32f2f' }}>
                                <LogoutIcon fontSize="small" sx={{ mr: 1 }} />
                                Logg ut
                            </MenuItem>
                        </Menu>
                    </Box>
                </Toolbar>
            </AppBar>

            {/* 2. MAIN CONTENT AREA */}
            <Box
                component="main"
                sx={{
                    flexGrow: 1,
                    overflowY: view === "map" ? "hidden" : "auto",
                    bgcolor: view === "map" ? "#e0e0e0" : "#f5f5f5",
                    position: "relative"
                }}
            >
                {view === "map" && (
                    <MapView
                        user={user}
                        focus={mapFocus}
                        onFocusComplete={() => setMapFocus(null)}
                    />
                )}
                {view === "overview" && (
                    <Overview user={user} onNavigate={handleCourseClick} />
                )}
                {view === "social" && <SocialView user={user} />}
            </Box>

            {/* 3. BOTTOM NAVIGATION */}
            <Paper sx={{ position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 1000 }} elevation={6}>
                <BottomNavigation
                    showLabels
                    value={view}
                    onChange={(event, newValue) => {
                        setView(newValue);
                    }}
                    sx={{
                        "& .Mui-selected": {
                            color: "#2E7D32"
                        }
                    }}
                >
                    <BottomNavigationAction label="Kart" value="map" icon={<MapIcon />} />
                    <BottomNavigationAction label="Oversikt" value="overview" icon={<BarChartIcon />} />
                    <BottomNavigationAction label="Venner" value="social" icon={<GroupIcon />} />
                </BottomNavigation>
            </Paper>
        </Box>
    );
}

export default App;