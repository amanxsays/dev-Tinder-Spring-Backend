package integration_service.service.vector;

import integration_service.model.DocumentChunk;
import integration_service.repository.DocumentChunkRepository;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentMetadataService {

    private final DocumentChunkRepository repository;

    public DocumentMetadataService(DocumentChunkRepository repository) {
        this.repository = repository;
    }

    public void saveChunks(String candidateId, String documentId, String fileName, List<String> chunks) {
        List<DocumentChunk> documentChunks = new ArrayList<>();

        for (int i = 0; i < chunks.size(); i++) {
            DocumentChunk chunk = new DocumentChunk();
            chunk.setId(documentId + "_chunk_" + i);
            chunk.setCandidateId(candidateId);
            chunk.setDocumentId(documentId);
            chunk.setFileName(fileName);
            chunk.setChunkIndex(i);
            chunk.setText(chunks.get(i));

            documentChunks.add(chunk);
        }

        repository.saveAll(documentChunks);
    }
}