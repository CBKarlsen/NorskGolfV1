package fritids.norskgolf.repository;

import fritids.norskgolf.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;


import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    @Query("SELECT u FROM User u LEFT JOIN FETCH u.playedCourses WHERE u.id = :userId")
    Optional<User> findByIdWithPlayedCourses(@Param("userId") Long userId);
    Optional<User> findByProviderId(String providerId);
    Optional<User> findByEmail(String email);
    Optional<User> findByPublicId(String publicId);

    // Friend search: partial, case-insensitive over the fields a user would actually type.
    // Excludes the caller; caller passes a Pageable to cap the result count.
    @Query("SELECT u FROM User u WHERE u.id <> :excludeUserId AND (" +
            "LOWER(u.firstName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.lastName) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.email) LIKE LOWER(CONCAT('%', :query, '%')) OR " +
            "LOWER(u.username) LIKE LOWER(CONCAT('%', :query, '%')))")
    List<User> searchUsers(@Param("query") String query, @Param("excludeUserId") Long excludeUserId, Pageable pageable);
}
