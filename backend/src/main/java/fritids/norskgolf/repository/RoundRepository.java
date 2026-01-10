package fritids.norskgolf.repository;

import fritids.norskgolf.entities.Round;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RoundRepository extends JpaRepository<Round, Long> {
    List<Round> findByUserIdOrderByDateDescIdDesc(Long userId);
    List<Round> findByUserIdAndCourseIdOrderByScoreAsc(Long userId, Long courseId);
    boolean existsByUserIdAndCourseId(Long userId, Long courseId);
    int countByUserId(Long userId);
}
