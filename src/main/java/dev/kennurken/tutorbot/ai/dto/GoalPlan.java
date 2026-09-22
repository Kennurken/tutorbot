package dev.kennurken.tutorbot.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.util.List;

/** A goal broken into ordered, verifiable study tasks. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record GoalPlan(String summary, @NotEmpty @Valid List<Step> steps) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Step(
            @NotBlank String title,
            String subject,
            String topic,
            @Pattern(regexp = "THEORY|PROGRAMMING|LANGUAGE|MATH|READING|PROJECT|OTHER") String type,
            Integer minutes) {
    }
}
