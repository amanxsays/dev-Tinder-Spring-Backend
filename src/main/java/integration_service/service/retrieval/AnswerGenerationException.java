package integration_service.service.retrieval;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.web.client.RestClientResponseException;

/** Carries a safe message for the chat client, without provider credentials or response bodies. */
public class AnswerGenerationException extends RuntimeException {
    public AnswerGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    public static AnswerGenerationException fromProviderResponse(RestClientResponseException error) {
        String message = "AI chat is currently unavailable. Please try again later.";
        if (error.getStatusCode().value() == 429) {
            message = "The AI request limit has been reached. Please try again later.";
            try {
                var details = new ObjectMapper().readTree(error.getResponseBodyAsString()).path("error").path("details");
                boolean daily = false;
                for (var detail : details) {
                    for (var violation : detail.path("violations")) {
                        daily |= violation.path("quotaId").asText().contains("PerDay");
                    }
                }
                if (daily) message = "The daily AI request limit has been reached. Please try again after it resets.";
            } catch (Exception ignored) {
                // Provider response formats may change. Never send raw errors or keys to the client.
            }
        }
        return new AnswerGenerationException(message, error);
    }
}
