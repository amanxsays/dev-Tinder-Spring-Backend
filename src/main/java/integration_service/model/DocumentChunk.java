package integration_service.model;

import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.TextIndexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "document_chunks")
@Data
public class DocumentChunk {
    @Id
    private String id;
    private String candidateId;
    private String documentId;
    private String fileName;
    private int chunkIndex;

    @TextIndexed(weight = 2)
    private String text;
}