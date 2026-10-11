package integration_service.service.routing;

import integration_service.model.DocumentChunk;
import integration_service.model.ChatAnswer;
import integration_service.model.IntentType;
import integration_service.model.UserMemory;
import integration_service.model.routing.ExecutionPlan;
import integration_service.model.routing.ExecutionTier;
import integration_service.repository.UserMemoryRepository;
import integration_service.service.retrieval.AdaptiveLoopService;
import integration_service.service.retrieval.HybridRetrievalService;
import integration_service.service.retrieval.RAGGenerationService;
import integration_service.service.retrieval.ResumeSourceService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class AgenticOrchestratorService {

    private final FastIntentService fastIntentService;
    private final UnifiedRouterService unifiedRouterService;
    private final HybridRetrievalService hybridRetrievalService;
    private final AdaptiveLoopService adaptiveLoopService;
    private final RAGGenerationService ragGenerationService;
    private final UserMemoryRepository userMemoryRepository;
    private final ResumeSourceService resumeSourceService;

    public AgenticOrchestratorService(
            FastIntentService fastIntentService,
            UnifiedRouterService unifiedRouterService,
            HybridRetrievalService hybridRetrievalService,
            AdaptiveLoopService adaptiveLoopService,
            RAGGenerationService ragGenerationService,
            UserMemoryRepository userMemoryRepository,
            ResumeSourceService resumeSourceService) {

        this.fastIntentService = fastIntentService;
        this.unifiedRouterService = unifiedRouterService;
        this.hybridRetrievalService = hybridRetrievalService;
        this.adaptiveLoopService = adaptiveLoopService;
        this.ragGenerationService = ragGenerationService;
        this.userMemoryRepository = userMemoryRepository;
        this.resumeSourceService = resumeSourceService;
    }

    public ChatAnswer processUserQuery(String userQuery, String userId) {
        System.out.println("\n--- New Request: " + userQuery + " ---");

        String enhancedQuery = userQuery;
        if (userId != null && !userId.isEmpty()) {
            Optional<UserMemory> memoryOpt = userMemoryRepository.findByUserId(userId);
            if (memoryOpt.isPresent() && !memoryOpt.get().getPreferences().isEmpty()) {
                String memoryContext = String.join("; ", memoryOpt.get().getPreferences());
                enhancedQuery = userQuery + "\n\n[SYSTEM NOTE - Strict User Preferences to follow: " + memoryContext + "]";
                System.out.println("Injected Memory: " + memoryContext);
            }
        }

        IntentType intent = fastIntentService.classifyIntent(enhancedQuery);

        if (intent == IntentType.GREETING_ACK) {
            return new ChatAnswer("Hello! I am DevTinder's AI recruiter. How can I help you find candidates today?", List.of());
        }
        if (intent == IntentType.BOT_QA) {
            return new ChatAnswer("I am an Agentic AI assistant. I can search through developer resumes, compare technical skills, and help you find the perfect candidate using vector search.", List.of());
        }

        ExecutionPlan plan = unifiedRouterService.planExecution(enhancedQuery);

        List<DocumentChunk> retrievedContext;

        if (plan.executionTier() == ExecutionTier.DIRECT) {
            retrievedContext = hybridRetrievalService.retrieveCandidates(enhancedQuery);
        } else {
            retrievedContext = adaptiveLoopService.executeAdaptiveSearch(enhancedQuery);
        }

        retrievedContext = resumeSourceService.filterCurrent(retrievedContext);
        String answer = ragGenerationService.generateAnswer(enhancedQuery, retrievedContext);
        var sources = resumeSourceService.getSources(retrievedContext);
        long documentCount = retrievedContext.stream().map(DocumentChunk::getDocumentId).distinct().count();
        if (sources.size() != documentCount) {
            return new ChatAnswer("A resume changed while I was answering. Please ask again for current information.", List.of());
        }
        return new ChatAnswer(answer, sources);
    }
}
