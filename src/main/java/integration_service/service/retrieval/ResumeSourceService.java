package integration_service.service.retrieval;

import integration_service.model.DocumentChunk;
import integration_service.model.ResumeSource;
import org.springframework.stereotype.Service;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ResumeSourceService {
    private final ActiveResumeService activeResumes;
    public ResumeSourceService(ActiveResumeService activeResumes) { this.activeResumes = activeResumes; }
    public List<DocumentChunk> filterCurrent(List<DocumentChunk> chunks) { return activeResumes.filterCurrent(chunks); }
    public List<ResumeSource> getSources(List<DocumentChunk> chunks) {
        Map<String, ResumeSource> sources = new LinkedHashMap<>();
        for (DocumentChunk chunk : filterCurrent(chunks)) {
            if (chunk.getDocumentId() != null && chunk.getFileUrl() != null) {
                sources.putIfAbsent(chunk.getDocumentId(), new ResumeSource(chunk.getDocumentId(),
                        chunk.getCandidateId(), chunk.getFileName(), chunk.getFileUrl()));
            }
        }
        return List.copyOf(sources.values());
    }
}
