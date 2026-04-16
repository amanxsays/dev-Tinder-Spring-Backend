package integration_service.dto;

import java.util.List;

public record LeetcodeStats(Data data) {

    // Added userContestRanking to the main Data record
    public record Data(
            MatchedUser matchedUser,
            UserContestRanking userContestRanking
    ) {}

    public record MatchedUser(
            Profile profile,
            SubmitStats submitStats
    ) {}

    public record Profile(
            String userAvatar,
            int ranking
    ) {}

    public record SubmitStats(List<SubmissionNode> acSubmissionNum) {}

    public record SubmissionNode(String difficulty, int count) {}

    // NEW: Contest records
    public record UserContestRanking(
            Double rating,
            Badge badge
    ) {}

    public record Badge(String name) {}
}