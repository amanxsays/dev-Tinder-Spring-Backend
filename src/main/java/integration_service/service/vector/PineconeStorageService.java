package integration_service.service.vector;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.BooleanSupplier;
import org.springframework.beans.factory.annotation.Autowired;

@Service
public class PineconeStorageService {

    private final EmbeddingService embeddingService;
    private final Index pineconeIndex;

    @Autowired
    public PineconeStorageService(
            EmbeddingService embeddingService,
            @org.springframework.beans.factory.annotation.Value("${pinecone.api-key}") String apiKey,
            @org.springframework.beans.factory.annotation.Value("${pinecone.index-name}") String indexName) {

        this.embeddingService = embeddingService;
        Pinecone pinecone = new Pinecone.Builder(apiKey).build();
        this.pineconeIndex = pinecone.getIndexConnection(indexName);
    }

    PineconeStorageService(EmbeddingService embeddingService, Index pineconeIndex) {
        this.embeddingService = embeddingService; this.pineconeIndex = pineconeIndex;
    }

    public void embedAndStoreChunks(String candidateId, String documentId, String fileName, String fileUrl, List<String> chunks) {
        embedAndStoreChunks(candidateId, documentId, fileName, fileUrl, chunks, () -> true);
    }

    public void embedAndStoreChunks(String candidateId, String documentId, String fileName, String fileUrl,
                                    List<String> chunks, BooleanSupplier isCurrent) {
        for (int i = 0; i < chunks.size(); i++) {
            if (!isCurrent.getAsBoolean()) return;
            String chunkText = chunks.get(i);

            List<Float> embedding = embeddingService.generateEmbedding(chunkText);
            if (!isCurrent.getAsBoolean()) return;

            String vectorId = documentId + "_chunk_" + i;

            Struct metadata = Struct.newBuilder()
                    .putFields("candidateId", Value.newBuilder().setStringValue(candidateId).build())
                    .putFields("documentId", Value.newBuilder().setStringValue(documentId).build())
                    .putFields("fileName", Value.newBuilder().setStringValue(fileName).build())
                    .putFields("fileUrl", Value.newBuilder().setStringValue(fileUrl).build())
                    .putFields("chunkIndex", Value.newBuilder().setNumberValue(i).build())
                    .putFields("text", Value.newBuilder().setStringValue(chunkText).build())
                    .build();

            pineconeIndex.upsert(vectorId, embedding, null, null, metadata, "devtinder-candidates");
        }
    }

    // Deleting stored vector IDs does not call EmbeddingService.
    public void deleteVectors(List<String> vectorIds) {
        for (int start = 0; start < vectorIds.size(); start += 1000) {
            pineconeIndex.deleteByIds(vectorIds.subList(start, Math.min(start + 1000, vectorIds.size())),
                    "devtinder-candidates");
        }
    }
}
