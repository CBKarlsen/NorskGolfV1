package fritids.norskgolf.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class CsrfController {

    /**
     * The SPA normally picks the token out of /api/auth/me, but that requires a login — which
     * makes the CSRF round-trip impossible to exercise with curl. This exposes the same token
     * to anyone holding the session it belongs to, which is no weaker: a token is only useful
     * to whoever already has the matching session cookie.
     */
    @GetMapping("/api/csrf")
    public Map<String, String> csrf(HttpServletRequest request) {
        CsrfToken token = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
        return token == null ? Map.of() : Map.of("token", token.getToken(), "header", token.getHeaderName());
    }
}
