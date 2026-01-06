import React, { useState, useEffect } from "react";

function LogRoundModal({ isOpen, onClose, course, onSubmit }) {
    // Default to today's date
    const [date, setDate] = useState(new Date().toISOString().split('T')[0]);
    const [score, setScore] = useState("");
    const [isSubmitting, setIsSubmitting] = useState(false);

    // --- THE FIX: Reset state whenever the modal opens ---
    useEffect(() => {
        if (isOpen) {
            setDate(new Date().toISOString().split('T')[0]);
            setScore("");
            setIsSubmitting(false); // Reset the button
        }
    }, [isOpen]);

    if (!isOpen || !course) return null;

    const handleSubmit = async (e) => {
        e.preventDefault();
        setIsSubmitting(true);

        try {
            // We await the parent function so we catch errors here
            await onSubmit({
                courseExternalId: course.externalId,
                date,
                score: parseInt(score, 10)
            });
            // If success, parent closes modal.
        } catch (error) {
            console.error("Submission failed:", error);
            alert("Failed to save round. Please try again.");
            setIsSubmitting(false); // Re-enable button on error
        }
    };

    return (
        <div style={styles.overlay}>
            <div style={styles.modal}>
                <h3>Log Round at {course.name}</h3>

                <form onSubmit={handleSubmit} style={styles.form}>
                    <div style={styles.inputGroup}>
                        <label>Date Played:</label>
                        <input
                            type="date"
                            value={date}
                            onChange={(e) => setDate(e.target.value)}
                            required
                            style={styles.input}
                        />
                    </div>

                    <div style={styles.inputGroup}>
                        <label>Score (Strokes):</label>
                        <input
                            type="number"
                            value={score}
                            onChange={(e) => setScore(e.target.value)}
                            placeholder="e.g. 82"
                            required
                            min="1"
                            style={styles.input}
                        />
                    </div>

                    <div style={styles.actions}>
                        <button
                            type="button"
                            onClick={onClose}
                            style={styles.cancelBtn}
                            disabled={isSubmitting}
                        >
                            Cancel
                        </button>
                        <button
                            type="submit"
                            disabled={isSubmitting}
                            style={{
                                ...styles.submitBtn,
                                opacity: isSubmitting ? 0.7 : 1,
                                cursor: isSubmitting ? "not-allowed" : "pointer"
                            }}
                        >
                            {isSubmitting ? "Saving..." : "Save Round"}
                        </button>
                    </div>
                </form>
            </div>
        </div>
    );
}

const styles = {
    overlay: {
        position: "fixed", top: 0, left: 0, right: 0, bottom: 0,
        backgroundColor: "rgba(0,0,0,0.5)",
        display: "flex", alignItems: "center", justifyContent: "center",
        zIndex: 1000
    },
    modal: {
        backgroundColor: "white", padding: "20px", borderRadius: "8px",
        width: "300px", boxShadow: "0 2px 10px rgba(0,0,0,0.1)"
    },
    form: { display: "flex", flexDirection: "column", gap: "15px" },
    inputGroup: { display: "flex", flexDirection: "column", gap: "5px" },
    input: { padding: "8px", borderRadius: "4px", border: "1px solid #ccc" },
    actions: { display: "flex", justifyContent: "flex-end", gap: "10px", marginTop: "10px" },
    cancelBtn: { padding: "8px 12px", background: "#f5f5f5", border: "none", borderRadius: "4px", cursor: "pointer" },
    submitBtn: { padding: "8px 12px", background: "#4CAF50", color: "white", border: "none", borderRadius: "4px" }
};

export default LogRoundModal;