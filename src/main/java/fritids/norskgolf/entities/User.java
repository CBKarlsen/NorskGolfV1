package fritids.norskgolf.entities;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String password;

    @ManyToMany
    @JoinTable(
            name = "user_played_courses",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "course_id")
    )
    private Set<Course> playedCourses = new HashSet<>();

    // Constructors, getters, setters, etc.

    public void addPlayedCourse(Course course) {
        this.playedCourses.add(course);
    }

    public void removePlayedCourse(Course course) {
        this.playedCourses.remove(course);
    }

    public Set<Course> getPlayedCourses() {
        return playedCourses;
    }

    public void setPlayedCourses(Set<Course> playedCourses) {
        this.playedCourses = playedCourses;
    }

    // ... other getters and setters
}