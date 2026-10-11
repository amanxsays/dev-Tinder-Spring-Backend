package integration_service.service.gemini;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClientResponseException;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Shared by all Gemini operations. Locking covers selection only, never HTTP calls. */
public final class GeminiKeyPool {
    private final List<String> keys;
    private final Clock clock;
    private final Map<String, long[]> cooldowns = new HashMap<>();
    private final Set<Integer> rejectedKeys = new HashSet<>();
    private int next;

    public GeminiKeyPool(String configuredKeys, String legacyKey, Clock clock) {
        List<String> configured = parse(configuredKeys);
        this.keys = configured.isEmpty() ? parse(legacyKey) : configured;
        this.clock = clock;
        if (keys.isEmpty()) throw new IllegalArgumentException("Configure GEMINI_API_KEY_1 through GEMINI_API_KEY_5, or GEMINI_API_KEY");
    }

    private static List<String> parse(String value) {
        return Arrays.stream((value == null ? "" : value).split(","))
                .map(String::trim).filter(key -> !key.isEmpty()).distinct().toList();
    }

    public synchronized int select(String model, Set<Integer> attempted) {
        long[] blocked = cooldowns.get(model);
        for (int offset = 0; offset < keys.size(); offset++) {
            int index = (next + offset) % keys.size();
            if (!attempted.contains(index) && !rejectedKeys.contains(index)
                    && (blocked == null || blocked[index] <= clock.millis())) {
                next = (index + 1) % keys.size();
                return index;
            }
        }
        return -1;
    }

    public String key(int index) { return keys.get(index); }

    public synchronized void reject(int index) { rejectedKeys.add(index); }

    public synchronized void coolDown(int index, String model, RestClientResponseException error) {
        long until = retryAt(error);
        long[] blocked = cooldowns.computeIfAbsent(model, ignored -> new long[keys.size()]);
        blocked[index] = Math.max(blocked[index], until);
    }

    private long retryAt(RestClientResponseException error) {
        long delay = 60_000;
        boolean daily = false;
        try {
            JsonNode details = new ObjectMapper().readTree(error.getResponseBodyAsString()).path("error").path("details");
            for (JsonNode detail : details) {
                if (detail.has("retryDelay")) {
                    try {
                        delay = Math.max(1_000, Duration.parse("PT" + detail.get("retryDelay").asText().toUpperCase()).toMillis());
                    } catch (RuntimeException ignored) {
                        // A malformed delay must not hide valid daily-quota information.
                    }
                }
                for (JsonNode violation : detail.path("violations")) {
                    daily |= violation.path("quotaId").asText().contains("PerDay");
                }
            }
        } catch (Exception ignored) {
            // Unknown response formats receive a short cooldown rather than a busy retry loop.
        }
        if (error.getResponseHeaders() != null) {
            try {
                String retryAfter = error.getResponseHeaders().getFirst("Retry-After");
                if (retryAfter != null) delay = Math.max(delay, Long.parseLong(retryAfter) * 1_000);
            } catch (NumberFormatException ignored) {
                // Gemini normally supplies the structured RetryInfo delay or integer seconds.
            }
        }
        if (daily) {
            long reset = clock.instant().atZone(ZoneId.of("America/Los_Angeles"))
                    .toLocalDate().plusDays(1).atStartOfDay(ZoneId.of("America/Los_Angeles"))
                    .toInstant().toEpochMilli();
            return Math.max(reset, clock.millis() + delay);
        }
        return clock.millis() + delay;
    }
}
