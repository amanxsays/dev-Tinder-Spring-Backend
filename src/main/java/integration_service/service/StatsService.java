package integration_service.service;

import integration_service.dto.CodeforcesStats;
import integration_service.dto.CodeforcesResponse;
import integration_service.dto.GitHubStats;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

@Service
public class StatsService {

    private final RestClient githubClient;
    private final RestClient codeforcesClient;

    public StatsService() {
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

    public GitHubStats getGitHubStats(String username) {
        try {
            return githubClient.get()
                    .uri("/users/{username}", username)
                    .retrieve()
                    .body(GitHubStats.class);
        } catch (Exception e) {
            System.out.println("Failed to fetch GitHub data for: " + username+ "Bcz of "+e.getMessage());
            return null;
        }
    }
    public CodeforcesStats getCodeforcesStats(String username) {
        try {
            CodeforcesResponse response= codeforcesClient.get()
                    .uri("/api/user.info?handles={handle}", username)
                    .retrieve()
                    .body(CodeforcesResponse.class);
            if(response!=null && "OK".equals(response.status()) && !response.result().isEmpty()){
                return response.result().get(0);
            }
        } catch (Exception e) {
            System.out.println("Failed to fetch Codeforces data for: " + username);
        }
        return null;
    }
}
