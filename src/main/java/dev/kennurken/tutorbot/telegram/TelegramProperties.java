package dev.kennurken.tutorbot.telegram;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "telegram")
public record TelegramProperties(
        String token,
        Mode mode,
        String webhookUrl,
        String webhookSecret,
        String allowedUserIds,
        int pollingTimeoutSeconds) {

    public enum Mode { POLLING, WEBHOOK, NONE }

    /** Empty set = open to everyone. A personal bot should list its owner here. */
    public Set<Long> allowedUsers() {
        if (allowedUserIds == null || allowedUserIds.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(allowedUserIds.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    public boolean hasToken() {
        return token != null && !token.isBlank();
    }
}
