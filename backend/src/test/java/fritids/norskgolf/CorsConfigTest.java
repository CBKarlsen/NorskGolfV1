package fritids.norskgolf;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.web.servlet.MockMvc;

import java.io.InputStream;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Regression: app.frontend.url shipped as "/" — not a valid origin — so the CORS allowlist matched
 * nothing and every browser POST returned 403 while GETs worked, because browsers send Origin on
 * writes but not on same-origin reads. curl never reproduced it (curl sends no Origin), so these
 * tests speak the browser's dialect instead.
 *
 * The property is set explicitly here: a developer machine has secrets.properties on the classpath
 * pointing at localhost, which would otherwise decide the outcome.
 */
@SpringBootTest(properties = "app.frontend.url=https://golfjakten.no,https://norskgolf.web.app")
@AutoConfigureMockMvc
class CorsConfigTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void preflightFromTheConfiguredFrontendOriginIsAllowed() throws Exception {
        mockMvc.perform(options("/api/rounds")
                        .header("Origin", "https://golfjakten.no")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://golfjakten.no"));
    }

    @Test
    void preflightFromTheSecondConfiguredOriginIsAlsoAllowed() throws Exception {
        mockMvc.perform(options("/api/rounds")
                        .header("Origin", "https://norskgolf.web.app")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://norskgolf.web.app"));
    }

    @Test
    void preflightFromSomewhereElseIsRejected() throws Exception {
        mockMvc.perform(options("/api/rounds")
                        .header("Origin", "https://evil.example.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    /** The shipped default must be an absolute origin, or the allowlist above matches nothing. */
    @Test
    void applicationPropertiesShipsAnAbsoluteFrontendUrl() throws Exception {
        Properties properties = new Properties();
        try (InputStream in = new ClassPathResource("application.properties").getInputStream()) {
            properties.load(in);
        }
        String value = properties.getProperty("app.frontend.url");

        assertNotNull(value, "app.frontend.url is missing from application.properties");
        for (String url : value.split(",")) {
            assertTrue(url.trim().startsWith("http://") || url.trim().startsWith("https://"),
                    "every app.frontend.url entry must be an absolute origin for CORS, was: " + url);
        }
    }
}
