package integration_service.controller;

import integration_service.service.streaming.SseEmitterManager;
import integration_service.config.AiQueueNames;
import integration_service.service.routing.AgenticOrchestratorService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")
public class ChatController {

    private final SseEmitterManager emitterManager;
    private final StringRedisTemplate redisTemplate;
    private final AiQueueNames names;
    private final AgenticOrchestratorService orchestrator;

    public ChatController(SseEmitterManager emitterManager, StringRedisTemplate redisTemplate, AiQueueNames names,
                          AgenticOrchestratorService orchestrator) {
        this.emitterManager = emitterManager;
        this.redisTemplate = redisTemplate;
        this.names = names;
        this.orchestrator = orchestrator;
    }

    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamChat(@RequestBody ChatRequest request) {
        SseEmitter emitter = new SseEmitter(120000L);

        var immediate = orchestrator.answerWithoutSearch(request.query());
        if (immediate.isPresent()) {
            try {
                emitter.send(SseEmitter.event().name("message").data(immediate.get(), MediaType.APPLICATION_JSON));
                emitter.complete();
            } catch (Exception error) { emitter.completeWithError(error); }
            return emitter;
        }

        String jobId = UUID.randomUUID().toString();

        emitterManager.addEmitter(jobId, emitter);

        try {
            emitter.send(SseEmitter.event().name("status").data("Queuing AI Agent..."));

            String jobPayload = jobId + "||" + request.userId() + "||" + request.query();
            redisTemplate.opsForList().rightPush(names.queue(), jobPayload);

        } catch (Exception e) {
            emitter.completeWithError(e);
        }

        return emitter;
    }

    public record ChatRequest(String query, String userId) {}
}
