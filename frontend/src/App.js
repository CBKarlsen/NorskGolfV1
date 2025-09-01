import React, { useState } from "react";
import Login from "./Login";
import MapView from "./MapView";
import "./App.css";

function App() {
    const [user, setUser] = useState(null);

    if (!user) {
        return <Login onLogin={setUser} />;
    }

    return (
        <div className="App">
            <h1>Welcome, {user.username}!</h1>
            <MapView user={user} />
        </div>
    );
}

export default App;