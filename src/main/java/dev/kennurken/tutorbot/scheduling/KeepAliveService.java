package dev.kennurken.tutorbot.scheduling;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Free-tier hosts (Render) spin a service down after ~15 minutes without inbound HTTP traffic,
 * and a sleeping bot sends no reminders. Requesting our own public URL through the host's proxy
 * counts as traffic and keeps the instance awake without any external cron account.
 * Disabled when no public URL is configured (local runs, tests).
 */
@Component
public class KeepAliveService {

    private static final Logger log = LoggerFactory.getLogger(KeepAliveService.class);

    private final String publicUrl;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

    public KeepAliveService(@Value("${app.public-url:}") String publicUrl) {
        this.publicUrl = publicUrl == null ? "" : publicUrl.replaceAll("/+$", "");
    }

    @Scheduled(fixedDelayString = "${app.keep-alive-interval:5m}", initialDelayString = "2m")
    public void ping() {
        if (publicUrl.isBlank()) {
            return;
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(publicUrl + "/actuator/health"))
                    .timeout(Duration.ofSeconds(20)).GET().build();
            HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
            log.debug("Keep-alive ping -> {}", response.statusCode());
        } catch (Exception e) {
            log.warn("Keep-alive ping failed: {}", e.getMessage());
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
