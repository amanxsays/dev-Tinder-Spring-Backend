package integration_service.service.gemini;

import integration_service.service.retrieval.AnswerGenerationException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.util.HashSet;
import java.util.Map;
import java.util.function.LongConsumer;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class GeminiClient {
    private final RestClient restClient;
    private final GeminiKeyPool pool;
    private final String generationModel;
    private final LongConsumer pause;

    @Autowired
    public GeminiClient(@Value("${google.gemini.api-keys:}") String keys,
                        @Value("${google.gemini.api-key:}") String legacyKey,
                        @Value("${google.gemini.model:gemini-3.6-flash}") String model) {
        this(RestClient.builder().baseUrl("https://generativelanguage.googleapis.com").build(),
                new GeminiKeyPool(keys, legacyKey, Clock.systemUTC()), model);
    }

    public GeminiClient(RestClient restClient, GeminiKeyPool pool, String model) {
        this(restClient, pool, model, GeminiClient::sleep);
    }

    GeminiClient(RestClient restClient, GeminiKeyPool pool, String model, LongConsumer pause) {
        this.restClient = restClient;
        this.pool = pool;
        this.generationModel = model;
        this.pause = pause;
    }

    public Map<String, Object> generateContent(Map<String, Object> body) {
        return post(generationModel, "generateContent", body);
    }

    public Map<String, Object> embedContent(Map<String, Object> body) {
        return post("gemini-embedding-001", "embedContent", body);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> post(String model, String operation, Map<String, Object> body) {
        var attempted = new HashSet<Integer>();
        RestClientResponseException lastFailure = null;
        int index = -1;
        int unavailableRetries = 0;
        for (;;) {
            if (index < 0) index = pool.select(model, attempted);
            if (index < 0) {
                if (lastFailure != null) throw AnswerGenerationException.fromProviderResponse(lastFailure);
                throw new AnswerGenerationException(
                        "All configured AI keys are temporarily unavailable. Please try again later.", null);
            }
            attempted.add(index);
            try {
                return restClient.post().uri("/v1beta/models/" + model + ":" + operation)
                        .header("x-goog-api-key", pool.key(index))
                        .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(Map.class);
            } catch (RestClientResponseException error) {
                int status = error.getStatusCode().value();
                System.err.println("Gemini " + operation + " failed: HTTP " + status + ", model=" + model);
                if (status == 503 && unavailableRetries < 2) {
                    pause.accept((1_000L << unavailableRetries++) + ThreadLocalRandom.current().nextLong(200));
                    continue;
                }
                if (status == 429) pool.coolDown(index, model, error);
                else if (status == 401 || status == 403) pool.reject(index);
                else throw error; // Never repeat requests with ambiguous outcomes or invalid payloads.
                lastFailure = error;
                index = -1;
            }
        }
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new AnswerGenerationException("The AI request was interrupted. Please try again.", error);
        }
    }
}
