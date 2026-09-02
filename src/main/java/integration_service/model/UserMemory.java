package integration_service.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Document(collection = "ai_user_memories")
public class UserMemory {
    @Id
    private String id;
    private String userId;
    private List<String> preferences = new ArrayList<>();
    private Instant lastUpdatedAt;

    public UserMemory() {}

    public UserMemory(String userId) {
        this.userId = userId;
        this.lastUpdatedAt = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }
    public List<String> getPreferences() { return preferences; }
    public void setPreferences(List<String> preferences) { this.preferences = preferences; }
    public Instant getLastUpdatedAt() { return lastUpdatedAt; }
    public void setLastUpdatedAt(Instant lastUpdatedAt) { this.lastUpdatedAt = lastUpdatedAt; }
}