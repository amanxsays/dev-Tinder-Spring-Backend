package integration_service.controller;

import integration_service.service.ingestion.ChunkingService;
import integration_service.service.ingestion.DocumentParsingService;
import integration_service.service.vector.DocumentMetadataService;
import integration_service.service.vector.PineconeStorageService;
import integration_service.service.ingestion.ResumeCleanupService;
import integration_service.service.retrieval.ActiveResumeService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private final DocumentParsingService parsingService;
    private final ChunkingService chunkingService;
    private final PineconeStorageService pineconeService;
    private final DocumentMetadataService metadataService;
    private final ResumeCleanupService cleanupService;
    private final ActiveResumeService activeResumes;

    public IngestionController(DocumentParsingService parsingService,
                               ChunkingService chunkingService,
                               PineconeStorageService pineconeService,
                               DocumentMetadataService metadataService, ResumeCleanupService cleanupService,
                               ActiveResumeService activeResumes) {
        this.parsingService = parsingService;
        this.chunkingService = chunkingService;
        this.pineconeService = pineconeService;
        this.metadataService = metadataService;
        this.cleanupService = cleanupService;
        this.activeResumes = activeResumes;
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> processDocument(@RequestBody Map<String, String> request) {
        String fileUrl = request.get("fileUrl");
        String candidateId = request.get("candidateId");
        String fileName = request.get("fileName");
        String documentId = request.get("documentId");
        if (fileUrl == null || candidateId == null || fileName == null || documentId == null || documentId.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing resume version details"));
        }
        List<String> chunks = List.of();

        try {
            if (!activeResumes.isCurrent(candidateId, documentId, fileUrl)) {
                return ResponseEntity.ok(Map.of("status", "superseded"));
            }
            String rawText = parsingService.parseFromUrl(fileUrl);

            chunks = chunkingService.chunkText(rawText);
            if (!activeResumes.isCurrent(candidateId, documentId, fileUrl)) {
                return ResponseEntity.ok(Map.of("status", "superseded"));
            }

            // Persist IDs before upsert so partial embedding failures can still be cleaned up.
            metadataService.saveChunks(candidateId, documentId, fileName, fileUrl, chunks);
            pineconeService.embedAndStoreChunks(candidateId, documentId, fileName, fileUrl, chunks,
                    () -> activeResumes.isCurrent(candidateId, documentId, fileUrl));
            if (!activeResumes.isCurrent(candidateId, documentId, fileUrl)) {
                return ResponseEntity.ok(Map.of("status", "superseded"));
            }

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "documentId", documentId,
                    "chunksProcessed", String.valueOf(chunks.size())
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Resume indexing failed"));
        } finally {
            // A late job must not resurrect vectors after a replacement/deletion.
            if (!chunks.isEmpty()) {
                try {
                    if (!activeResumes.isCurrent(candidateId, documentId, fileUrl)) {
                        try {
                            cleanupService.deleteChunks(candidateId, IntStream.range(0, chunks.size())
                                    .mapToObj(i -> documentId + "_chunk_" + i).toList());
                        } catch (RuntimeException cleanupError) {
                            // Restore the retry list if an overlapping cleanup already removed these records.
                            metadataService.saveChunks(candidateId, documentId, fileName, fileUrl, chunks);
                        }
                    }
                } catch (RuntimeException cleanupError) {
                    org.slf4j.LoggerFactory.getLogger(IngestionController.class)
                            .warn("Resume cleanup pending for document {}", documentId);
                }
            }
        }
    }

    @PostMapping("/cleanup")
    public ResponseEntity<Map<String, String>> cleanupRetiredDocuments(@RequestBody Map<String, String> request) {
        try {
            return ResponseEntity.ok(Map.of("status", "success", "chunksDeleted",
                    String.valueOf(cleanupService.cleanupCandidate(request.get("candidateId")))));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid candidate ID"));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", "Cleanup pending; will retry"));
        }
    }
}
