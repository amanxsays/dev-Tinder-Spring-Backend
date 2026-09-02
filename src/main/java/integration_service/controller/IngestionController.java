package integration_service.controller;

import integration_service.service.ingestion.ChunkingService;
import integration_service.service.ingestion.DocumentParsingService;
import integration_service.service.vector.DocumentMetadataService;
import integration_service.service.vector.PineconeStorageService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/ingestion")
public class IngestionController {

    private final DocumentParsingService parsingService;
    private final ChunkingService chunkingService;
    private final PineconeStorageService pineconeService;
    private final DocumentMetadataService metadataService;

    public IngestionController(DocumentParsingService parsingService,
                               ChunkingService chunkingService,
                               PineconeStorageService pineconeService,
                               DocumentMetadataService metadataService) {
        this.parsingService = parsingService;
        this.chunkingService = chunkingService;
        this.pineconeService = pineconeService;
        this.metadataService = metadataService;
    }

    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> processDocument(@RequestBody Map<String, String> request) {
        String fileUrl = request.get("fileUrl");
        String candidateId = request.get("candidateId");
        String fileName = request.get("fileName");
        String documentId = UUID.randomUUID().toString();

        try {
            String rawText = parsingService.parseFromUrl(fileUrl);

            List<String> chunks = chunkingService.chunkText(rawText);

            pineconeService.embedAndStoreChunks(candidateId, documentId, fileName, chunks);

            metadataService.saveChunks(candidateId, documentId, fileName, chunks);

            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "documentId", documentId,
                    "chunksProcessed", String.valueOf(chunks.size())
            ));

        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }
}