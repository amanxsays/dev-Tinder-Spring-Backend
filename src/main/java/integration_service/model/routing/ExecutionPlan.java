package integration_service.model.routing;

import java.util.List;

public record ExecutionPlan(
        Strategy strategy,
        ExecutionTier executionTier,
        RetrievalMode retrievalMode,
        List<String> decomposedQueries
) {}