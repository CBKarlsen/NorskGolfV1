import logo from './logo.svg';
import './App.css';

import React, { useState } from "react";
import Login from "./Login";
import "./App.css";

function App() {
    const [user, setUser] = useState(null);

    if (!user) {
        return <Login onLogin={setUser} />;
    }

    return (
        <div className="App">
            <h1>Welcome, {user.username}!</h1>
            {/* Your main app content goes here */}
        </div>
    );
}

export default App;
