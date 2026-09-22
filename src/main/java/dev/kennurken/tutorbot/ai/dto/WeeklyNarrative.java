package dev.kennurken.tutorbot.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotBlank;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record WeeklyNarrative(
        @NotBlank String summary,
        List<String> observations,
        List<Recommendation> recommendations) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Recommendation(String change, String reason) {
    }
}
