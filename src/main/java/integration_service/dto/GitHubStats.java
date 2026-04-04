package integration_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record GitHubStats (
        @JsonProperty("public_repos") int publicRepos,
        int followers,
        int following,
        @JsonProperty("avatar_url") String avatarUrl,
        String name,
        String company
) {}
