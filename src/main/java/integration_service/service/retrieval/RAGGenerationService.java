package integration_service.service.retrieval;

import integration_service.model.DocumentChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RAGGenerationService {

    private final RestClient restClient;

    @Value("${google.gemini.api-key}")
    private String apiKey;

    @Value("classpath:prompts/generation-prompt.txt")
    private Resource generationPromptResource;

    public RAGGenerationService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    public String generateAnswer(String userQuery, List<DocumentChunk> retrievedChunks) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return "I couldn't find any relevant candidate information for your query.";
        }

        try {
            String contextText = retrievedChunks.stream()
                    .map(chunk -> "Source [" + chunk.getFileName() + "]:\n" + chunk.getText())
                    .collect(Collectors.joining("\n\n"));

            String template = generationPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String fullPrompt = String.format(template, userQuery, contextText);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", fullPrompt)))),
                    "generationConfig", Map.of("temperature", 0.3)
            );

            Map response = restClient.post()
                    .uri("/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            return extractTextFromResponse(response);

        } catch (Exception e) {
            System.err.println("Final generation failed: " + e.getMessage());
            return "I encountered an error while trying to generate an answer. Please try again.";
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(Map response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.getFirst().get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.getFirst().get("text");
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract final text from Gemini");
        }
    }
}