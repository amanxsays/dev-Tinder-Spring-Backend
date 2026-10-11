package integration_service.service.ingestion;

import integration_service.model.DocumentChunk;
import integration_service.service.vector.PineconeStorageService;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResumeCleanupServiceTests {
    private static final String CANDIDATE = "507f1f77bcf86cd799439011";
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final PineconeStorageService pinecone = mock(PineconeStorageService.class);
    private final ResumeCleanupService service = new ResumeCleanupService(mongo, pinecone);

    @Test
    void replacementDeletesOnlyOldIdsAndDeletesPineconeBeforeMongo() {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("users"))).thenReturn(
                new Document("resumeDocumentId", "new").append("resumeUrl", "https://example.test/new.pdf"));
        when(mongo.find(any(Query.class), eq(DocumentChunk.class))).thenReturn(List.of(chunk("old"), chunk("new")));
        assertEquals(1, service.cleanupCandidate(CANDIDATE));
        var order = inOrder(pinecone, mongo);
        order.verify(pinecone).deleteVectors(List.of("old_chunk_0"));
        ArgumentCaptor<Query> query = ArgumentCaptor.forClass(Query.class);
        order.verify(mongo).remove(query.capture(), eq(DocumentChunk.class));
        assertEquals(CANDIDATE, query.getValue().getQueryObject().get("candidateId"));
        assertEquals(new Document("$in", List.of("old_chunk_0")), query.getValue().getQueryObject().get("_id"));
    }

    @Test
    void deletionRemovesAllCandidateChunks() {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("users"))).thenReturn(new Document());
        when(mongo.find(any(Query.class), eq(DocumentChunk.class))).thenReturn(List.of(chunk("old"), chunk("new")));
        assertEquals(2, service.cleanupCandidate(CANDIDATE));
        verify(pinecone).deleteVectors(List.of("old_chunk_0", "new_chunk_0"));
    }

    @Test
    void failedPineconeDeletionKeepsMongoIdsForRetry() {
        when(mongo.find(any(Query.class), eq(DocumentChunk.class))).thenReturn(List.of(chunk("old")));
        doThrow(new IllegalStateException("offline")).when(pinecone).deleteVectors(anyList());
        assertThrows(IllegalStateException.class, () -> service.cleanupCandidate(CANDIDATE));
        verify(mongo, never()).remove(any(Query.class), eq(DocumentChunk.class));
    }

    @Test
    void ambiguousLegacyCurrentDocumentIsPreservedUntilReplacement() {
        when(mongo.findOne(any(Query.class), eq(Document.class), eq("users"))).thenReturn(
                new Document("resumeFileName", "Resume.pdf").append("resumeUrl", "https://example.test/current.pdf"));
        when(mongo.find(any(Query.class), eq(DocumentChunk.class))).thenReturn(List.of(chunk("legacy")));
        assertEquals(0, service.cleanupCandidate(CANDIDATE));
        verifyNoInteractions(pinecone);
    }

    @Test
    void missingCandidateDoesNotDeleteAnything() {
        assertThrows(IllegalArgumentException.class, () -> service.cleanupCandidate(null));
        verifyNoInteractions(mongo, pinecone);
    }

    private DocumentChunk chunk(String id) {
        DocumentChunk chunk = new DocumentChunk(); chunk.setId(id + "_chunk_0");
        chunk.setDocumentId(id); chunk.setCandidateId(CANDIDATE); chunk.setFileName("Resume.pdf");
        return chunk;
    }
}
