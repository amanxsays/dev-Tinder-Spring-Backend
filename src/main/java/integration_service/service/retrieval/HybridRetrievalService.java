package integration_service.service.retrieval;

import integration_service.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Service
public class HybridRetrievalService {

    private final PineconeDenseSearchService denseSearchService;
    private final MongoSparseSearchService sparseSearchService;
    private final ReciprocalRankFusionService rrfService;

    private static final int TOP_K_PER_ENGINE = 15;

    public HybridRetrievalService(PineconeDenseSearchService denseSearchService,
                                  MongoSparseSearchService sparseSearchService,
                                  ReciprocalRankFusionService rrfService) {
        this.denseSearchService = denseSearchService;
        this.sparseSearchService = sparseSearchService;
        this.rrfService = rrfService;
    }

    public List<DocumentChunk> retrieveCandidates(String queryText) {
        if (queryText == null || queryText.trim().isEmpty()) {
            return List.of();
        }

        CompletableFuture<List<DocumentChunk>> denseFuture = CompletableFuture.supplyAsync(
                () -> denseSearchService.search(queryText, TOP_K_PER_ENGINE)
        );

        CompletableFuture<List<DocumentChunk>> sparseFuture = CompletableFuture.supplyAsync(
                () -> sparseSearchService.search(queryText, TOP_K_PER_ENGINE)
        );

        CompletableFuture.allOf(denseFuture, sparseFuture).join();

        List<DocumentChunk> denseResults = denseFuture.join();
        List<DocumentChunk> sparseResults = sparseFuture.join();

        return rrfService.fuseAndFilter(denseResults, sparseResults);
    }
}