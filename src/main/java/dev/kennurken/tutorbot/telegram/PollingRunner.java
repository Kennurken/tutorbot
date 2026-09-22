package dev.kennurken.tutorbot.telegram;

import dev.kennurken.tutorbot.telegram.api.TelegramApiException;
import dev.kennurken.tutorbot.telegram.api.TelegramClient;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.Update;
import jakarta.annotation.PreDestroy;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Long-polling transport for local development: no public URL needed. Production uses the
 * webhook. Both feed the same {@link UpdateProcessor}, so behaviour is identical.
 */
@Component
public class PollingRunner {

    private static final Logger log = LoggerFactory.getLogger(PollingRunner.class);

    private final TelegramClient client;
    private final UpdateProcessor processor;
    private final TelegramProperties properties;
    private volatile boolean running;
    private Thread thread;

    public PollingRunner(TelegramClient client, UpdateProcessor processor, TelegramProperties properties) {
        this.client = client;
        this.processor = processor;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (properties.mode() != TelegramProperties.Mode.POLLING || !properties.hasToken()) {
            return;
        }
        running = true;
        thread = new Thread(this::loop, "telegram-polling");
        thread.setDaemon(true);
        thread.start();
        log.info("Telegram long polling started");
    }

    private void loop() {
        long offset = 0;
        while (running) {
            try {
                List<Update> updates = client.getUpdates(offset, properties.pollingTimeoutSeconds());
                for (Update update : updates) {
                    offset = Math.max(offset, update.updateId() + 1);
                    try {
                        processor.process(update);
                    } catch (RuntimeException e) {
                        log.error("Processing update {} failed", update.updateId(), e);
                    }
                }
            } catch (TelegramApiException e) {
                log.warn("Polling error: {}", e.getMessage());
                sleep(5000);
            } catch (RuntimeException e) {
                log.error("Unexpected polling error", e);
                sleep(5000);
            }
        }
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }
}
