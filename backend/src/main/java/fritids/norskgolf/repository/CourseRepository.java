package fritids.norskgolf.repository;

import fritids.norskgolf.entities.Course;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface CourseRepository extends JpaRepository<Course, Long> {
    Optional<Course> findByExternalId(String externalId);
    List<Course> findByName(String name);

    // Add other query methods as needed
}
