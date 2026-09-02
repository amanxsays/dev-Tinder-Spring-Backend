package integration_service.service.retrieval;

import integration_service.model.DocumentChunk;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class ReciprocalRankFusionService {

    private static final int RRF_K = 60;

    private static final int MAX_CHUNKS_PER_DOC = 2;

    public List<DocumentChunk> fuseAndFilter(List<DocumentChunk> denseResults, List<DocumentChunk> sparseResults) {
        Map<String, Double> rrfScores = new HashMap<>();
        Map<String, DocumentChunk> chunkMap = new HashMap<>();

        for (int i = 0; i < denseResults.size(); i++) {
            DocumentChunk chunk = denseResults.get(i);
            double score = 1.0 / (RRF_K + (i + 1));

            rrfScores.put(chunk.getId(), rrfScores.getOrDefault(chunk.getId(), 0.0) + score);
            chunkMap.putIfAbsent(chunk.getId(), chunk);
        }

        for (int i = 0; i < sparseResults.size(); i++) {
            DocumentChunk chunk = sparseResults.get(i);
            double score = 1.0 / (RRF_K + (i + 1));

            rrfScores.put(chunk.getId(), rrfScores.getOrDefault(chunk.getId(), 0.0) + score);
            chunkMap.putIfAbsent(chunk.getId(), chunk);
        }

        List<String> sortedChunkIds = new ArrayList<>(rrfScores.keySet());
        sortedChunkIds.sort((id1, id2) -> Double.compare(rrfScores.get(id2), rrfScores.get(id1)));

        List<DocumentChunk> finalResults = new ArrayList<>();
        Map<String, Integer> docChunkCount = new HashMap<>();

        for (String chunkId : sortedChunkIds) {
            DocumentChunk chunk = chunkMap.get(chunkId);
            String docId = chunk.getDocumentId();

            int currentCount = docChunkCount.getOrDefault(docId, 0);

            if (currentCount < MAX_CHUNKS_PER_DOC) {
                finalResults.add(chunk);
                docChunkCount.put(docId, currentCount + 1);
            }
        }

        return finalResults;
    }
}