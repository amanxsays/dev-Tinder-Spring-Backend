package integration_service.service.streaming;

import integration_service.service.routing.AgenticOrchestratorService;
import integration_service.service.memory.MemoryEngineService;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Lazy;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
@Lazy(false)
public class RedisWorkerService {

    private final StringRedisTemplate redisTemplate;
    private final AgenticOrchestratorService orchestratorService;
    private final MemoryEngineService memoryEngineService; // <-- ADD THIS

    private final ExecutorService workerPool = Executors.newFixedThreadPool(3);

    public RedisWorkerService(StringRedisTemplate redisTemplate,
                              AgenticOrchestratorService orchestratorService,
                              MemoryEngineService memoryEngineService) {
        this.redisTemplate = redisTemplate;
        this.orchestratorService = orchestratorService;
        this.memoryEngineService = memoryEngineService;
    }

    @PostConstruct
    public void startListening() {
        workerPool.execute(() -> {
            while (true) {
                try {
                    String jobPayload = redisTemplate.opsForList().leftPop("ai-job-queue", Duration.ofSeconds(5));
                    if (jobPayload != null) {
                        workerPool.submit(() -> processJob(jobPayload));
                    }
                } catch (Exception e) {
                    System.err.println("Worker Polling Error: " + e.getMessage());
                }
            }
        });
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
            String finalAnswer = orchestratorService.processUserQuery(query, userId);

            System.out.println("Publishing final answer to frontend...");
            publish(jobId, "message", finalAnswer);

            System.out.println("Extracting memories in background...");
            memoryEngineService.extractAndSaveMemories(userId, query);

            System.out.println("--- Job Complete ---");

        } catch (Exception e) {
            System.err.println("Worker Error: " + e.getMessage());
            publish(jobId, "error", "Agentic generation failed: " + e.getMessage());
        }
    }

    private void publish(String jobId, String eventName, String payload) {
        String message = jobId + "||" + eventName + "||" + payload;
        redisTemplate.convertAndSend("ai-chat-updates", message);
    }
}