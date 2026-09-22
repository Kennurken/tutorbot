package dev.kennurken.tutorbot.telegram;

import dev.kennurken.tutorbot.telegram.api.TelegramClient;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.BotCommand;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/** Registers the webhook (or removes it for polling) and publishes the command menu on boot. */
@Component
@Order(1)
public class TelegramStartup {

    private static final Logger log = LoggerFactory.getLogger(TelegramStartup.class);

    private final TelegramClient client;
    private final TelegramProperties properties;
    private final UpdateDispatcher dispatcher;

    public TelegramStartup(TelegramClient client, TelegramProperties properties, UpdateDispatcher dispatcher) {
        this.client = client;
        this.properties = properties;
        this.dispatcher = dispatcher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (!properties.hasToken() || properties.mode() == TelegramProperties.Mode.NONE) {
            log.warn("Telegram disabled (no token or mode=none)");
            return;
        }
        try {
            switch (properties.mode()) {
                case WEBHOOK -> {
                    String url = properties.webhookUrl().replaceAll("/+$", "") + "/telegram/webhook";
                    client.setWebhook(url, properties.webhookSecret());
                    log.info("Webhook registered at {}", url);
                }
                case POLLING -> client.deleteWebhook();
                default -> {
                    // nothing
                }
            }
            List<BotCommand> menu = dispatcher.commands().values().stream()
                    .filter(CommandHandler::listed)
                    .sorted(java.util.Comparator.comparing(CommandHandler::command))
                    .map(h -> new BotCommand(h.command(), h.description()))
                    .toList();
            client.setMyCommands(menu);
            log.info("Telegram ready as @{} with {} commands", client.getMe().username(), menu.size());
        } catch (RuntimeException e) {
            log.error("Telegram startup failed (bot will keep running and retry on next restart): {}", e.getMessage());
        }
    }
}
