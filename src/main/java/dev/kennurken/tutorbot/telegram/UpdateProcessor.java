package dev.kennurken.tutorbot.telegram;

import dev.kennurken.tutorbot.common.DomainException;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Html;
import dev.kennurken.tutorbot.telegram.api.TelegramClient;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.TgUser;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.Update;
import dev.kennurken.tutorbot.user.TelegramIdentity;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.user.UserService;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Entry point for every update regardless of transport (webhook or long polling):
 * idempotency check -> allowlist -> user registration -> dispatch -> error translation.
 * Business exceptions become polite replies; anything else is logged with the update id.
 */
@Component
public class UpdateProcessor {

    private static final Logger log = LoggerFactory.getLogger(UpdateProcessor.class);

    private final UpdateDeduplicator deduplicator;
    private final UpdateDispatcher dispatcher;
    private final UserService userService;
    private final TelegramClient client;
    private final TelegramProperties properties;
    private final BotMessages msg;

    public UpdateProcessor(UpdateDeduplicator deduplicator, UpdateDispatcher dispatcher, UserService userService,
                           TelegramClient client, TelegramProperties properties, BotMessages msg) {
        this.deduplicator = deduplicator;
        this.dispatcher = dispatcher;
        this.userService = userService;
        this.client = client;
        this.properties = properties;
        this.msg = msg;
    }

    /** Webhook path: Telegram wants a fast 200, model calls take seconds, so processing is detached. */
    @Async
    public void processAsync(Update update) {
        process(update);
    }

    public void process(Update update) {
        if (!deduplicator.firstTime(update.updateId())) {
            log.debug("Duplicate update {} ignored", update.updateId());
            return;
        }
        TgUser from = update.message() != null ? update.message().from()
                : update.callbackQuery() != null ? update.callbackQuery().from() : null;
        if (from == null || from.bot()) {
            return;
        }
        long chatId = update.message() != null ? update.message().chat().id()
                : update.callbackQuery() != null && update.callbackQuery().message() != null
                ? update.callbackQuery().message().chat().id() : from.id();

        Set<Long> allowed = properties.allowedUsers();
        if (!allowed.isEmpty() && !allowed.contains(from.id())) {
            log.info("Rejected update from non-allowlisted user {}", from.id());
            safeSend(chatId, msg.get("en", null, "error.notallowed"));
            return;
        }

        User user;
        try {
            user = userService.getOrRegister(new TelegramIdentity(from.id(), chatId, from.firstName(), from.username(),
                    from.languageCode()));
        } catch (RuntimeException e) {
            log.error("Could not register user {} for update {}", from.id(), update.updateId(), e);
            return;
        }

        try {
            if (update.message() != null) {
                dispatcher.onMessage(user, update.message());
            } else if (update.callbackQuery() != null) {
                dispatcher.onCallback(user, update.callbackQuery());
            }
        } catch (DomainException e) {
            safeSend(chatId, "⚠️ " + Html.esc(e.getMessage()));
        } catch (RuntimeException e) {
            log.error("Update {} failed", update.updateId(), e);
            safeSend(chatId, msg.get(user, "error.generic"));
        }
    }

    private void safeSend(long chatId, String html) {
        try {
            client.sendMessage(chatId, html, null);
        } catch (RuntimeException e) {
            log.warn("Could not send error reply to chat {}: {}", chatId, e.getMessage());
        }
    }
}
