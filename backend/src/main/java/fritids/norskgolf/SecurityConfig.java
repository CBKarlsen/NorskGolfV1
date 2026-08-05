package fritids.norskgolf;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.DelegatingAuthenticationEntryPoint;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.util.matcher.AntPathRequestMatcher;
import org.springframework.security.web.util.matcher.RegexRequestMatcher;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Value("${app.frontend.url}")
    private String frontendUrl;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        // Use a request attribute handler to make the token available as an attribute
        CsrfTokenRequestAttributeHandler requestHandler = new CsrfTokenRequestAttributeHandler();
        requestHandler.setCsrfRequestAttributeName(null);

        return http
                .csrf(csrf -> csrf
                        // Not a cookie: Firebase Hosting strips every cookie except __session on
                        // the way to Cloud Run, so an XSRF-TOKEN cookie would never reach us and
                        // every mutating request would 403. The token rides in the session and is
                        // handed to the SPA in the /api/auth/me response instead.
                        .csrfTokenRepository(csrfTokenRepository())
                        .csrfTokenRequestHandler(requestHandler)
                )
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(registry -> {
                    registry.requestMatchers(
                            "/", "/index.html", "/static/**", "/*.ico", "/*.json", "/*.png",
                            "/js/**", "/css/**", "/login/**", "/oauth2/**", "/error", "/api/auth/me"
                    ).permitAll();
                    // SPA shell: SpaRedirectController forwards every extensionless single-segment
                    // path to index.html, so permit that same class of GETs instead of listing each
                    // React route. /api/** contains a slash, so it never matches here.
                    registry.requestMatchers(RegexRequestMatcher.regexMatcher(HttpMethod.GET, "/[^/.]*(\\?.*)?")).permitAll();
                    registry.requestMatchers(HttpMethod.GET, "/api/courses").permitAll();
                    registry.anyRequest().authenticated();
                })
                .headers(headers -> headers
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::sameOrigin)
                )
                .exceptionHandling(ex -> ex.authenticationEntryPoint(authenticationEntryPoint()))
                .oauth2Login(oauth2 -> oauth2
                        // Points at the React route, not a Spring-generated page: with a custom
                        // login page set, DefaultLoginPageGeneratingFilter stops intercepting
                        // /login and SpaRedirectController forwards it to index.html.
                        .loginPage("/login")
                        .defaultSuccessUrl("/", true)
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutRequestMatcher(new AntPathRequestMatcher("/logout"))
                        .logoutSuccessUrl(frontendUrl)
                        .invalidateHttpSession(true)
                        .deleteCookies("__session")
                        .permitAll()
                )
                // Materialises the CSRF token so it is stored in the session and can be
                // handed to the SPA by /api/auth/me.
                .addFilterAfter(new CsrfTokenMaterialisingFilter(), BasicAuthenticationFilter.class)
                .build();
    }

    /**
     * The header name is set explicitly and must stay X-XSRF-TOKEN: this repository defaults to
     * X-CSRF-TOKEN, while the SPA and the CORS allowlist both use X-XSRF-TOKEN. Leaving the
     * default silently 403s every mutating request. Covered by SecurityConfigTest.
     */
    static HttpSessionCsrfTokenRepository csrfTokenRepository() {
        HttpSessionCsrfTokenRepository repository = new HttpSessionCsrfTokenRepository();
        repository.setHeaderName("X-XSRF-TOKEN");
        return repository;
    }

    /**
     * Unauthenticated /api/** gets a bare 401 so fetch() can tell "logged out" from a real
     * failure; everything else (the SPA shell) is redirected to the React login page.
     */
    private static AuthenticationEntryPoint authenticationEntryPoint() {
        LinkedHashMap<RequestMatcher, AuthenticationEntryPoint> mappings = new LinkedHashMap<>();
        mappings.put(new AntPathRequestMatcher("/api/**"), new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED));
        DelegatingAuthenticationEntryPoint entryPoint = new DelegatingAuthenticationEntryPoint(mappings);
        entryPoint.setDefaultEntryPoint(new LoginUrlAuthenticationEntryPoint("/login"));
        return entryPoint;
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(frontendUrl));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        // Explicitly allowing headers helps with CORS preflight checks
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-XSRF-TOKEN"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }


    private static final class CsrfTokenMaterialisingFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
                throws ServletException, IOException {

            // Deferred by default; calling getToken() forces it into the session.
            CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
            if (csrfToken != null) {
                csrfToken.getToken();
            }
            filterChain.doFilter(request, response);
        }
    }
}
