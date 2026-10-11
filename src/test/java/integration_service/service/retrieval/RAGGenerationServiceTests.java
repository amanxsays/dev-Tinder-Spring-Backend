package integration_service.service.retrieval;

import integration_service.model.DocumentChunk;
import integration_service.service.gemini.GeminiClient;
import integration_service.service.gemini.GeminiKeyPool;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class RAGGenerationServiceTests {
    private MockRestServiceServer server;
    private RAGGenerationService service;

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        service = new RAGGenerationService(new GeminiClient(builder.build(),
                new GeminiKeyPool("fixture-key", "", Clock.systemUTC()), "gemini-3.6-flash"));
        ReflectionTestUtils.setField(service, "generationPromptResource", new ByteArrayResource(
                "Question: %s\nContext: %s".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void dailyQuotaIsAnErrorRatherThanAnAnswerWithResumeSources() {
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.6-flash:generateContent"))
                .andExpect(method(HttpMethod.POST)).andExpect(header("x-goog-api-key", "fixture-key"))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":{\"details\":[{\"violations\":[{\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}]}]}}"));
        var error = assertThrows(AnswerGenerationException.class, () -> service.generateAnswer("Query", chunks()));
        assertTrue(error.getMessage().contains("daily AI request limit"));
        assertFalse(error.getMessage().contains("fixture-key"));
        server.verify();
    }

    @Test
    void successfulResponseCombinesAllTextParts() {
        server.expect(anything()).andRespond(withSuccess(
                "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"First\"},{\"text\":\"Second\"}]}}]}",
                MediaType.APPLICATION_JSON));
        assertEquals("First\nSecond", service.generateAnswer("Query", chunks()));
        server.verify();
    }

    @Test
    void missingAnswerTextIsAnError() {
        server.expect(anything()).andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));
        assertThrows(AnswerGenerationException.class, () -> service.generateAnswer("Query", chunks()));
        server.verify();
    }

    private List<DocumentChunk> chunks() {
        var chunk = new DocumentChunk();
        chunk.setDocumentId("fixture-document");
        chunk.setFileName("Fixture.pdf");
        chunk.setText("Fixture resume text");
        return List.of(chunk);
    }
}
