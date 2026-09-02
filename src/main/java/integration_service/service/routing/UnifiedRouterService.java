package integration_service.service.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import integration_service.model.routing.ExecutionPlan;
import integration_service.model.routing.ExecutionTier;
import integration_service.model.routing.RetrievalMode;
import integration_service.model.routing.Strategy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class UnifiedRouterService {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${google.gemini.api-key}")
    private String apiKey;

    @Value("classpath:prompts/router-prompt.txt")
    private Resource routerPromptResource;

    public UnifiedRouterService() {
        this.objectMapper = new ObjectMapper();
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    public ExecutionPlan planExecution(String userQuery) {
        try {
            String systemInstructionTemplate = routerPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String fullPrompt = String.format(systemInstructionTemplate, userQuery);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(
                                            Map.of("text", fullPrompt)
                                    )
                            )
                    ),
                    "generationConfig", Map.of(
                            "responseMimeType", "application/json",
                            "temperature", 0.0
                    )
            );

            Map<String, Object> response = restClient.post()
                    .uri("/v1beta/models/gemini-2.5-flash:generateContent?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            String jsonText = extractResponseText(response);
            return objectMapper.readValue(jsonText, ExecutionPlan.class);

        } catch (Exception e) {
            System.err.println("Routing failed: " + e.getMessage());
            return new ExecutionPlan(
                    Strategy.SINGLE_SOURCE,
                    ExecutionTier.DIRECT,
                    RetrievalMode.RANKED_RETRIEVAL,
                    List.of(userQuery)
            );
        }
    }

    @SuppressWarnings("unchecked")
    private String extractResponseText(Map<String, Object> response) {
        if (response != null && response.containsKey("candidates")) {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (!candidates.isEmpty()) {
                Map<String, Object> candidate = candidates.getFirst();
                Map<String, Object> content = (Map<String, Object>) candidate.get("content");
                List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
                if (!parts.isEmpty()) {
                    return (String) parts.getFirst().get("text");
                }
            }
        }
        throw new RuntimeException("Empty response from Gemini Router API");
    }
}