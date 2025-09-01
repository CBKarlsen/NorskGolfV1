package fritids.norskgolf.repository;

import fritids.norskgolf.entities.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    Course findByName(String name);
    Optional<Course> findByExternalId(String externalId);

    // Add other query methods as needed
}
