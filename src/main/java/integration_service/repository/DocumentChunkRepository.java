package integration_service.repository;

import integration_service.model.DocumentChunk;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentChunkRepository extends MongoRepository<DocumentChunk, String> {
    List<DocumentChunk> findAllBy(TextCriteria criteria);
}