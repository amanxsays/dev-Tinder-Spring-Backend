package integration_service.service.retrieval;

import integration_service.model.DocumentChunk;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ActiveResumeService {
    private final MongoTemplate mongo;
    public ActiveResumeService(MongoTemplate mongo) { this.mongo = mongo; }

    public boolean isCurrent(String candidateId, String documentId, String fileUrl) {
        if (candidateId == null || candidateId.isBlank()) return false;
        Query query = Query.query(Criteria.where("_id").is(userId(candidateId)));
        query.fields().include("resumeUrl").include("resumeDocumentId");
        Document user = mongo.findOne(query, Document.class, "users");
        return user != null && Objects.equals(documentId, user.getString("resumeDocumentId"))
                && Objects.equals(fileUrl, user.getString("resumeUrl")) && safeUrl(fileUrl);
    }

    public List<DocumentChunk> filterCurrent(List<DocumentChunk> chunks) {
        if (chunks.isEmpty()) return List.of();
        List<Object> ids = chunks.stream().map(DocumentChunk::getCandidateId).filter(Objects::nonNull)
                .distinct().map(this::userId).toList();
        Query query = Query.query(Criteria.where("_id").in(ids));
        query.fields().include("resumeUrl").include("resumeFileName").include("resumeDocumentId");
        Map<String, Document> users = mongo.find(query, Document.class, "users").stream()
                .collect(Collectors.toMap(user -> user.get("_id").toString(), user -> user));
        Map<String, Boolean> legacyDocuments = new HashMap<>();
        return chunks.stream().filter(chunk -> {
            Document user = users.get(chunk.getCandidateId());
            if (user == null || !safeUrl(user.getString("resumeUrl"))
                    || !Objects.equals(chunk.getFileName(), user.getString("resumeFileName"))) return false;
            String currentId = user.getString("resumeDocumentId");
            if (currentId != null && !currentId.isBlank()) {
                if (!Objects.equals(currentId, chunk.getDocumentId())) return false;
            } else {
                String url = chunk.getFileUrl();
                if (url != null && !url.isBlank()) {
                    if (!Objects.equals(url, user.getString("resumeUrl"))) return false;
                } else {
                    String key = chunk.getCandidateId() + ":" + chunk.getDocumentId();
                    if (!legacyDocuments.computeIfAbsent(key, ignored -> isOnlyLegacyDocument(chunk))) return false;
                }
            }
            chunk.setFileUrl(user.getString("resumeUrl"));
            return true;
        }).toList();
    }

    private boolean isOnlyLegacyDocument(DocumentChunk chunk) {
        Query query = Query.query(Criteria.where("candidateId").is(chunk.getCandidateId())
                .and("fileName").is(chunk.getFileName()));
        query.fields().include("documentId");
        Set<String> documents = mongo.find(query, DocumentChunk.class).stream()
                .map(DocumentChunk::getDocumentId).collect(Collectors.toSet());
        return documents.size() == 1 && documents.contains(chunk.getDocumentId());
    }

    private Object userId(String id) { return ObjectId.isValid(id) ? new ObjectId(id) : id; }
    private boolean safeUrl(String value) {
        if (value == null || value.isBlank()) return false;
        try {
            URI uri = URI.create(value);
            return ("https".equalsIgnoreCase(uri.getScheme()) || "http".equalsIgnoreCase(uri.getScheme()))
                    && uri.getHost() != null && uri.getUserInfo() == null;
        } catch (IllegalArgumentException e) { return false; }
    }
}
