package integration_service.service.retrieval;

import integration_service.model.DocumentChunk;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ResumeSourceServiceTests {
    private final MongoTemplate mongo = mock(MongoTemplate.class);
    private final ActiveResumeService active = new ActiveResumeService(mongo);
    private final ResumeSourceService service = new ResumeSourceService(active);

    @Test
    void excludesReplacedVersionsEvenWhenFilenamesAreIdentical() {
        users(new Document("_id", "candidate").append("resumeDocumentId", "new")
                .append("resumeFileName", "Resume.pdf").append("resumeUrl", "https://example.test/new.pdf"));
        var old = chunk("old", "candidate", "https://example.test/old.pdf");
        var current = chunk("new", "candidate", "https://example.test/new.pdf");
        assertEquals(List.of(current), active.filterCurrent(List.of(old, current)));
        var sources = service.getSources(List.of(old, current, current));
        assertEquals(1, sources.size());
        assertEquals("new", sources.getFirst().documentId());
    }

    @Test
    void deletedResumesProduceNoSourcesOrContext() {
        users(new Document("_id", "candidate"));
        assertTrue(service.getSources(List.of(chunk("old", "candidate", "https://example.test/old.pdf"))).isEmpty());
    }

    @Test
    void currentVersionUsesAuthoritativeUrlRatherThanOldVectorUrl() {
        users(new Document("_id", "candidate").append("resumeDocumentId", "current")
                .append("resumeFileName", "Resume.pdf").append("resumeUrl", "https://example.test/current.pdf"));
        var source = service.getSources(List.of(chunk("current", "candidate", null))).getFirst();
        assertEquals("https://example.test/current.pdf", source.fileUrl());
    }

    @Test
    void legacyResumesRequireMatchingUrl() {
        users(new Document("_id", "candidate").append("resumeFileName", "Resume.pdf")
                .append("resumeUrl", "https://example.test/current.pdf"));
        assertTrue(service.getSources(List.of(chunk("old", "candidate", "https://example.test/old.pdf"))).isEmpty());
        assertEquals(1, service.getSources(List.of(chunk("current", "candidate", "https://example.test/current.pdf"))).size());
    }

    @Test
    void ambiguousUrlLessLegacyVersionsAreExcluded() {
        users(new Document("_id", "candidate").append("resumeFileName", "Resume.pdf")
                .append("resumeUrl", "https://example.test/current.pdf"));
        var old = chunk("old", "candidate", null);
        var latest = chunk("latest", "candidate", null);
        when(mongo.find(any(Query.class), eq(DocumentChunk.class))).thenReturn(List.of(old, latest));
        assertTrue(service.getSources(List.of(old, latest)).isEmpty());
    }

    @Test
    void distinctCandidatesWithIdenticalFilenamesRemainDistinct() {
        when(mongo.find(any(Query.class), eq(Document.class), eq("users"))).thenReturn(List.of(
                new Document("_id", "a").append("resumeDocumentId", "one").append("resumeFileName", "Resume.pdf").append("resumeUrl", "https://example.test/a.pdf"),
                new Document("_id", "b").append("resumeDocumentId", "two").append("resumeFileName", "Resume.pdf").append("resumeUrl", "https://example.test/b.pdf")));
        assertEquals(2, service.getSources(List.of(chunk("one", "a", null), chunk("two", "b", null))).size());
    }

    private void users(Document user) {
        when(mongo.find(any(Query.class), eq(Document.class), eq("users"))).thenReturn(List.of(user));
    }
    private DocumentChunk chunk(String documentId, String candidateId, String url) {
        DocumentChunk chunk = new DocumentChunk();
        chunk.setId(documentId + "_chunk_0"); chunk.setDocumentId(documentId);
        chunk.setCandidateId(candidateId); chunk.setFileName("Resume.pdf"); chunk.setFileUrl(url);
        return chunk;
    }
}
