package integration_service.config;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AiQueueNamesTests {
    @Test void defaultsPreserveExistingProductionQueues() {
        var names = new AiQueueNames("");
        assertEquals("ai-job-queue", names.queue());
        assertEquals("ai-chat-updates", names.channel());
    }
    @Test void developmentUsesSeparateQueueAndUpdateChannel() {
        var names = new AiQueueNames("devtinder-local");
        assertEquals("devtinder-local:ai-job-queue", names.queue());
        assertEquals("devtinder-local:ai-chat-updates", names.channel());
        assertThrows(IllegalArgumentException.class, () -> new AiQueueNames("*"));
    }
}
