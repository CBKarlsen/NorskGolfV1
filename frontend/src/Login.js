import React, { useState } from "react";

function Login({ onLogin }) {
    const [username, setUsername] = useState("");
    const [password, setPassword] = useState("");
    const [error, setError] = useState("");

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError("");
        const response = await fetch("http://localhost:8080/login", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            credentials: "include",
            body: new URLSearchParams({ username, password }).toString(),
        });

        if (response.ok) {
            // If server redirects, follow it in the browser
            if (response.redirected) {
                window.location.href = response.url;
                return;
            }
            // otherwise try to parse user info
            try {
                const data = await response.json();
                onLogin(data);
            } catch {
                // fallback: reload to pick up session-authenticated state
                window.location.reload();
            }
        } else {
            setError("Invalid username or password");
        }
    };

    return (
        <form onSubmit={handleSubmit}>
            <h2>Login</h2>
            <input
                value={username}
                onChange={e => setUsername(e.target.value)}
                placeholder="Username"
                required
            />
            <input
                type="password"
                value={password}
                onChange={e => setPassword(e.target.value)}
                placeholder="Password"
                required
            />
            <button type="submit">Login</button>
            {error && <div style={{color: "red"}}>{error}</div>}
            <button
                type="button"
                onClick={() => window.location.href = "http://localhost:8080/oauth2/authorization/google"}
            >
                Login with Google
            </button>
        </form>
    );
}

export default Login;