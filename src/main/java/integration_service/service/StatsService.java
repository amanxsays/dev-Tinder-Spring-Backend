package integration_service.service;

import integration_service.dto.CodeforcesStats;
import integration_service.dto.CodeforcesResponse;
import integration_service.dto.GitHubStats;
import integration_service.dto.LeetcodeStats;
import org.bson.types.ObjectId;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.Map;

@Service
public class StatsService {

    private final RestClient githubClient;
    private final RestClient codeforcesClient;
    private final RestClient leetcodeClient;
    private final MongoTemplate mongoTemplate;

    public StatsService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
        //cf
        this.codeforcesClient = RestClient.builder().baseUrl("https://codeforces.com").build();
        //git
        RestClient.Builder gitHubBuilder= RestClient.builder().baseUrl("https://api.github.com").defaultHeader("User-Agent", "DevTinder-App");
        String githubToken = System.getenv("GITHUB_TOKEN");
        if (githubToken != null && !githubToken.trim().isEmpty()) {
            System.out.println("✅ GITHUB_TOKEN loaded successfully!");
            gitHubBuilder.defaultHeader("Authorization", "Bearer " + githubToken);
        } else {
            System.out.println("⚠️ WARNING: No GITHUB_TOKEN found. Default rate limits apply.");
        }
        this.githubClient = gitHubBuilder.build();
        //leetcode
        this.leetcodeClient = RestClient.builder()
                .baseUrl("https://leetcode.com")
                .defaultHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .build();
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

    public LeetcodeStats getLeetCodeStats(String username, String userId) {
        try {
            // NEW QUERY: Fetches profile stats AND contest ranking/badges simultaneously
            String query = "{\"query\": \"query userProblemsSolved($username: String!) { " +
                    "matchedUser(username: $username) { " +
                    "  profile { userAvatar ranking } " +
                    "  submitStats { acSubmissionNum { difficulty count } } " +
                    "} " +
                    "userContestRanking(username: $username) { " +
                    "  rating " +
                    "  badge { name } " +
                    "} }\", \"variables\": {\"username\": \"" + username + "\"}}";

            LeetcodeStats stats = leetcodeClient.post()
                    .uri("/graphql")
                    .header("Referer", "https://leetcode.com/" + username + "/")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(query)
                    .retrieve()
                    .body(LeetcodeStats.class);

            if (stats != null && userId != null && !userId.isEmpty()) {
                Query mongoQuery = new Query(Criteria.where("_id").is(new ObjectId(userId)));

                // CRITICAL FIX: Save the parent 'data' object so MongoDB gets both the profile and the contest ranking
                Update update = new Update().set("integrations.leetcode", stats.data());

                mongoTemplate.updateFirst(mongoQuery, update, "users");
                System.out.println("✅ Saved LeetCode Stats, Rating, and Badge for User: " + userId);
            }
            return stats;
        } catch (Exception e) {
            System.out.println("Failed to fetch LeetCode data for: " + username + " Bcz of " + e.getMessage());
            return null;
        }
    }
}
