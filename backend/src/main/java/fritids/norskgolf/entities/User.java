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

    @Column
    private String password;

    @Column
    private String provider;

    @Column(unique = true)
    private String providerId;

    // Join table with extra attributes (bestScore, lastPlayed)
    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PlayedCourse> playedCourses = new HashSet<>();

    // --- Relationship helpers ---
    public void addPlayedCourse(Course course) {
        PlayedCourse pc = new PlayedCourse(this, course);
        this.playedCourses.add(pc);
        course.getPlayedBy().add(pc); // keep both sides in sync
    }

    public void removePlayedCourse(Course course) {
        this.playedCourses.removeIf(pc -> pc.getCourse().equals(course));
        course.getPlayedBy().removeIf(pc -> pc.getUser().equals(this));
    }

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public Set<PlayedCourse> getPlayedCourses() { return playedCourses; }
    public void setPlayedCourses(Set<PlayedCourse> playedCourses) { this.playedCourses = playedCourses; }
}