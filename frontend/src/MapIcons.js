import L from "leaflet";

// Helper to create SVG strings
const createFlagSvg = (color, stroke) => `
  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" xmlns="http://www.w3.org/2000/svg" style="filter: drop-shadow(2px 4px 6px rgba(0,0,0,0.3));">
    <path d="M5 21V4" stroke="#555" stroke-width="2" stroke-linecap="round"/>
    <path d="M5 5H18.5C19.5 5 20 6 19 7L17.5 9L19 11C20 12 19.5 13 18.5 13H5" fill="${color}" stroke="${stroke}" stroke-width="1.5" stroke-linejoin="round"/>
    <circle cx="5" cy="21" r="2" fill="#555"/>
  </svg>
`;

// 1. Unplayed Icon (Red/Orange)
export const UnplayedIcon = L.divIcon({
    className: "custom-icon", // We will add a tiny bit of CSS to reset default styles
    html: createFlagSvg("#ff6b6b", "#c0392b"),
    iconSize: [40, 40],
    iconAnchor: [5, 38], // Anchor the bottom of the pole (x, y)
    popupAnchor: [10, -35]
});

// 2. Played Icon (Green)
export const PlayedIcon = L.divIcon({
    className: "custom-icon",
    html: createFlagSvg("#2E7D32", "#1b5e20"),
    iconSize: [45, 45], // Make played ones slightly larger?
    iconAnchor: [5, 43],
    popupAnchor: [10, -40]
});