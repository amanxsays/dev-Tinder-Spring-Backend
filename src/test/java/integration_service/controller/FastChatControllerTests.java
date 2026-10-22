package integration_service.controller;

import integration_service.config.AiQueueNames;
import integration_service.repository.UserMemoryRepository;
import integration_service.service.retrieval.*;
import integration_service.service.routing.*;
import integration_service.service.streaming.SseEmitterManager;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

class FastChatControllerTests {
    @ParameterizedTest
    @ValueSource(strings = {"hii", "hiii", "hello", "who are you?"})
    void immediateSseAnswerBypassesRedisMongoAndGemini(String query) throws Exception {
        var router = mock(UnifiedRouterService.class);
        var retrieval = mock(HybridRetrievalService.class);
        var adaptive = mock(AdaptiveLoopService.class);
        var generation = mock(RAGGenerationService.class);
        var memory = mock(UserMemoryRepository.class);
        var sources = mock(ResumeSourceService.class);
        var orchestrator = new AgenticOrchestratorService(new FastIntentService(), router, retrieval,
                adaptive, generation, memory, sources);
        var redis = mock(StringRedisTemplate.class);
        var emitters = mock(SseEmitterManager.class);
        var mvc = MockMvcBuilders.standaloneSetup(new ChatController(emitters, redis,
                new AiQueueNames("test"), orchestrator)).build();
        var result = mvc.perform(post("/api/chat/stream").contentType("application/json")
                .content("{\"query\":\"" + query + "\",\"userId\":\"existing-user\"}")).andReturn();
        if (result.getRequest().isAsyncStarted()) result = mvc.perform(asyncDispatch(result)).andReturn();
        assertEquals(200, result.getResponse().getStatus());
        assertTrue(result.getResponse().getContentAsString().contains("event:message"));
        assertTrue(result.getResponse().getContentAsString().contains("\"sources\":[]"));
        verifyNoInteractions(redis, emitters, router, retrieval, adaptive, generation, memory, sources);
        assertTrue(orchestrator.processUserQuery(query, "existing-user").sources().isEmpty());
        verifyNoInteractions(memory, router, retrieval, adaptive, generation, sources);
    }
}
