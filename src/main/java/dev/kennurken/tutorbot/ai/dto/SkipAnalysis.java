package dev.kennurken.tutorbot.ai.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Classification of a free-text skip reason. A hypothesis, never a verdict about the person. */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SkipAnalysis(
        @NotNull @Pattern(regexp = "OBJECTIVE_REASON|TOO_TIRED|FORGOT|TOO_DIFFICULT|DID_NOT_WANT_TO|BAD_SCHEDULE|EMERGENCY|OTHER")
        String category,
        String insight,
        String suggestion) {
}
