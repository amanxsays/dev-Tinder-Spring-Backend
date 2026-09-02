package integration_service.service.vector;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class EmbeddingService {

    private final RestClient restClient;

    @Value("${google.gemini.api-key}")
    private String apiKey;

    public EmbeddingService() {
        this.restClient = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();
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

        Map<String, Object> response = restClient.post()
                .uri("/v1beta/models/gemini-embedding-001:embedContent?key=" + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response != null && response.containsKey("embedding")) {
            Map<String, Object> embeddingMap = (Map<String, Object>) response.get("embedding");
            List<Double> values = (List<Double>) embeddingMap.get("values");

            return values.stream().map(Double::floatValue).toList();
        }

        throw new RuntimeException("Failed to generate embedding from Gemini API");
    }
}