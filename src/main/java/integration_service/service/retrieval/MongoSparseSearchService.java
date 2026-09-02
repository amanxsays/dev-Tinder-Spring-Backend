package integration_service.service.retrieval;

import integration_service.model.DocumentChunk;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.data.mongodb.core.query.TextQuery;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class MongoSparseSearchService {

    private final MongoTemplate mongoTemplate;

    public MongoSparseSearchService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public List<DocumentChunk> search(String queryText, int topK) {
        if (queryText == null || queryText.trim().isEmpty()) {
            return List.of();
        }

        TextCriteria criteria = TextCriteria.forDefaultLanguage()
                .matchingAny(queryText.split("\\s+"));

        Query query = TextQuery.queryText(criteria)
                .sortByScore()
                .limit(topK);

        return mongoTemplate.find(query, DocumentChunk.class);
    }
}