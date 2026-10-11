package integration_service.model;

import java.util.List;

public record ChatAnswer(String text, List<ResumeSource> sources) {}
