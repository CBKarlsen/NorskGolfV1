package fritids.norskgolf.services;

import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.UserRepository;
import org.springframework.stereotype.Service;
import java.util.Optional;

@Service
public class LeaderboardService {

    private final UserRepository userRepository;

    public LeaderboardService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    // THIS IS THE METHOD "addUserToLeaderboard"
    public String resolveDisplayName(String inputEmail) {
        // 1. Ask the database: "Do we know this person?"
        // Note: You must add findByEmail() to your Repository interface first!
        Optional<User> user = userRepository.findByEmail(inputEmail);

        if (user.isPresent()) {
            User existingUser = user.get();
            // 2. Logic: Prefer Username -> Real Name -> Email
            if (existingUser.getUsername() != null && !existingUser.getUsername().isEmpty()) {
                return existingUser.getUsername();
            } else if (existingUser.getFullName() != null) {
                return existingUser.getFullName();
            }
        }

        // 3. If user is not found, return the email so the frontend can display it
        return inputEmail;
    }
}