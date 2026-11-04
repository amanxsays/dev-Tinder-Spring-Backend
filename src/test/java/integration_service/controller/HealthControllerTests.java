package integration_service.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class HealthControllerTests {
    @Test
    void browserCanReadThePublicReadinessResponseWithoutCredentials() throws Exception {
        MockMvcBuilders.standaloneSetup(new HealthController()).build()
                .perform(get("/api/ping").header("Origin", "https://frontend.example.test"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"))
                .andExpect(content().string("Integration Service is up and running!"));
    }
}
