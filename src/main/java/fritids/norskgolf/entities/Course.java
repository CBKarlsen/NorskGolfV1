package fritids.norskgolf.entities;

import jakarta.persistence.*;
import java.util.HashSet;
import java.util.Set;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private Double latitude;
    private Double longitude;
    private String externalId; // Unique identifier from a map API (recommended)

    @ManyToMany(mappedBy = "playedCourses")
    private Set<User> players = new HashSet<>(); // You can keep this for potential future use or remove if you are certain you won't need to navigate from Course to Users

    // Constructors, getters, setters, equals, and hashCode (important for collections)

    public Set<User> getPlayers() {
        return players;
    }

    public void setPlayers(Set<User> players) {
        this.players = players;
    }

    // ... other getters and setters

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Course course = (Course) o;
        return externalId != null ? externalId.equals(course.externalId) : (name.equals(course.name) && (latitude != null ? latitude.equals(course.latitude) : course.latitude == null) && (longitude != null ? longitude.equals(course.longitude) : course.longitude == null));
    }

    @Override
    public int hashCode() {
        int result = name.hashCode();
        result = 31 * result + (latitude != null ? latitude.hashCode() : 0);
        result = 31 * result + (longitude != null ? longitude.hashCode() : 0);
        result = 31 * result + (externalId != null ? externalId.hashCode() : 0);
        return result;
    }
}
