package fritids.norskgolf.controller;

import fritids.norskgolf.entities.Friendship;
import fritids.norskgolf.entities.FriendshipStatus;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.FriendshipRepository;
import fritids.norskgolf.repository.UserRepository;
import fritids.norskgolf.repository.PlayedCourseRepository;
import fritids.norskgolf.repository.RoundRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.Comparator;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    @Autowired private UserRepository userRepository;
    @Autowired private FriendshipRepository friendshipRepository;
    @Autowired private PlayedCourseRepository playedCourseRepository;
    @Autowired private RoundRepository roundRepository;

    // --- 1. SEARCH USERS (Updated to support Email Search) ---
    @GetMapping("/search")
    public List<FriendDto> searchUsers(@RequestParam String query, Principal principal) {
        User currentUser = resolveUser(principal);
        List<User> matches = new ArrayList<>();

        // A. If query looks like an email, try to find EXACT match first
        if (query.contains("@")) {
            userRepository.findByEmail(query).ifPresent(matches::add);
        }

        // B. If no email match, search by username (partial match)
        if (matches.isEmpty()) {
            matches.addAll(userRepository.findByUsernameContainingIgnoreCase(query));
        }

        return matches.stream()
                .filter(u -> !u.getId().equals(currentUser.getId())) // Don't show myself
                .map(u -> new FriendDto(
                        u.getId(),
                        resolveDisplayName(u), // <--- USE NAME RESOLUTION HERE
                        getFriendshipStatus(currentUser, u)
                ))
                .collect(Collectors.toList());
    }

    // --- 2. SEND FRIEND REQUEST (Unchanged) ---
    @PostMapping("/request/{receiverId}")
    public ResponseEntity<?> sendRequest(@PathVariable Long receiverId, Principal principal) {
        User sender = resolveUser(principal);
        User receiver = userRepository.findById(receiverId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        if (sender.getId().equals(receiver.getId())) return ResponseEntity.badRequest().body("Cannot add yourself");
        if (friendshipRepository.findRelationship(sender, receiver).isPresent()) return ResponseEntity.badRequest().body("Relationship already exists");

        friendshipRepository.save(new Friendship(sender, receiver));
        return ResponseEntity.ok().build();
    }

    // --- 3. GET PENDING REQUESTS (Updated Name) ---
    @GetMapping("/requests")
    public List<FriendDto> getPendingRequests(Principal principal) {
        User user = resolveUser(principal);

        return friendshipRepository.findByReceiverAndStatus(user, FriendshipStatus.PENDING).stream()
                .map(f -> new FriendDto(
                        f.getRequester().getId(),
                        resolveDisplayName(f.getRequester()), // <--- SHOW REAL NAME REQUESTING
                        "PENDING_ACTION",
                        f.getId()
                ))
                .collect(Collectors.toList());
    }

    // --- 4. RESPOND (Unchanged) ---
    @PostMapping("/respond/{friendshipId}")
    public ResponseEntity<?> respondToRequest(@PathVariable Long friendshipId, @RequestParam String action, Principal principal) {
        User user = resolveUser(principal);
        Friendship friendship = friendshipRepository.findById(friendshipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));

        if (!friendship.getReceiver().getId().equals(user.getId())) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();

        if ("ACCEPT".equalsIgnoreCase(action)) {
            friendship.setStatus(FriendshipStatus.ACCEPTED);
            friendshipRepository.save(friendship);
        } else if ("REJECT".equalsIgnoreCase(action)) {
            friendshipRepository.delete(friendship);
        } else return ResponseEntity.badRequest().body("Invalid action");

        return ResponseEntity.ok().build();
    }

    // --- 5. LIST ALL FRIENDS (The Leaderboard - Updated) ---
    @GetMapping
    public List<FriendDto> getFriends(Principal principal) {
        User me = resolveUser(principal);

        List<FriendDto> leaderboard = friendshipRepository.findAllFriends(me.getId()).stream()
                .map(f -> {
                    User friend = f.getRequester().getId().equals(me.getId()) ? f.getReceiver() : f.getRequester();

                    return new FriendDto(
                            friend.getId(),
                            resolveDisplayName(friend), // <--- KEY CHANGE: Show Real Name
                            "ACCEPTED",
                            f.getId(),
                            (int) playedCourseRepository.countByUserId(friend.getId()),
                            (int) roundRepository.countByUserId(friend.getId()),
                            friend.getAvatar()
                    );
                })
                .collect(Collectors.toList());

        // Add "Me" to the board
        int myCourses = (int) playedCourseRepository.countByUserId(me.getId());
        int myRounds = (int) roundRepository.countByUserId(me.getId());

        // Ensure "Me" also uses the nice name logic (or adds " (You)")
        String myName = resolveDisplayName(me) + " (You)";
        leaderboard.add(new FriendDto(me.getId(), myName, "ME", null, myCourses, myRounds, me.getAvatar()));

        // Sort: High score top
        leaderboard.sort(Comparator.comparingInt((FriendDto d) -> d.totalCourses).reversed());

        return leaderboard;
    }

    // --- HELPERS ---

    // NEW: The Logic to pick the best name available
    private String resolveDisplayName(User user) {
        // 1. If they have a custom username set, prefer that
        if (user.getUsername() != null && !user.getUsername().isEmpty() && !user.getUsername().contains("@")) {
            return user.getUsername();
        }
        // 2. Otherwise, use the Google Real Name (if you saved it in User entity)
        if (user.getFullName() != null && !user.getFullName().isEmpty()) {
            return user.getFullName();
        }
        // 3. Fallback: Return email (or split email to look like name)
        return user.getEmail();
    }

    private User resolveUser(Principal principal) {
        String name = principal.getName();
        // Try to find by username, then providerId, then EMAIL (if using OAuth email as principal)
        return userRepository.findByUsername(name)
                .or(() -> userRepository.findByProviderId(name))
                .or(() -> userRepository.findByEmail(name))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found"));
    }

    private String getFriendshipStatus(User me, User other) {
        Optional<Friendship> f = friendshipRepository.findRelationship(me, other);
        if (f.isEmpty()) return "NONE";
        Friendship friendship = f.get();
        if (friendship.getStatus() == FriendshipStatus.ACCEPTED) return "FRIENDS";
        if (friendship.getRequester().getId().equals(me.getId())) return "SENT";
        return "RECEIVED";
    }

    // --- DTO ---
    public static class FriendDto {
        public Long id;
        public String displayName; // Renamed from 'username' to be clear
        public String status;
        public Long friendshipId;
        public int totalCourses;
        public int totalRounds;
        public String avatar;

        public FriendDto(Long id, String displayName, String status) {
            this(id, displayName, status, null, 0, 0, null);
        }

        public FriendDto(Long id, String displayName, String status, Long friendshipId) {
            this(id, displayName, status, friendshipId, 0, 0, null);
        }

        public FriendDto(Long id, String displayName, String status, Long friendshipId, int totalCourses, int totalRounds, String avatar) {
            this.id = id;
            this.displayName = displayName; // Holds "Casper Karlsen" or "GolfPro"
            this.status = status;
            this.friendshipId = friendshipId;
            this.totalCourses = totalCourses;
            this.totalRounds = totalRounds;
            this.avatar = avatar;
        }
    }
}