package integration_service.controller;

import integration_service.service.StatsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final StatsService statsService;

    public StatsController(StatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping
    public Map<String, Object> getAggregatedStats(@RequestParam(required = false) String github,
                                                  @RequestParam(required = false) String codeforces,
                                                  @RequestParam String userId) {
        Map<String, Object> response = new HashMap<>();

        if (github != null && !github.trim().isEmpty()) {
            response.put("github", statsService.getGitHubStats(github, userId));
        }

        if(codeforces != null && !codeforces.trim().isEmpty()) {
            response.put("codeforces", statsService.getCodeforcesStats(codeforces,userId));
        }
        return response;
    }
}
