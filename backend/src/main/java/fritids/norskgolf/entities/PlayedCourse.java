package fritids.norskgolf.entities;

import jakarta.persistence.*;

@Entity
@Table(
        name = "played_course",
        uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "course_id"})
)
public class PlayedCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;

    protected PlayedCourse() {}

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
}