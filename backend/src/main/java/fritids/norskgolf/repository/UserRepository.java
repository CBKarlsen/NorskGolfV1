package fritids.norskgolf.repository;

import fritids.norskgolf.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;


import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.playedCourses WHERE u.id = :userId")
    Optional<User> findByIdWithPlayedCourses(@Param("userId") Long userId);
    Optional<User> findByProviderId(String providerId);
}
