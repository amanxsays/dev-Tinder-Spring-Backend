package integration_service.service.retrieval;

import integration_service.model.DocumentChunk;
import integration_service.service.gemini.GeminiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class RAGGenerationService {

    private final GeminiClient gemini;

    @Value("classpath:prompts/generation-prompt.txt")
    private Resource generationPromptResource;

    public RAGGenerationService(GeminiClient gemini) {
        this.gemini = gemini;
    }

    public String generateAnswer(String userQuery, List<DocumentChunk> retrievedChunks) {
        if (retrievedChunks == null || retrievedChunks.isEmpty()) {
            return "I couldn't find any relevant candidate information for your query.";
        }

        try {
            String contextText = retrievedChunks.stream()
                    .map(chunk -> "Source [" + chunk.getFileName() + "] (resume:" + chunk.getDocumentId() + "):\n" + chunk.getText())
                    .collect(Collectors.joining("\n\n"));

            String template = generationPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String fullPrompt = String.format(template, userQuery, contextText);

            Map<String, Object> requestBody = Map.of(
                    "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", fullPrompt)))),
                    "generationConfig", Map.of("temperature", 0.3)
            );

            Map response = gemini.generateContent(requestBody);

            return extractTextFromResponse(response);

        } catch (AnswerGenerationException e) {
            throw e;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            System.err.println("Final generation failed: Gemini HTTP " + status);
            throw AnswerGenerationException.fromProviderResponse(e);
        } catch (Exception e) {
            System.err.println("Final generation failed: " + e.getClass().getSimpleName());
            throw new AnswerGenerationException(
                    "I couldn't generate an answer. Please try again.", e);
        }
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(Map response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.getFirst().get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            String text = parts.stream().map(part -> part.get("text"))
                    .filter(String.class::isInstance).map(String.class::cast)
                    .collect(Collectors.joining("\n")).trim();
            if (text.isEmpty()) throw new IllegalStateException("Gemini returned no answer text");
            return text;
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract final text from Gemini");
        }
    }
}
