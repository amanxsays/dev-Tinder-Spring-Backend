package integration_service.service.streaming;

import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
public class RedisStreamListener {

    private final SseEmitterManager emitterManager;

    public RedisStreamListener(SseEmitterManager emitterManager) {
        this.emitterManager = emitterManager;
    }

    public void receiveMessage(String message) {
        try {
            String[] parts = message.split("\\|\\|", 3);
            if (parts.length != 3) return;

            String jobId = parts[0];
            String eventName = parts[1];
            String payload = parts[2];

            SseEmitter emitter = emitterManager.getEmitter(jobId);

            if (emitter != null) {
                emitter.send(SseEmitter.event().name(eventName).data(payload));

                if ("message".equals(eventName) || "error".equals(eventName)) {
                    emitter.complete();
                    emitterManager.removeEmitter(jobId);
                }
            }
        } catch (Exception e) {
            System.err.println("Failed to forward Redis message to SSE: " + e.getMessage());
        }
    }
}