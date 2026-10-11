package integration_service.service.gemini;

import integration_service.service.retrieval.AnswerGenerationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicIntegerArray;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiClientTests {
    private MockRestServiceServer server;
    private GeminiClient client;
    private GeminiKeyPool pool;
    private final MutableClock clock = new MutableClock();

    @BeforeEach
    void setup() {
        var builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        pool = new GeminiKeyPool("key1,key2,key3,key4,key5", "legacy", clock);
        client = new GeminiClient(builder.build(), pool, "gemini-3.6-flash");
    }

    @Test
    void rotatesSuccessfulCallsThroughAllFiveKeys() {
        for (String key : List.of("key1", "key2", "key3", "key4", "key5", "key1")) {
            success(key, "generateContent");
        }
        for (int i = 0; i < 6; i++) assertEquals("ok", client.generateContent(Map.of("fixture", true)).get("result"));
        server.verify();
    }

    @Test
    void quotaFailureTriesAnotherKeyAndSkipsItForLaterCalls() {
        quota("key1", "{\"error\":{\"details\":[{\"retryDelay\":\"120s\"}]}}");
        success("key2", "generateContent");
        success("key3", "generateContent");
        client.generateContent(Map.of());
        client.generateContent(Map.of());
        assertEquals(-1, pool.select("gemini-3.6-flash", Set.of(1, 2, 3, 4)));
        clock.advance(121);
        assertEquals(0, pool.select("gemini-3.6-flash", Set.of(1, 2, 3, 4)));
        server.verify();
    }

    @Test
    void exhaustedKeysAreEachTriedOnlyOnceAndNoMoreRequestsAreSentDuringCooldown() {
        for (int i = 1; i <= 5; i++) quota("key" + i, "{}");
        assertThrows(AnswerGenerationException.class, () -> client.generateContent(Map.of()));
        assertThrows(AnswerGenerationException.class, () -> client.generateContent(Map.of()));
        server.verify();
    }

    @Test
    void generationCooldownDoesNotBlockEmbeddingsOnThatKey() {
        var single = new GeminiKeyPool("key1", "", clock);
        var builder = RestClient.builder().baseUrl("https://generativelanguage.googleapis.com");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GeminiClient(builder.build(), single, "gemini-3.6-flash");
        quota("key1", "{}");
        success("key1", "embedContent");
        assertThrows(AnswerGenerationException.class, () -> client.generateContent(Map.of()));
        assertEquals("ok", client.embedContent(Map.of()).get("result"));
        server.verify();
    }

    @Test
    void authenticationFailureSkipsThatKeyAcrossModelsUntilRestart() {
        server.expect(anything()).andExpect(header("x-goog-api-key", "key1"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED));
        success("key2", "generateContent");
        client.generateContent(Map.of());
        assertEquals(-1, pool.select("gemini-embedding-001", Set.of(1, 2, 3, 4)));
        server.verify();
    }

    @Test
    void otherFailuresDoNotRepeatRequestsAcrossKeys() {
        server.expect(anything()).andExpect(header("x-goog-api-key", "key1"))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        assertThrows(RestClientResponseException.class, () -> client.generateContent(Map.of()));
        server.verify();
    }

    @Test
    void legacyKeyAndDuplicateKeyConfigurationRemainSupported() {
        var legacy = new GeminiKeyPool(" , ", "old-key", clock);
        assertEquals("old-key", legacy.key(legacy.select("model", Set.of())));
        var deduplicated = new GeminiKeyPool("key1,key1,key2,, ", "old-key", clock);
        assertEquals(0, deduplicated.select("model", Set.of()));
        assertEquals(1, deduplicated.select("model", Set.of()));
        assertEquals(-1, deduplicated.select("model", Set.of(0, 1)));
        assertThrows(IllegalArgumentException.class, () -> new GeminiKeyPool("", "", clock));
    }

    @Test
    void dailyQuotaStaysBlockedUntilPacificMidnightEvenWithoutRetryInfo() {
        var single = new GeminiKeyPool("key1", "", clock);
        var error = new RestClientResponseException("Fixture quota failure", 429, "Too Many Requests", null,
                "{\"error\":{\"details\":[{\"violations\":[{\"quotaId\":\"GenerateRequestsPerDayPerProjectPerModel-FreeTier\"}]}]}}"
                        .getBytes(java.nio.charset.StandardCharsets.UTF_8), java.nio.charset.StandardCharsets.UTF_8);
        single.coolDown(0, "model", error);
        clock.advance(120);
        assertEquals(-1, single.select("model", Set.of()));
        clock.now = Instant.parse("2026-10-12T07:00:01Z");
        assertEquals(0, single.select("model", Set.of()));
    }

    @Test
    void retryAfterHeaderExtendsCooldownWithoutRetryInfo() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.add("Retry-After", "180");
        pool.coolDown(0, "model", new RestClientResponseException("Fixture quota", 429,
                "Too Many Requests", headers, "{}".getBytes(java.nio.charset.StandardCharsets.UTF_8),
                java.nio.charset.StandardCharsets.UTF_8));
        clock.advance(61);
        assertEquals(-1, pool.select("model", Set.of(1, 2, 3, 4)));
        clock.advance(120);
        assertEquals(0, pool.select("model", Set.of(1, 2, 3, 4)));
    }

    @Test
    void parallelSelectionsRemainBalancedAndThreadSafe() throws Exception {
        var counts = new AtomicIntegerArray(5);
        try (var executor = Executors.newFixedThreadPool(8)) {
            var futures = IntStream.range(0, 500).mapToObj(i -> executor.submit(() -> {
                counts.incrementAndGet(pool.select("model", Set.of()));
            })).toList();
            for (var future : futures) future.get(5, TimeUnit.SECONDS);
        }
        for (int i = 0; i < 5; i++) assertEquals(100, counts.get(i));
    }

    private void success(String key, String operation) {
        String model = operation.equals("embedContent") ? "gemini-embedding-001" : "gemini-3.6-flash";
        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":" + operation))
                .andExpect(header("x-goog-api-key", key))
                .andRespond(withSuccess("{\"result\":\"ok\"}", MediaType.APPLICATION_JSON));
    }

    private void quota(String key, String body) {
        server.expect(anything()).andExpect(header("x-goog-api-key", key))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).contentType(MediaType.APPLICATION_JSON).body(body));
    }

    private static class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-10-11T12:00:00Z");
        void advance(long seconds) { now = now.plusSeconds(seconds); }
        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return Clock.fixed(now, zone); }
        @Override public Instant instant() { return now; }
    }
}
