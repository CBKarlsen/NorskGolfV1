package fritids.norskgolf;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.csrf.CsrfToken;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SecurityConfigTest {

    /**
     * Regression: swapping CookieCsrfTokenRepository for HttpSessionCsrfTokenRepository silently
     * changed the expected header from X-XSRF-TOKEN to X-CSRF-TOKEN, so every mutating request
     * 403'd while login still worked. The SPA (api.js) and the CORS allowlist both send
     * X-XSRF-TOKEN.
     */
    @Test
    void csrfTokenUsesTheHeaderNameTheFrontendSends() {
        CsrfToken token = SecurityConfig.csrfTokenRepository().generateToken(new MockHttpServletRequest());

        assertEquals("X-XSRF-TOKEN", token.getHeaderName());
    }
}
