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

    public boolean hasWebhookSecret() {
        return webhookSecret != null && !webhookSecret.isBlank();
    }

    /**
     * Telegram accepts only [A-Za-z0-9_-] (1-256 chars) as a secret token, but a generated
     * secret (e.g. from Render) may contain anything. The value actually registered with
     * Telegram and compared on inbound requests is therefore the SHA-256 hex of the configured
     * secret: always valid, and the raw secret never leaves the process.
     */
    public String effectiveWebhookSecret() {
        if (!hasWebhookSecret()) {
            return null;
        }
        try {
            byte[] digest = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(webhookSecret.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
