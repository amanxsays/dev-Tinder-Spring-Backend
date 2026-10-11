package integration_service.controller;

import integration_service.service.ingestion.*;
import integration_service.service.retrieval.ActiveResumeService;
import integration_service.service.vector.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IngestionControllerTests {
    private final DocumentParsingService parser = mock(DocumentParsingService.class);
    private final ChunkingService chunker = mock(ChunkingService.class);
    private final PineconeStorageService pinecone = mock(PineconeStorageService.class);
    private final DocumentMetadataService metadata = mock(DocumentMetadataService.class);
    private final ResumeCleanupService cleanup = mock(ResumeCleanupService.class);
    private final ActiveResumeService active = mock(ActiveResumeService.class);
    private final IngestionController controller = new IngestionController(parser, chunker, pinecone, metadata, cleanup, active);
    private final Map<String, String> request = Map.of("candidateId", "candidate", "documentId", "new",
            "fileUrl", "https://example.test/new.pdf", "fileName", "Resume.pdf");

    @Test
    void supersededRequestDoesNotParseOrEmbed() {
        assertEquals("superseded", controller.processDocument(request).getBody().get("status"));
        verifyNoInteractions(parser, chunker, pinecone, metadata);
    }

    @Test
    void replacementDuringIndexingDeletesAllPotentiallyUpsertedIds() throws Exception {
        when(active.isCurrent(anyString(), anyString(), anyString())).thenReturn(true, true, false, false);
        when(parser.parseFromUrl(anyString())).thenReturn("resume text");
        when(chunker.chunkText(anyString())).thenReturn(List.of("one", "two"));
        assertEquals("superseded", controller.processDocument(request).getBody().get("status"));
        var order = inOrder(metadata, pinecone);
        order.verify(metadata).saveChunks("candidate", "new", "Resume.pdf", request.get("fileUrl"), List.of("one", "two"));
        order.verify(pinecone).embedAndStoreChunks(eq("candidate"), eq("new"), eq("Resume.pdf"),
                eq(request.get("fileUrl")), eq(List.of("one", "two")), any(BooleanSupplier.class));
        verify(cleanup).deleteChunks("candidate", List.of("new_chunk_0", "new_chunk_1"));
    }

    @Test
    void cleanupEndpointCallsOnlyCleanupService() {
        when(cleanup.cleanupCandidate("candidate")).thenReturn(4);
        assertEquals("4", controller.cleanupRetiredDocuments(Map.of("candidateId", "candidate")).getBody().get("chunksDeleted"));
        verifyNoInteractions(parser, chunker, pinecone, metadata, active);
    }
}
