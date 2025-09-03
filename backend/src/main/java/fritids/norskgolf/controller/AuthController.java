// AuthController.java
package fritids.norskgolf.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import fritids.norskgolf.entities.User;
import fritids.norskgolf.dto.UserProfileDTO;
import fritids.norskgolf.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.security.Principal;


@RestController
public class AuthController {

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/profile")
    public UserProfileDTO profile(OAuth2AuthenticationToken token) {
        String email = token.getPrincipal().getAttribute("email");
        String name = token.getPrincipal().getAttribute("name");
        String photo = token.getPrincipal().getAttribute("picture");
        String providerId = token.getPrincipal().getAttribute("sub"); // Google user ID

        // Find or create user
        User user = userRepository.findByProviderId(providerId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setUsername(email);
                    newUser.setProvider("google");
                    newUser.setProviderId(providerId);
                    userRepository.save(newUser);
                    return newUser;
                });

        // Build DTO for frontend
        UserProfileDTO dto = new UserProfileDTO();
        dto.setName(name);
        dto.setEmail(email);
        dto.setPhoto(photo);
        return dto;
    }

    @RequestMapping("/")
    public String home() {
        return "welcome";
    }

    @RequestMapping("/user")
    public Principal user(Principal user) {
        return user;
    }

    @GetMapping("/api/auth/me")
    public UserProfileDTO getMe(OAuth2AuthenticationToken token) {
        String email = token.getPrincipal().getAttribute("email");
        String name = token.getPrincipal().getAttribute("name");
        String photo = token.getPrincipal().getAttribute("picture");
        String providerId = token.getPrincipal().getAttribute("sub");

        // Find or create user
        User user = userRepository.findByProviderId(providerId)
                .orElseGet(() -> {
                    User newUser = new User();
                    newUser.setUsername(email);
                    newUser.setProvider("google");
                    newUser.setProviderId(providerId);
                    userRepository.save(newUser);
                    return newUser;
                });

        UserProfileDTO dto = new UserProfileDTO();
        dto.setName(name);
        dto.setEmail(email);
        dto.setPhoto(photo);
        dto.setUserId(user.getId());
        dto.setUsername(user.getUsername());
        return dto;
    }
}