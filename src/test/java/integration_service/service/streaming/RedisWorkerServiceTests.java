package integration_service.service.streaming;

import integration_service.service.memory.MemoryEngineService;
import integration_service.service.routing.AgenticOrchestratorService;
import integration_service.model.ChatAnswer;
import integration_service.config.AiQueueNames;
import integration_service.model.ResumeSource;
import integration_service.service.retrieval.AnswerGenerationException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.concurrent.CompletionException;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.List;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RedisWorkerServiceTests {
    private final AiQueueNames names = new AiQueueNames("test-local");

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    @SuppressWarnings("unchecked")
    void generationFailureSendsOnlyErrorAndSkipsMemoryCall(boolean asynchronous) throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> queue = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(queue);
        AtomicBoolean delivered = new AtomicBoolean();
        when(queue.leftPop(names.queue(), Duration.ofSeconds(5))).thenAnswer(invocation -> {
            if (!delivered.getAndSet(true)) return "job||user||Fixture query";
            try { new CountDownLatch(1).await(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            return null;
        });
        AgenticOrchestratorService orchestrator = mock(AgenticOrchestratorService.class);
        var failure = new AnswerGenerationException("Daily limit reached", new RuntimeException("provider detail"));
        when(orchestrator.processUserQuery("Fixture query", "user"))
                .thenThrow(asynchronous ? new CompletionException(failure) : failure);
        MemoryEngineService memory = mock(MemoryEngineService.class);
        CountDownLatch errorSent = new CountDownLatch(1);
        when(redis.convertAndSend(eq(names.channel()), anyString())).thenAnswer(invocation -> {
            if (((String) invocation.getArgument(1)).startsWith("job||error||")) errorSent.countDown();
            return 1L;
        });
        var worker = new RedisWorkerService(redis, orchestrator, memory, names);
        try { worker.start(); assertTrue(errorSent.await(5, TimeUnit.SECONDS)); }
        finally { worker.stop(); }
        verify(redis).convertAndSend(names.channel(), "job||error||Daily limit reached");
        verify(redis, never()).convertAndSend(eq(names.channel()), startsWith("job||message||"));
        verifyNoInteractions(memory);
    }

    @Test
    @SuppressWarnings("unchecked")
    void sendsResumeSourcesTogetherWithTheTerminalAnswerEvent() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> queue = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(queue);
        AtomicBoolean delivered = new AtomicBoolean();
        when(queue.leftPop(names.queue(), Duration.ofSeconds(5))).thenAnswer(invocation -> {
            if (!delivered.getAndSet(true)) return "job||user||Find developers";
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        });
        CountDownLatch answerSent = new CountDownLatch(1);
        when(redis.convertAndSend(eq(names.channel()), anyString())).thenAnswer(invocation -> {
            if (((String) invocation.getArgument(1)).startsWith("job||message||")) answerSent.countDown();
            return 1L;
        });
        AgenticOrchestratorService orchestrator = mock(AgenticOrchestratorService.class);
        when(orchestrator.processUserQuery("Find developers", "user"))
                .thenReturn(new ChatAnswer("See [Resume.pdf](resume:doc)",
                        List.of(new ResumeSource("doc", "candidate", "Resume.pdf", "https://example.test/resume.pdf"))));
        RedisWorkerService worker = new RedisWorkerService(redis, orchestrator, mock(MemoryEngineService.class), names);
        try {
            worker.start();
            assertTrue(answerSent.await(5, TimeUnit.SECONDS));
        } finally {
            worker.stop();
        }
        ArgumentCaptor<String> messages = ArgumentCaptor.forClass(String.class);
        verify(redis, times(2)).convertAndSend(eq(names.channel()), messages.capture());
        List<String> sent = messages.getAllValues();
        assertTrue(sent.get(1).startsWith("job||message||"));
        var answer = new ObjectMapper().readTree(sent.get(1).split("\\|\\|", 3)[2]);
        assertEquals("See [Resume.pdf](resume:doc)", answer.get("text").asText());
        var source = answer.get("sources").get(0);
        assertEquals("doc", source.get("documentId").asText());
        assertEquals("https://example.test/resume.pdf", source.get("fileUrl").asText());
    }

    @Test
    @SuppressWarnings("unchecked")
    void contextStartsWorkerAndStopsBlockedPollingOnClose() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ListOperations<String, String> queue = mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(queue);
        CountDownLatch polling = new CountDownLatch(1);
        CountDownLatch interrupted = new CountDownLatch(1);
        when(queue.leftPop(names.queue(), Duration.ofSeconds(5))).thenAnswer(invocation -> {
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
                mock(AgenticOrchestratorService.class), mock(MemoryEngineService.class), names);
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
        verify(queue, times(1)).leftPop(names.queue(), Duration.ofSeconds(5));
        worker.stop(); // Repeated destruction is safe.
    }
}
