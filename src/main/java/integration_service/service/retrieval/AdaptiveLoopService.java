package integration_service.service.retrieval;

import integration_service.model.DocumentChunk;
import integration_service.service.gemini.GeminiClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientResponseException;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AdaptiveLoopService {

    private final HybridRetrievalService hybridRetrievalService;
    private final GeminiClient gemini;

    @Value("classpath:prompts/evaluator-prompt.txt")
    private Resource evaluatorPromptResource;

    @Value("classpath:prompts/rewriter-prompt.txt")
    private Resource rewriterPromptResource;

    private static final int MAX_ITERATIONS = 3;

    public AdaptiveLoopService(HybridRetrievalService hybridRetrievalService, GeminiClient gemini) {
        this.hybridRetrievalService = hybridRetrievalService;
        this.gemini = gemini;
    }

    public List<DocumentChunk> executeAdaptiveSearch(String originalQuery) {
        List<DocumentChunk> accumulatedContext = new ArrayList<>();
        String currentQuery = originalQuery;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            List<DocumentChunk> newChunks = hybridRetrievalService.retrieveCandidates(currentQuery);

            for (DocumentChunk chunk : newChunks) {
                if (accumulatedContext.stream().noneMatch(c -> c.getId().equals(chunk.getId()))) {
                    accumulatedContext.add(chunk);
                }
            }

            String contextString = accumulatedContext.stream()
                    .map(DocumentChunk::getText)
                    .collect(Collectors.joining("\n---\n"));

            boolean isSufficient = evaluateSufficiency(originalQuery, contextString);

            if (isSufficient) {
                System.out.println("Adaptive Loop: Context sufficient after " + (i + 1) + " iterations.");
                break;
            }

            if (i < MAX_ITERATIONS - 1) {
                currentQuery = rewriteQuery(originalQuery, contextString);
                System.out.println("Adaptive Loop: Context insufficient. Rewrote query to: " + currentQuery);
            }
        }

        return accumulatedContext;
    }

    private boolean evaluateSufficiency(String originalQuery, String contextText) {
        try {
            String template = evaluatorPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String fullPrompt = String.format(template, originalQuery, contextText);
            String responseText = callGemini(fullPrompt);
            return responseText.trim().equalsIgnoreCase("YES");
        } catch (AnswerGenerationException e) {
            throw e;
        } catch (RestClientResponseException e) {
            System.err.println("Evaluator failed: Gemini HTTP " + e.getStatusCode().value());
            if (e.getStatusCode().value() == 429) throw AnswerGenerationException.fromProviderResponse(e);
            return true;
        } catch (Exception e) {
            System.err.println("Evaluator failed: " + e.getClass().getSimpleName());
            return true;
        }
    }

    private String rewriteQuery(String originalQuery, String contextText) {
        try {
            String template = rewriterPromptResource.getContentAsString(StandardCharsets.UTF_8);
            String fullPrompt = String.format(template, originalQuery, contextText);
            return callGemini(fullPrompt).trim();
        } catch (AnswerGenerationException e) {
            throw e;
        } catch (RestClientResponseException e) {
            System.err.println("Rewriter failed: Gemini HTTP " + e.getStatusCode().value());
            if (e.getStatusCode().value() == 429) throw AnswerGenerationException.fromProviderResponse(e);
            return originalQuery;
        } catch (Exception e) {
            System.err.println("Rewriter failed: " + e.getClass().getSimpleName());
            return originalQuery;
        }
    }

    private String callGemini(String prompt) {
        Map<String, Object> requestBody = Map.of(
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", prompt)))),
                "generationConfig", Map.of("temperature", 0.0)
        );

        Map response = gemini.generateContent(requestBody);

        return extractTextFromResponse(response);
    }

    @SuppressWarnings("unchecked")
    private String extractTextFromResponse(Map response) {
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            Map<String, Object> content = (Map<String, Object>) candidates.getFirst().get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) content.get("parts");
            return (String) parts.getFirst().get("text");
        } catch (Exception e) {
            throw new RuntimeException("Failed to extract text from Gemini response");
        }
    }
}
