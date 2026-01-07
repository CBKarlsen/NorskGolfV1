package fritids.norskgolf.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.dto.UserProfileDTO;
import fritids.norskgolf.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;

import java.security.Principal;
import java.util.Optional;

@RestController
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/profile")
    public UserProfileDTO profile(OAuth2AuthenticationToken token) {
        return syncUser(token);
    }

    @GetMapping("/api/auth/me")
    public UserProfileDTO getMe(OAuth2AuthenticationToken token) {
        return syncUser(token);
    }


    @RequestMapping("/user")
    public Principal user(Principal user) {
        return user;
    }

    /**
     * HELPER METHOD:
     * Extracts Google data -> Saves to Database -> Returns DTO
     * This ensures your DB always has the latest Name/Photo/Email.
     */
    private UserProfileDTO syncUser(OAuth2AuthenticationToken token) {
        if (token == null) return null;

        // 1. Extract details from Google Token
        String email = token.getPrincipal().getAttribute("email");
        String fullName = token.getPrincipal().getAttribute("name");
        String firstName = token.getPrincipal().getAttribute("given_name"); // Google specific
        String lastName = token.getPrincipal().getAttribute("family_name"); // Google specific
        String photo = token.getPrincipal().getAttribute("picture");
        String providerId = token.getPrincipal().getAttribute("sub");

        // 2. Find existing user OR create a new empty one
        User user = userRepository.findByProviderId(providerId)
                .orElse(new User());

        // 3. Update/Set the fields (THIS WAS MISSING BEFORE)
        if (user.getId() == null) {
            // New User Setup
            user.setProviderId(providerId);
            user.setProvider("google");
            user.setUsername(email); // Default username = email
        }

        // Always update these (in case you changed your Google name/pic)
        user.setEmail(email);
        user.setFirstName(firstName);
        user.setLastName(lastName);
        user.setAvatar(photo);

        // 4. Save to Database
        userRepository.save(user);

        // 5. Build DTO for Frontend
        UserProfileDTO dto = new UserProfileDTO();
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername()); // Returns "casper.karlsen@gmail.com" or custom name
        dto.setName(user.getFullName());     // Returns "Casper Karlsen" (Your new helper method)
        dto.setEmail(email);
        dto.setPhoto(photo);

        return dto;
    }
}