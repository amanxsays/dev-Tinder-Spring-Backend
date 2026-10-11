package integration_service.service.streaming;

import integration_service.service.memory.MemoryEngineService;
import integration_service.service.routing.AgenticOrchestratorService;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisWorkerServiceTests {

    @Test
    @SuppressWarnings("unchecked")
    void contextStartsWorkerAndStopsBlockedPollingOnClose() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> queue = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(queue);
        CountDownLatch polling = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(queue.leftPop("ai-job-queue", Duration.ofSeconds(60))).thenAnswer(invocation -> {
            polling.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Polling interrupted during shutdown", e);
            }
            return null;
        });

        RedisWorkerService worker = new RedisWorkerService(redis,
                mock(AgenticOrchestratorService.class), mock(MemoryEngineService.class));
        assertFalse(worker.isRunning());
        verifyNoInteractions(redis);

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean(RedisWorkerService.class, () -> worker);
            context.refresh();
            assertTrue(polling.await(5, TimeUnit.SECONDS));
            assertTrue(worker.isRunning());
        }

        assertFalse(worker.isRunning());
        assertEquals(0, interrupted.getCount());
        verify(queue, times(1)).leftPop("ai-job-queue", Duration.ofSeconds(60));
        worker.stop(); // Repeated destruction is safe.
    }
}
