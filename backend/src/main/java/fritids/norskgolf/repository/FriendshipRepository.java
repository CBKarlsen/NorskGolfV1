package fritids.norskgolf.repository;

import fritids.norskgolf.entities.Friendship;
import fritids.norskgolf.entities.FriendshipStatus;
import fritids.norskgolf.entities.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, Long> {

    // Check if a relationship already exists (to prevent duplicate requests)
    @Query("SELECT f FROM Friendship f WHERE " +
            "(f.requester = :u1 AND f.receiver = :u2) OR " +
            "(f.requester = :u2 AND f.receiver = :u1)")
    Optional<Friendship> findRelationship(User u1, User u2);

    // Find all ACCEPTED friendships for a user (Where user is either requester OR receiver)
    @Query("SELECT f FROM Friendship f WHERE " +
            "(f.requester.id = :userId OR f.receiver.id = :userId) AND f.status = 'ACCEPTED'")
    List<Friendship> findAllFriends(Long userId);

    // Find pending requests waiting for ME to accept
    List<Friendship> findByReceiverAndStatus(User receiver, FriendshipStatus status);
}