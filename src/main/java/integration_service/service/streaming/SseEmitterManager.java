package integration_service.service.streaming;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SseEmitterManager {
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void addEmitter(String jobId, SseEmitter emitter) {
        emitters.put(jobId, emitter);
        emitter.onCompletion(() -> emitters.remove(jobId));
        emitter.onTimeout(() -> emitters.remove(jobId));
        emitter.onError(e -> emitters.remove(jobId));
    }

    public SseEmitter getEmitter(String jobId) {
        return emitters.get(jobId);
    }

    public void removeEmitter(String jobId) {
        emitters.remove(jobId);
    }
}