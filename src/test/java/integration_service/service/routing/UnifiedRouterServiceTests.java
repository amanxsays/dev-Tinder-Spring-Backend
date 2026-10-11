package integration_service.service.routing;

import integration_service.service.retrieval.AnswerGenerationException;
import integration_service.service.gemini.GeminiClient;
import integration_service.service.gemini.GeminiKeyPool;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class UnifiedRouterServiceTests {
    @Test
    void quotaFailureStopsRoutingInsteadOfFallingBackToMoreModelCalls() {
        var builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var service = new UnifiedRouterService(new GeminiClient(builder.build(),
                new GeminiKeyPool("fixture-key", "", Clock.systemUTC()), "gemini-3.6-flash"));
        ReflectionTestUtils.setField(service, "routerPromptResource",
                new ByteArrayResource("Route %s".getBytes(StandardCharsets.UTF_8)));
        server.expect(anything()).andExpect(header("x-goog-api-key", "fixture-key"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"status\":\"RESOURCE_EXHAUSTED\"}}"));
        assertThrows(AnswerGenerationException.class, () -> service.planExecution("Fixture query"));
        server.verify();
    }
}
