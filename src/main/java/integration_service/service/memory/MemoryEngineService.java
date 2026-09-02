package integration_service.service.memory;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import integration_service.model.UserMemory;
import integration_service.repository.UserMemoryRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class MemoryEngineService {

    private final UserMemoryRepository memoryRepository;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    @Value("${google.gemini.api-key}")
    private String apiKey;

    @Value("classpath:prompts/memory-extraction-prompt.txt")
    private Resource extractionPromptResource;

    public MemoryEngineService(UserMemoryRepository memoryRepository) {
        this.memoryRepository = memoryRepository;
        this.objectMapper = new ObjectMapper();
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
    }

    public void extractAndSaveMemories(String userId, String userQuery) {
        if (userId == null || userId.trim().isEmpty()) {
            return;
        }

        try {
            String template = extractionPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String fullPrompt = String.format(template, userQuery);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", fullPrompt)))),
                    "generationConfig", Map.of("temperature", 0.0)
            );

            Map response = restClient.post()
                    .uri("/v1beta/models/gemini-2.0-flash:generateContent?key=" + apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            String rawJson = extractTextFromResponse(response);

            List<String> newFacts = objectMapper.readValue(rawJson, new TypeReference<List<String>>() {});

            if (!newFacts.isEmpty()) {
                System.out.println("Extracted new memories for user " + userId + ": " + newFacts);
                saveFactsToMongo(userId, newFacts);
            }

        } catch (Exception e) {
            System.err.println("Failed to extract memories: " + e.getMessage());
        }
    }

    private void saveFactsToMongo(String userId, List<String> newFacts) {
        UserMemory memory = memoryRepository.findByUserId(userId)
                .orElse(new UserMemory(userId));

        List<String> existingFacts = memory.getPreferences();
        for (String fact : newFacts) {
            if (!existingFacts.contains(fact)) {
                existingFacts.add(fact);
            }
        }

        memory.setLastUpdatedAt(Instant.now());
        memoryRepository.save(memory);
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(Map response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.getFirst().get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String text = (String) parts.getFirst().get("text");

            return text.replace("```json", "").replace("```", "").trim();
        } catch (Exception e) {
            return "[]";
        }
    }
}