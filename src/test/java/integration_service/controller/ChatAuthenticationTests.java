package integration_service.controller;

import integration_service.config.AiQueueNames;
import integration_service.security.AiServiceAssertionVerifier;
import integration_service.service.routing.AgenticOrchestratorService;
import integration_service.service.streaming.SseEmitterManager;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class ChatAuthenticationTests {
    private static final String KEY = "isolated-test-key-32-bytes-minimum";
    private static final String USER = "68847cafa68d283cb2be86db";

    @Test void rejectsDirectBrowserRequestsBeforeFastIntentOrQueueAccess() throws Exception {
        var orchestrator = mock(AgenticOrchestratorService.class);
        var redis = mock(StringRedisTemplate.class);
        var emitters = mock(SseEmitterManager.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ChatController(emitters, redis,
                new AiQueueNames("test"), orchestrator, new AiServiceAssertionVerifier(KEY, true))).build();
        var response = mvc.perform(post("/api/chat/stream").contentType("application/json")
                .content("{\"query\":\"hello\",\"userId\":\"" + USER + "\"}")).andReturn();
        assertEquals(401, response.getResponse().getStatus());
        verifyNoInteractions(redis, orchestrator, emitters);
    }

    @Test void queuedIdentityComesFromSignedGatewayHeader() throws Exception {
        var orchestrator = mock(AgenticOrchestratorService.class);
        when(orchestrator.answerWithoutSearch(anyString())).thenReturn(Optional.empty());
        var redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked") var lists = (ListOperations<String, String>) mock(ListOperations.class);
        when(redis.opsForList()).thenReturn(lists);
        var emitters = mock(SseEmitterManager.class);
        var names = new AiQueueNames("test");
        var mvc = MockMvcBuilders.standaloneSetup(new ChatController(emitters, redis, names,
                orchestrator, new AiServiceAssertionVerifier(KEY, true))).build();
        String query = "Find React developers";
        String timestamp = Long.toString(Instant.now().getEpochSecond());
        String digest = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(query.getBytes(StandardCharsets.UTF_8)));
        var mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(KEY.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String signature = HexFormat.of().formatHex(mac.doFinal(("POST\n/api/chat/stream\n" + USER
                + "\n" + timestamp + "\n" + digest).getBytes(StandardCharsets.UTF_8)));
        var response = mvc.perform(post("/api/chat/stream").contentType("application/json")
                .header("X-DevTinder-User", USER).header("X-DevTinder-Timestamp", timestamp)
                .header("X-DevTinder-Signature", signature)
                .content("{\"query\":\"" + query + "\",\"userId\":\"forged\"}")).andReturn();
        assertEquals(200, response.getResponse().getStatus());
        verify(lists).rightPush(eq(names.queue()), matches("[^|]+\\|\\|" + USER + "\\|\\|" + query));
    }
}
