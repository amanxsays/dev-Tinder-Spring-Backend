package integration_service.service.ingestion;

import integration_service.model.DocumentChunk;
import integration_service.service.vector.PineconeStorageService;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Objects;

@Service
public class ResumeCleanupService {
    private static final Logger log = LoggerFactory.getLogger(ResumeCleanupService.class);
    private final MongoTemplate mongo;
    private final PineconeStorageService pinecone;
    public ResumeCleanupService(MongoTemplate mongo, PineconeStorageService pinecone) {
        this.mongo = mongo; this.pinecone = pinecone;
    }

    public int cleanupCandidate(String candidateId) {
        if (candidateId == null || !ObjectId.isValid(candidateId)) throw new IllegalArgumentException("Invalid candidate ID");
        Query chunksQuery = Query.query(Criteria.where("candidateId").is(candidateId));
        chunksQuery.fields().include("documentId").include("fileName").include("fileUrl");
        // Snapshot IDs first: a later upload must never be deleted using an older profile snapshot.
        List<DocumentChunk> chunks = mongo.find(chunksQuery, DocumentChunk.class);
        Query profileQuery = Query.query(Criteria.where("_id").is(new ObjectId(candidateId)));
        profileQuery.fields().include("resumeDocumentId").include("resumeUrl").include("resumeFileName");
        Document user = mongo.findOne(profileQuery, Document.class, "users");
        List<String> retiredIds = chunks.stream()
                .filter(chunk -> !mightBeCurrent(chunk, user)).map(DocumentChunk::getId).toList();
        deleteChunks(candidateId, retiredIds);
        return retiredIds.size();
    }

    private boolean mightBeCurrent(DocumentChunk chunk, Document user) {
        if (user == null || user.getString("resumeUrl") == null || user.getString("resumeUrl").isBlank()) return false;
        String currentId = user.getString("resumeDocumentId");
        if (currentId != null && !currentId.isBlank()) return Objects.equals(currentId, chunk.getDocumentId());
        // Preserve ambiguous legacy chunks until the next replacement establishes a version ID.
        return Objects.equals(chunk.getFileName(), user.getString("resumeFileName"))
                && (chunk.getFileUrl() == null || chunk.getFileUrl().isBlank()
                || Objects.equals(chunk.getFileUrl(), user.getString("resumeUrl")));
    }

    public void deleteChunks(String candidateId, List<String> ids) {
        if (ids.isEmpty()) return;
        // Keep Mongo records on Pinecone failure: they are the durable retry list.
        pinecone.deleteVectors(ids);
        mongo.remove(Query.query(Criteria.where("candidateId").is(candidateId).and("_id").in(ids)), DocumentChunk.class);
    }

    @Scheduled(fixedDelayString = "${resume.cleanup.interval-ms:60000}", initialDelayString = "${resume.cleanup.interval-ms:60000}")
    public void retryRetiredResumes() {
        try {
            List<String> candidates = mongo.findDistinct(new Query(), "candidateId", DocumentChunk.class, String.class);
            for (String candidateId : candidates) {
                try { cleanupCandidate(candidateId); }
                catch (RuntimeException e) { log.warn("Resume cleanup pending for candidate {}: {}", candidateId, e.getClass().getSimpleName()); }
            }
        } catch (RuntimeException e) { log.warn("Resume cleanup scan could not run: {}", e.getClass().getSimpleName()); }
    }
}
