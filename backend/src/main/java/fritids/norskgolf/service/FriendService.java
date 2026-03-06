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
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
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

    // --- 1. SEARCH ---
    public List<FriendDto> searchUsers(String query, User currentUser) {
        List<User> matches = new ArrayList<>();

        //  STRICT SEARCH: Only allow exact email matches
        if (query != null && query.contains("@")) {
            userRepository.findByEmail(query).ifPresent(matches::add);
        }

        return matches.stream()
                .filter(u -> !u.getId().equals(currentUser.getId())) // Remove self
                .map(u -> {
                    String status = getFriendshipStatus(currentUser, u);
                    boolean isFriend = "FRIENDS".equals(status);

                    // PRIVACY:
                    // Since they searched by EXACT Email, we know they probably know the person.
                    // But let's stick to the safe rule:
                    // Friends -> Full Name. Strangers -> First Name.
                    String displayName = isFriend ? resolveDisplayName(u) : u.getFirstName();
                    if (displayName == null) displayName = "Golfer";

                    // Avatar: Hide for strangers (optional, but consistent)
                    String displayAvatar = isFriend ? u.getAvatar() : null;

                    return new FriendDto(
                            null,
                            displayName,
                            u.getEmail(), // Safe to show because they just typed it in!
                            status,
                            null,
                            0, 0,
                            displayAvatar
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

    // --- 5. LEADERBOARD ---
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

    private String getFriendshipStatus(User me, User other) {
        Optional<Friendship> f = friendshipRepository.findRelationship(me, other);
        if (f.isEmpty()) return "NONE";
        Friendship friendship = f.get();
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) return "FRIENDS";
        if (friendship.getRequester().getId().equals(me.getId())) return "SENT";
        return "RECEIVED";
    }
}