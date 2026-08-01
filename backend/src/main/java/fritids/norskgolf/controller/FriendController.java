package fritids.norskgolf.controller;

import fritids.norskgolf.dto.FriendDto;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.service.FriendService;
import fritids.norskgolf.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    @Autowired private FriendService friendService;
    @Autowired private UserService userService;

    @GetMapping("/search")
    public List<FriendDto> searchUsers(@RequestParam String query, Principal principal) {
        return friendService.searchUsers(query, resolveUser(principal));
    }

    // Notice we use String receiverId (UUID) now
    @PostMapping("/request/{receiverId}")
    public ResponseEntity<Void> sendRequest(@PathVariable String receiverId, Principal principal) {
        friendService.sendRequest(resolveUser(principal), receiverId);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/requests")
    public List<FriendDto> getPendingRequests(Principal principal) {
        return friendService.getPendingRequests(resolveUser(principal));
    }

    @PostMapping("/respond/{friendshipId}")
    public ResponseEntity<Void> respondToRequest(@PathVariable Long friendshipId, @RequestParam String action, Principal principal) {
        friendService.respondToRequest(friendshipId, action, resolveUser(principal));
        return ResponseEntity.ok().build();
    }

    // Unfriend, or cancel/decline a pending request. Works for either party.
    @DeleteMapping("/{friendshipId}")
    public ResponseEntity<Void> removeFriendship(@PathVariable Long friendshipId, Principal principal) {
        friendService.removeFriendship(friendshipId, resolveUser(principal));
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public List<FriendDto> getFriends(Principal principal) {
        return friendService.getLeaderboard(resolveUser(principal));
    }

    // Helper to get the actual User object from the login session
    private User resolveUser(Principal principal) {
        User user = userService.resolveUser(principal);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found");
        }
        return user;
    }
}