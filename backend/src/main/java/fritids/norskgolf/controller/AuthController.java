package fritids.norskgolf.controller;

import fritids.norskgolf.dto.UserProfileDTO;
import fritids.norskgolf.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
public class AuthController {

    @Autowired
    private UserService userService;

    @GetMapping("/profile")
    public UserProfileDTO profile(OAuth2AuthenticationToken token) {
        return userService.syncUser(token);
    }

    @GetMapping("/api/auth/me")
    public UserProfileDTO getMe(OAuth2AuthenticationToken token) {
        return userService.syncUser(token);
    }

    //might not need
    @GetMapping("/user")
    public Principal user(Principal user) {
        return user;
    }
}