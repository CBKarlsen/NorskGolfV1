// Firebase Hosting strips every cookie except __session before forwarding to
// Cloud Run, so the CSRF token cannot travel as a readable cookie. The backend
// returns it with the profile from /api/auth/me; App.js stores it here, and
// every mutating request echoes it as the X-XSRF-TOKEN header.
let csrfToken = null;

export function setCsrfToken(token) {
	csrfToken = token ?? null;
}

export function csrfHeaders() {
	return csrfToken ? { "X-XSRF-TOKEN": csrfToken } : {};
}
