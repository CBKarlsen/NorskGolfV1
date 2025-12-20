export const API_BASE = process.env.REACT_APP_API_URL || "";
export function apiUrl(path) {
    const base = API_BASE.replace(/\/+$/, "");
    return base + (path.startsWith("/") ? path : `/${path}`);
}