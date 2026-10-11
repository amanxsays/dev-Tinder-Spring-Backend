package integration_service.service.vector;

import integration_service.service.gemini.GeminiClient;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private final GeminiClient gemini;

    public EmbeddingService(GeminiClient gemini) {
        this.gemini = gemini;
    }

    @SuppressWarnings("unchecked")
    public List<Float> generateEmbedding(String text) {
        Map<String, Object> body = Map.of(
                "model", "models/gemini-embedding-001",
                "content", Map.of(
                        "parts", List.of(Map.of("text", text))
                ),
                "outputDimensionality", 768
        );

        Map<String, Object> response = gemini.embedContent(body);

        if (response != null && response.containsKey("embedding")) {
            Map<String, Object> embeddingMap = (Map<String, Object>) response.get("embedding");
            List<Double> values = (List<Double>) embeddingMap.get("values");

            return values.stream().map(Double::floatValue).toList();
        }

        throw new RuntimeException("Failed to generate embedding from Gemini API");
    }
}
