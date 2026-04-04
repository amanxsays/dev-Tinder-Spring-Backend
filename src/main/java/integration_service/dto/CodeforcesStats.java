package integration_service.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public record CodeforcesStats (
        String handle,
        int rating,
        String rank,
        @JsonProperty("titlePhoto") String titlePhoto,
        int maxRating,
        String maxRank
){}
