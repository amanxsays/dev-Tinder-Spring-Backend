package integration_service.service;

import integration_service.dto.CodeforcesStats;
import integration_service.dto.CodeforcesResponse;
import integration_service.dto.GitHubStats;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class StatsService {

    private final RestClient githubClient;
    private final RestClient codeforcesClient;
    private final MongoTemplate mongoTemplate;

    public StatsService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        this.codeforcesClient = RestClient.builder().baseUrl("https://codeforces.com").build();
        RestClient.Builder gitHubBuilder= RestClient.builder().baseUrl("https://api.github.com").defaultHeader("User-Agent", "DevTinder-App");
        String githubToken = System.getenv("GITHUB_TOKEN");
        if (githubToken != null && !githubToken.trim().isEmpty()) {
            System.out.println("✅ GITHUB_TOKEN loaded successfully!");
            gitHubBuilder.defaultHeader("Authorization", "Bearer " + githubToken);
        } else {
            System.out.println("⚠️ WARNING: No GITHUB_TOKEN found. Default rate limits apply.");
        }
        this.githubClient = gitHubBuilder.build();
    }

    public GitHubStats getGitHubStats(String username,String userId) {
        try {
            GitHubStats stats= githubClient.get()
                    .uri("/users/{username}", username)
                    .retrieve()
                    .body(GitHubStats.class);
            if (stats != null && userId != null && !userId.isEmpty()) {
                Query query = new Query(Criteria.where("_id").is(new ObjectId(userId)));
                Update update = new Update().set("integrations.github", stats);
                mongoTemplate.updateFirst(query, update, "users");
                System.out.println("✅ Saved fresh GitHub stats to MongoDB for User: " + userId);
            }
            return stats;
        } catch (Exception e) {
            System.out.println("Failed to fetch GitHub data for: " + username+ "Bcz of "+e.getMessage());
            return null;
        }
    }
    public CodeforcesStats getCodeforcesStats(String username,String userId) {
        try {
            CodeforcesResponse response= codeforcesClient.get()
                    .uri("/api/user.info?handles={handle}", username)
                    .retrieve()
                    .body(CodeforcesResponse.class);
            if(response!=null && "OK".equals(response.status()) && !response.result().isEmpty()){
                CodeforcesStats stats= response.result().get(0);
                if (userId != null && !userId.isEmpty()) {
                    Query query = new Query(Criteria.where("_id").is(new ObjectId(userId)));
                    Update update = new Update().set("integrations.codeforces", stats);
                    mongoTemplate.updateFirst(query, update, "users");
                    System.out.println("✅ Saved fresh Codeforces stats to MongoDB for User: " + userId);
                }
                return stats;
            }
        } catch (Exception e) {
            System.out.println("Failed to fetch Codeforces data for: " + username);
        }
        return null;
    }
}
