package integration_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Keeps the job producer, worker, and SSE subscription in the same environment. */
@Component
public class AiQueueNames {
    private final String prefix;

    public AiQueueNames(@Value("${ai.redis.namespace:}") String namespace) {
        String value = namespace.trim();
        if (!value.matches("[a-zA-Z0-9_-]{0,64}")) throw new IllegalArgumentException("Invalid AI_REDIS_NAMESPACE");
        prefix = value.isEmpty() ? "" : value + ":";
    }

    public String queue() { return prefix + "ai-job-queue"; }
    public String channel() { return prefix + "ai-chat-updates"; }
}
