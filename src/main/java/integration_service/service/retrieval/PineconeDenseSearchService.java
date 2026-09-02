package integration_service.service.retrieval;

import com.google.protobuf.Struct;
import integration_service.model.DocumentChunk;
import integration_service.service.vector.EmbeddingService;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class PineconeDenseSearchService {

    private final EmbeddingService embeddingService;
    private final Index pineconeIndex;

    public PineconeDenseSearchService(
            EmbeddingService embeddingService,
            @Value("${pinecone.api-key}") String apiKey,
            @Value("${pinecone.index-name}") String indexName) {

        this.embeddingService = embeddingService;
        Pinecone pinecone = new Pinecone.Builder(apiKey).build();
        this.pineconeIndex = pinecone.getIndexConnection(indexName);
    }

    public List<DocumentChunk> search(String queryText, int topK) {
        List<Float> queryVector = embeddingService.generateEmbedding(queryText);

        var response = pineconeIndex.query(
                topK,
                queryVector,
                null,
                null,
                null,
                "devtinder-candidates",
                null,
                false,
                true
        );

        List<DocumentChunk> results = new ArrayList<>();

        if (response != null && response.getMatchesList() != null) {
            for (var match : response.getMatchesList()) {
                Struct metadata = match.getMetadata();

                DocumentChunk chunk = new DocumentChunk();
                chunk.setId(match.getId());
                chunk.setCandidateId(getMetaString(metadata, "candidateId"));
                chunk.setDocumentId(getMetaString(metadata, "documentId"));
                chunk.setFileName(getMetaString(metadata, "fileName"));
                chunk.setChunkIndex((int) getMetaNumber(metadata, "chunkIndex"));
                chunk.setText(getMetaString(metadata, "text"));

                results.add(chunk);
            }
        }

        return results;
    }

    private String getMetaString(Struct metadata, String key) {
        if (metadata != null && metadata.containsFields(key)) {
            return metadata.getFieldsOrThrow(key).getStringValue();
        }
        return "";
    }

    private double getMetaNumber(Struct metadata, String key) {
        if (metadata != null && metadata.containsFields(key)) {
            return metadata.getFieldsOrThrow(key).getNumberValue();
        }
        return 0.0;
    }
}