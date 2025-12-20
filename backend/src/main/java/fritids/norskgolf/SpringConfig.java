package fritids.norskgolf;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.session.web.http.DefaultCookieSerializer;
import java.util.List;

@Configuration
public class SpringConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        return http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .authorizeHttpRequests(registry -> {
                    registry.requestMatchers("/", "/h2-console/**", "/login/**", "/oauth2/**").permitAll();
                    registry.anyRequest().authenticated();
                })
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/h2-console/**", "/api/**", "/login/**")
                )
                .headers(headers -> headers
                        .frameOptions().sameOrigin()
                )
                .oauth2Login(oauth2 -> oauth2
                        .defaultSuccessUrl("http://localhost:3000", true)
                )
                .formLogin()
                .and()
                .build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        // use allowed origin patterns when allowing credentials
        configuration.setAllowedOriginPatterns(List.of("http://localhost:3000"));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public DefaultCookieSerializer cookieSerializer() {
        DefaultCookieSerializer serializer = new DefaultCookieSerializer();
        serializer.setSameSite("Lax");
        serializer.setUseSecureCookie(false);
        return serializer;
    }

}