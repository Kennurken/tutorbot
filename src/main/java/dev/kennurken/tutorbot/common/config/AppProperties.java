package dev.kennurken.tutorbot.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Global, user-independent defaults. */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(@NotBlank String defaultTimezone, @NotBlank String defaultLanguage) {
}
