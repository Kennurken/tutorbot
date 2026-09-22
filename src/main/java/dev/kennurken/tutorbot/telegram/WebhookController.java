package dev.kennurken.tutorbot.telegram;

import dev.kennurken.tutorbot.telegram.api.TelegramTypes.Update;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

/**
 * Receives updates in webhook mode. Telegram signs nothing, but it echoes the secret token we
 * registered with setWebhook in a header; anything without the right token is dropped.
 * Returns 200 immediately and processes asynchronously (Telegram retries slow webhooks).
 */
@RestController
public class WebhookController {

    private final UpdateProcessor processor;
    private final TelegramProperties properties;

    public WebhookController(UpdateProcessor processor, TelegramProperties properties) {
        this.processor = processor;
        this.properties = properties;
    }

    @PostMapping("/telegram/webhook")
    public ResponseEntity<Void> receive(@RequestBody Update update,
                                        @RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String token) {
        String expected = properties.webhookSecret();
        if (expected == null || expected.isBlank() || token == null
                || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), token.getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        processor.processAsync(update);
        return ResponseEntity.ok().build();
    }
}
