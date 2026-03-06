package fritids.norskgolf.service;

import fritids.norskgolf.dto.UserProfileDTO;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    /**
     * Extracts Google data -> Saves to Database -> Returns DTO
     */
    public UserProfileDTO syncUser(OAuth2AuthenticationToken token) {
        if (token == null) return null;

        // 1. Extract details from Google Token
        String email = token.getPrincipal().getAttribute("email");
        String firstName = token.getPrincipal().getAttribute("given_name");
        String lastName = token.getPrincipal().getAttribute("family_name");
        String photo = token.getPrincipal().getAttribute("picture");
        String providerId = token.getPrincipal().getAttribute("sub");

        // 2. Find existing user OR create a new empty one
        User user = userRepository.findByProviderId(providerId)
                .orElse(new User());

        // 3. Update/Set the fields
        if (user.getId() == null) {
            // New User Setup
            user.setProviderId(providerId);
            user.setProvider("google");
            user.setUsername(email); // Default username

            // IMPORTANT: Ensure UUID is set if not generated automatically by DB
            if (user.getPublicId() == null) {
                user.setPublicId(UUID.randomUUID().toString());
            }
        }

        // Always update these (Syncs Google changes)
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setAvatar(photo);

        // 4. Save to Database
        userRepository.save(user);

        // 5. Build DTO
        UserProfileDTO dto = new UserProfileDTO();


        dto.setUserId(user.getPublicId());

        dto.setUsername(user.getUsername());
        dto.setName(user.getFullName());
        dto.setEmail(email);
        dto.setPhoto(photo);

        return dto;
    }

    public User resolveUser(java.security.Principal principal) {
        String name = principal.getName();
        return userRepository.findByUsername(name)
                .or(() -> userRepository.findByProviderId(name))
                .or(() -> userRepository.findByEmail(name))
                .orElse(null);
    }
}