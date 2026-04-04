package integration_service.dto;

import java.util.List;

public record CodeforcesResponse(
        String status,
        List<CodeforcesStats> result
){}
