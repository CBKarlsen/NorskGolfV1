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

    @Column(unique = true)
    private String externalId; // Unique identifier from a map API

    // Link to PlayedCourse join entity
    @OneToMany(mappedBy = "course", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<PlayedCourse> playedBy = new HashSet<>();

    // --- Getters & Setters ---
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public Double getLatitude() { return latitude; }
    public void setLatitude(Double latitude) { this.latitude = latitude; }

    public Double getLongitude() { return longitude; }
    public void setLongitude(Double longitude) { this.longitude = longitude; }

    public String getExternalId() { return externalId; }
    public void setExternalId(String externalId) { this.externalId = externalId; }

    public Set<PlayedCourse> getPlayedBy() { return playedBy; }
    public void setPlayedBy(Set<PlayedCourse> playedBy) { this.playedBy = playedBy; }

    // --- Equals & HashCode (based on externalId if present, else fallback) ---
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Course)) return false;
        Course course = (Course) o;
        return externalId != null
                ? externalId.equals(course.externalId)
                : name.equals(course.name)
                && (latitude != null ? latitude.equals(course.latitude) : course.latitude == null)
                && (longitude != null ? longitude.equals(course.longitude) : course.longitude == null);
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