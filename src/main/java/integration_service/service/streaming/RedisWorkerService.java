package integration_service.service.streaming;

import com.fasterxml.jackson.databind.ObjectMapper;
import integration_service.model.ChatAnswer;
import integration_service.config.AiQueueNames;
import integration_service.service.routing.AgenticOrchestratorService;
import integration_service.service.memory.MemoryEngineService;
import integration_service.service.retrieval.AnswerGenerationException;
import jakarta.annotation.PreDestroy;
import org.springframework.context.SmartLifecycle;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Service
@Lazy(false)
public class RedisWorkerService implements SmartLifecycle {

    private final StringRedisTemplate redisTemplate;
    private final AiQueueNames names;
    private final AgenticOrchestratorService orchestratorService;
    private final MemoryEngineService memoryEngineService; // <-- ADD THIS

    private ExecutorService workerPool;
    private volatile boolean running;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RedisWorkerService(StringRedisTemplate redisTemplate,
                              AgenticOrchestratorService orchestratorService,
                              MemoryEngineService memoryEngineService, AiQueueNames names) {
        this.redisTemplate = redisTemplate;
        this.orchestratorService = orchestratorService;
        this.memoryEngineService = memoryEngineService;
        this.names = names;
    }

    @Override
    public synchronized void start() {
        if (running) {
            return;
        }
        workerPool = Executors.newFixedThreadPool(3);
        running = true;
        workerPool.execute(() -> {
            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    String jobPayload = redisTemplate.opsForList().leftPop(names.queue(), Duration.ofSeconds(5));
                    if (running && jobPayload != null) {
                        workerPool.submit(() -> processJob(jobPayload));
                    }
                } catch (Exception e) {
                    if (!running || Thread.currentThread().isInterrupted()) {
                        break;
                    }
                    System.err.println("Worker Polling Error: " + e.getMessage());
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    @Override
    @PreDestroy
    public synchronized void stop() {
        running = false;
        if (workerPool == null) {
            return;
        }
        workerPool.shutdownNow();
        try {
            if (!workerPool.awaitTermination(5, TimeUnit.SECONDS)) {
                System.err.println("Redis workers did not finish within the shutdown timeout");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void processJob(String jobPayload) {
        System.out.println("--- Worker Picked Up Job! Payload: " + jobPayload + " ---");

        String[] parts = jobPayload.split("\\|\\|", 3);
        if (parts.length != 3) {
            System.err.println("Invalid job payload format.");
            return;
        }

        String jobId = parts[0];
        String userId = parts[1];
        String query = parts[2];

        try {
            System.out.println("Sending status update to frontend...");
            publish(jobId, "status", "Processing intent and building execution plan...");

            System.out.println("Orchestrator is processing the query...");
            ChatAnswer finalAnswer = orchestratorService.processUserQuery(query, userId);

            System.out.println("Publishing final answer to frontend...");
            // Keep citations and answer together: Pub/Sub callbacks can run concurrently.
            publish(jobId, "message", objectMapper.writeValueAsString(finalAnswer));

            if (orchestratorService.answerWithoutSearch(query).isEmpty()) {
                System.out.println("Extracting memories in background...");
                memoryEngineService.extractAndSaveMemories(userId, query);
            }

            System.out.println("--- Job Complete ---");

        } catch (Exception e) {
            Throwable failure = e;
            while (failure instanceof CompletionException && failure.getCause() != null) failure = failure.getCause();
            System.err.println("Worker Error: " + failure.getClass().getSimpleName());
            publish(jobId, "error", failure instanceof AnswerGenerationException
                    ? failure.getMessage() : "I couldn't complete this request. Please try again.");
        }
    }

    private void publish(String jobId, String eventName, String payload) {
        if (!running) {
            return;
        }
        String message = jobId + "||" + eventName + "||" + payload;
        redisTemplate.convertAndSend(names.channel(), message);
    }
}
