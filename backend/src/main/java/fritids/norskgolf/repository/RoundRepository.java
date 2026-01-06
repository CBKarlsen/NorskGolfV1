package fritids.norskgolf.repository;

import fritids.norskgolf.entities.Round;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoundRepository extends JpaRepository<Round, Long> {
    List<Round> findByUserIdOrderByDateDesc(Long userId);
    List<Round> findByUserIdAndCourseIdOrderByScoreAsc(Long userId, Long courseId);
    boolean existsByUserIdAndCourseId(Long userId, Long courseId);
}
