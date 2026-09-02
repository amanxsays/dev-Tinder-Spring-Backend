package integration_service.service.vector;

import com.google.protobuf.Struct;
import com.google.protobuf.Value;
import io.pinecone.clients.Index;
import io.pinecone.clients.Pinecone;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class PineconeStorageService {

    private final EmbeddingService embeddingService;
    private final Index pineconeIndex;

    public PineconeStorageService(
            EmbeddingService embeddingService,
            @org.springframework.beans.factory.annotation.Value("${pinecone.api-key}") String apiKey,
            @org.springframework.beans.factory.annotation.Value("${pinecone.index-name}") String indexName) {

        this.embeddingService = embeddingService;
        Pinecone pinecone = new Pinecone.Builder(apiKey).build();
        this.pineconeIndex = pinecone.getIndexConnection(indexName);
    }

    public void embedAndStoreChunks(String candidateId, String documentId, String fileName, List<String> chunks) {
        for (int i = 0; i < chunks.size(); i++) {
            String chunkText = chunks.get(i);

            List<Float> embedding = embeddingService.generateEmbedding(chunkText);

            String vectorId = documentId + "_chunk_" + i;

            Struct metadata = Struct.newBuilder()
                    .putFields("candidateId", Value.newBuilder().setStringValue(candidateId).build())
                    .putFields("documentId", Value.newBuilder().setStringValue(documentId).build())
                    .putFields("fileName", Value.newBuilder().setStringValue(fileName).build())
                    .putFields("chunkIndex", Value.newBuilder().setNumberValue(i).build())
                    .putFields("text", Value.newBuilder().setStringValue(chunkText).build())
                    .build();

            pineconeIndex.upsert(vectorId, embedding, null, null, metadata, "devtinder-candidates");
        }
    }
}