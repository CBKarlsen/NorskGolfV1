import React, { useState, useEffect } from "react";
// We don't import Login here anymore as a blocking component
import Login from "./Login";
import MapView from "./MapView";
import Overview from "./Overview";
import SocialView from "./SocialView";
import "./App.css";

// --- ROUTER IMPORTS ---
import { Routes, Route, useNavigate, useLocation, Navigate } from "react-router-dom";

// --- MUI IMPORTS ---
import Box from "@mui/material/Box";
import AppBar from "@mui/material/AppBar";
import Toolbar from "@mui/material/Toolbar";
import Typography from "@mui/material/Typography";
import Button from "@mui/material/Button";
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
import LoginIcon from '@mui/icons-material/Login'; // New icon

function App() {
    const [user, setUser] = useState(null);
    const [loading, setLoading] = useState(true);
    const [mapFocus, setMapFocus] = useState(null);

    // --- NAVIGATION HOOKS ---
    const navigate = useNavigate();
    const location = useLocation();

    // Menu State
    const [anchorEl, setAnchorEl] = useState(null);
    const openMenu = Boolean(anchorEl);

    useEffect(() => {
        // Try to fetch user, but don't block app if it fails (401)
        fetch("/api/auth/me")
            .then(res => {
                if (res.ok) return res.json();
                return null; // It's okay if we are not logged in
            })
            .then(data => {
                setUser(data);
                setLoading(false);
            })
            .catch(() => setLoading(false));
    }, []);

    const handleCourseClick = (lat, lng) => {
        setMapFocus({ lat, lng, zoom: 15 });
        navigate("/kart"); // Use URL navigation!
    };

    // --- MENU HANDLERS ---
    const handleMenuClick = (event) => setAnchorEl(event.currentTarget);
    const handleMenuClose = () => setAnchorEl(null);
    const handleLogout = () => window.location.href = "/logout";

    // --- LOADING STATE ---
    if (loading) {
        return (
            <Box sx={{ display: 'flex', justifyContent: 'center', alignItems: 'center', height: '100vh', bgcolor: '#f5f5f5' }}>
                <CircularProgress color="success" />
            </Box>
        );
    }

    return (
        <Box sx={{ display: 'flex', flexDirection: 'column', height: '100vh', overflow: 'hidden' }}>
            <CssBaseline />

            {/* 1. TOP APP BAR */}
            <AppBar position="static" elevation={4} sx={{ bgcolor: '#2E7D32' }}>
                <Toolbar>
                    <Typography
                        variant="h5"
                        component="div"
                        sx={{ flexGrow: 1, fontFamily: "'Playball', cursive", display: 'flex', alignItems: 'center', gap: 1, cursor: 'pointer' }}
                        onClick={() => navigate("/")} // Clicking logo goes home
                    >
                        <span style={{ fontSize: "1.8rem" }}>⛳</span> GolfJakten
                    </Typography>

                    <Box>
                        {user ? (
                            // LOGGED IN VIEW: Show Avatar
                            <>
                                <Box onClick={handleMenuClick} sx={{ cursor: 'pointer' }}>
                                    <Avatar
                                        src={user.photo}
                                        sx={{ border: '2px solid rgba(255,255,255,0.8)', width: 40, height: 40 }}
                                    />
                                </Box>
                                <Menu
                                    anchorEl={anchorEl}
                                    open={openMenu}
                                    onClose={handleMenuClose}
                                    slotProps={{ paper: { sx: { mt: 1.5 } } }}
                                >
                                    <MenuItem onClick={handleLogout} sx={{ color: '#d32f2f' }}>
                                        <LogoutIcon fontSize="small" sx={{ mr: 1 }} /> Logg ut
                                    </MenuItem>
                                </Menu>
                            </>
                        ) : (
                            // LOGGED OUT VIEW: Show Login Button
                            <Button
                                color="inherit"
                                startIcon={<LoginIcon />}
                                onClick={() => navigate("/login")}
                                sx={{ fontWeight: 'bold' }}
                            >
                                Logg inn
                            </Button>
                        )}
                    </Box>
                </Toolbar>
            </AppBar>

            {/* 2. MAIN CONTENT AREA (ROUTING) */}
            <Box
                component="main"
                sx={{
                    flexGrow: 1,
                    overflowY: location.pathname === "/kart" ? "hidden" : "auto",
                    bgcolor: location.pathname === "/kart" ? "#e0e0e0" : "#f5f5f5",
                    position: "relative"
                }}
            >
                <Routes>
                    {/* Default Route: Go to Map */}
                    <Route path="/" element={<Navigate to="/kart" replace />} />

                    {/* PUBLIC ROUTE: Map is open to everyone */}
                    <Route path="/kart" element={
                        <MapView
                            user={user} // Pass user (can be null!)
                            focus={mapFocus}
                            onFocusComplete={() => setMapFocus(null)}
                        />
                    } />

                    {/* PROTECTED ROUTES: Only for logged in users */}
                    <Route path="/oversikt" element={
                        <Overview user={user} onNavigate={handleCourseClick} />
                    } />

                    <Route path="/venner" element={
                        <SocialView user={user} />
                    } />

                    {/* LOGIN PAGE */}
                    <Route path="/login" element={<Login />} />
                </Routes>
            </Box>

            {/* 3. BOTTOM NAVIGATION */}
            {/* We hide the bottom bar if we are on the login page */}
            {location.pathname !== "/login" && (
                <Paper sx={{ position: 'fixed', bottom: 0, left: 0, right: 0, zIndex: 1000 }} elevation={6}>
                    <BottomNavigation
                        showLabels
                        value={location.pathname}
                        onChange={(event, newValue) => navigate(newValue)}
                        sx={{ "& .Mui-selected": { color: "#2E7D32" } }}
                    >
                        <BottomNavigationAction label="Kart" value="/kart" icon={<MapIcon />} />
                        <BottomNavigationAction label="Oversikt" value="/oversikt" icon={<BarChartIcon />} />
                        <BottomNavigationAction label="Venner" value="/venner" icon={<GroupIcon />} />
                    </BottomNavigation>
                </Paper>
            )}
        </Box>
    );
}

export default App;