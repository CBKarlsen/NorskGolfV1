package fritids.norskgolf.entities;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(
        name = "played_course",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "course_id"})
)
public class PlayedCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id; // simple surrogate primary key

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    @Column(name = "best_score")
    private Integer bestScore;   // null if not set

    @Column(name = "last_played")
    private LocalDate lastPlayed; // optional

    public PlayedCourse() {}

    public PlayedCourse(User user, Course course) {
        this.user = user;
        this.course = course;
    }

    // --- Getters & Setters ---
    public Long getId() { return id; }

    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }

    public Course getCourse() { return course; }
    public void setCourse(Course course) { this.course = course; }

    public Integer getBestScore() { return bestScore; }
    public void setBestScore(Integer bestScore) { this.bestScore = bestScore; }

    public LocalDate getLastPlayed() { return lastPlayed; }
    public void setLastPlayed(LocalDate lastPlayed) { this.lastPlayed = lastPlayed; }
}