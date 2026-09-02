package integration_service.repository;

import integration_service.model.UserMemory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface UserMemoryRepository extends MongoRepository<UserMemory, String> {
    Optional<UserMemory> findByUserId(String userId);
}