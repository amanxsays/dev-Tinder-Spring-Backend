package integration_service.service.routing;

import com.fasterxml.jackson.databind.ObjectMapper;
import integration_service.model.routing.ExecutionPlan;
import integration_service.model.routing.ExecutionTier;
import integration_service.model.routing.RetrievalMode;
import integration_service.model.routing.Strategy;
import integration_service.service.gemini.GeminiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;
import integration_service.service.retrieval.AnswerGenerationException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Service
public class UnifiedRouterService {

    private final GeminiClient gemini;
    private final ObjectMapper objectMapper;

    @Value("classpath:prompts/router-prompt.txt")
    private Resource routerPromptResource;

    public UnifiedRouterService(GeminiClient gemini) {
        this.objectMapper = new ObjectMapper();
        this.gemini = gemini;
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

            Map<String, Object> response = gemini.generateContent(requestBody);

            String jsonText = extractResponseText(response);
            return objectMapper.readValue(jsonText, ExecutionPlan.class);

        } catch (AnswerGenerationException e) {
            throw e;
        } catch (RestClientResponseException e) {
            System.err.println("Routing failed: Gemini HTTP " + e.getStatusCode().value());
            if (e.getStatusCode().value() == 429) throw AnswerGenerationException.fromProviderResponse(e);
            return fallback(userQuery);
        } catch (Exception e) {
            System.err.println("Routing failed: " + e.getClass().getSimpleName());
            return fallback(userQuery);
        }
    }

    private ExecutionPlan fallback(String userQuery) {
        return new ExecutionPlan(
                    Strategy.SINGLE_SOURCE,
                    ExecutionTier.DIRECT,
                    RetrievalMode.RANKED_RETRIEVAL,
                    List.of(userQuery)
            );
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
