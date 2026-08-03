package fritids.norskgolf.service;

import fritids.norskgolf.dto.FriendDto;
import fritids.norskgolf.entities.Friendship;
import fritids.norskgolf.entities.FriendshipStatus;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.FriendshipRepository;
import fritids.norskgolf.repository.PlayedCourseRepository;
import fritids.norskgolf.repository.RoundRepository;
import fritids.norskgolf.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FriendService {

    @Autowired private UserRepository userRepository;
    @Autowired private FriendshipRepository friendshipRepository;
    @Autowired private PlayedCourseRepository playedCourseRepository;
    @Autowired private RoundRepository roundRepository;

    private static final int MIN_QUERY_LENGTH = 3;
    private static final int MAX_SEARCH_RESULTS = 20;

    // --- 1. SEARCH ---
    public List<FriendDto> searchUsers(String query, User currentUser) {
        String q = query == null ? "" : query.trim();
        // The React client enforces this too, but that is not a control.
        if (q.length() < MIN_QUERY_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Search must be at least " + MIN_QUERY_LENGTH + " characters");
        }

        return userRepository.searchUsers(q, currentUser.getId(), PageRequest.of(0, MAX_SEARCH_RESULTS)).stream()
                .map(u -> {
                    Optional<Friendship> relationship = friendshipRepository.findRelationship(currentUser, u);
                    String status = relationship.map(f -> statusOf(f, currentUser)).orElse("NONE");
                    boolean isFriend = "FRIENDS".equals(status);

                    // PRIVACY: Friends -> Full Name + avatar. Strangers -> First Name, no avatar.
                    String displayName = isFriend ? resolveDisplayName(u) : u.getFirstName();
                    if (displayName == null) displayName = "Golfer";

                    // ponytail: email only if they already typed (part of) it, as before - a name
                    // search must not hand out strangers' addresses.
                    boolean typedEmail = u.getEmail() != null && u.getEmail().toLowerCase().contains(q.toLowerCase());

                    return new FriendDto(
                            u.getPublicId(),
                            displayName,
                            isFriend || typedEmail ? u.getEmail() : null,
                            status,
                            // The client needs this to cancel a request it sent (DELETE /api/friends/{id}).
                            relationship.map(Friendship::getId).orElse(null),
                            0, 0,
                            isFriend ? u.getAvatar() : null
                    );
                })
                .collect(Collectors.toList());
    }

    // --- 2. SEND REQUEST ---
    public void sendRequest(User sender, String receiverPublicId) {
        // Find user by UUID instead of Database ID
        User receiver = userRepository.findByPublicId(receiverPublicId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (sender.getId().equals(receiver.getId())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot add yourself");
        }

        if (friendshipRepository.findRelationship(sender, receiver).isPresent()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Relationship already exists");
        }

        friendshipRepository.save(new Friendship(sender, receiver));
    }

    // --- 3. GET REQUESTS ---
    public List<FriendDto> getPendingRequests(User currentUser) {
        return friendshipRepository.findByReceiverAndStatus(currentUser, FriendshipStatus.PENDING).stream()
                .map(f -> new FriendDto(
                        f.getRequester().getPublicId(),
                        resolveDisplayName(f.getRequester()),
                        "PENDING_ACTION",
                        f.getId()
                ))
                .collect(Collectors.toList());
    }

    // --- 4. RESPOND ---
    public void respondToRequest(Long friendshipId, String action, User currentUser) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Security Check: Is the current user actually the receiver?
        if (!friendship.getReceiver().getId().equals(currentUser.getId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        if ("ACCEPT".equalsIgnoreCase(action)) {
            friendship.setStatus(FriendshipStatus.ACCEPTED);
            friendshipRepository.save(friendship);
        } else if ("REJECT".equalsIgnoreCase(action)) {
            friendshipRepository.delete(friendship);
        } else {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action");
        }
    }

    // --- 5. REMOVE (unfriend / cancel or decline a pending request) ---
    public void removeFriendship(Long friendshipId, User currentUser) {
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        // Either party may remove it, in any status.
        boolean isParty = friendship.getRequester().getId().equals(currentUser.getId())
                || friendship.getReceiver().getId().equals(currentUser.getId());
        if (!isParty) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }

        friendshipRepository.delete(friendship);
    }

    // --- 6. LEADERBOARD ---
    public List<FriendDto> getLeaderboard(User me) {
        List<FriendDto> leaderboard = friendshipRepository.findAllFriends(me.getId()).stream()
                .map(f -> {
                    User friend = f.getRequester().getId().equals(me.getId()) ? f.getReceiver() : f.getRequester();
                    return mapToDto(friend, "ACCEPTED", f.getId());
                })
                .collect(Collectors.toList());

        // Add Me
        leaderboard.add(mapToDto(me, "ME", null));

        // Sort
        leaderboard.sort(Comparator.comparingInt(FriendDto::getTotalCourses).reversed());
        return leaderboard;
    }

    // --- HELPERS ---

    private FriendDto mapToDto(User user, String status, Long friendshipId) {
        return new FriendDto(
                user.getPublicId(),
                resolveDisplayName(user),
                user.getEmail(),
                status,
                friendshipId,
                (int) playedCourseRepository.countByUserId(user.getId()),
                (int) roundRepository.countByUserId(user.getId()),
                user.getAvatar()
        );
    }

    private String resolveDisplayName(User user) {
        if (user.getUsername() != null && !user.getUsername().isEmpty() && !user.getUsername().contains("@")) {
            return user.getUsername();
        }
        if (user.getFullName() != null && !user.getFullName().isEmpty()) {
            return user.getFullName();
        }
        return user.getEmail();
    }

    private String statusOf(Friendship friendship, User me) {
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) return "FRIENDS";
        if (friendship.getRequester().getId().equals(me.getId())) return "SENT";
        return "RECEIVED";
    }
}