package integration_service.service.vector;

import io.pinecone.clients.Index;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.stream.IntStream;
import static org.mockito.Mockito.*;

class PineconeStorageServiceTests {
    @Test
    void deleteBatchesIdsWithoutAnyAiCalls() {
        var embeddings = mock(EmbeddingService.class);
        var index = mock(Index.class);
        var service = new PineconeStorageService(embeddings, index);
        var ids = IntStream.range(0, 1001).mapToObj(i -> "old_chunk_" + i).toList();
        service.deleteVectors(ids);
        verify(index).deleteByIds(ids.subList(0, 1000), "devtinder-candidates");
        verify(index).deleteByIds(List.of("old_chunk_1000"), "devtinder-candidates");
        verifyNoInteractions(embeddings);
    }

    @Test
    void supersededJobMakesNoEmbeddingOrUpsertCalls() {
        var embeddings = mock(EmbeddingService.class);
        var index = mock(Index.class);
        new PineconeStorageService(embeddings, index).embedAndStoreChunks(
                "candidate", "old", "Resume.pdf", "https://example.test/old.pdf", List.of("chunk"), () -> false);
        verifyNoInteractions(embeddings, index);
    }
}
