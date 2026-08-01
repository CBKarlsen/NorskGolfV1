// Reads the XSRF-TOKEN cookie Spring Security sets.
// Must be sent back as the X-XSRF-TOKEN header on every mutating request.
export function getCookie(name) {
	const match = document.cookie.match(new RegExp(`(^|; )${name}=([^;]*)`));
	return match ? decodeURIComponent(match[2]) : null;
}
