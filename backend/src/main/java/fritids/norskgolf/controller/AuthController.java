package fritids.norskgolf.controller;

import fritids.norskgolf.dto.UserProfileDTO;
import jakarta.servlet.http.HttpServletRequest;
import fritids.norskgolf.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AuthController {

    @Autowired
    private UserService userService;

    @GetMapping("/api/auth/me")
    public ResponseEntity<UserProfileDTO> getMe(OAuth2AuthenticationToken token, HttpServletRequest request) {
        if (token == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();

        UserProfileDTO profile = userService.syncUser(token);
        // The SPA cannot read the CSRF token from a cookie any more — Firebase Hosting strips
        // every cookie except __session — so it comes back with the profile the app already
        // fetches on load, and is echoed as the X-XSRF-TOKEN header on mutating requests.
        CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        if (csrfToken != null) profile.setCsrfToken(csrfToken.getToken());

        return ResponseEntity.ok(profile);
    }
}
