package fritids.norskgolf.repository;

import fritids.norskgolf.entities.PlayedCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface PlayedCourseRepository extends JpaRepository<PlayedCourse, Long> {

    boolean existsByUserIdAndCourseId(Long userId, Long courseId);
    List<PlayedCourse> findByUserId(Long userId);

    long countByUserId(Long userId);

    Optional<PlayedCourse> findByUserIdAndCourseId(Long userId, Long courseId);

    Optional<PlayedCourse> findByUserIdAndCourse_ExternalId(Long userId, String externalId);

    @Query("""
        select pc from PlayedCourse pc
        join fetch pc.course c
        where pc.user.id = :userId
    """)
    List<PlayedCourse> findByUserIdWithCourse(Long userId);
}