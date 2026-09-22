package dev.kennurken.tutorbot.notification;

import dev.kennurken.tutorbot.messaging.InlineKeyboard;
import dev.kennurken.tutorbot.telegram.api.TelegramApiException;
import dev.kennurken.tutorbot.telegram.api.TelegramClient;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.Message;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.user.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Outbox writer + dispatcher. Delivery is at-least-once: a row is leased (attempts++,
 * nextAttemptAt = now + lease) in one transaction, sent with no transaction open, then marked
 * SENT in a second transaction. A crash in between re-sends after the lease expires; that is
 * the accepted trade-off versus losing a reminder.
 */
@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);
    private static final int MAX_ATTEMPTS = 5;
    private static final Duration LEASE = Duration.ofMinutes(2);
    private static final Duration MAX_LATENESS = Duration.ofHours(6);

    private final NotificationRepository notifications;
    private final UserRepository users;
    private final TelegramClient telegram;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final Clock clock;

    public NotificationService(NotificationRepository notifications, UserRepository users, TelegramClient telegram,
                               TransactionTemplate tx, JsonMapper json, Clock clock) {
        this.notifications = notifications;
        this.users = users;
        this.telegram = telegram;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
    }

    /** Queue a message. {@code dedupeKey} (nullable) makes re-scheduling the same reminder a no-op. */
    @Transactional
    public void schedule(Long userId, Long taskId, NotificationKind kind, Instant at, String html,
                         InlineKeyboard keyboard, String dedupeKey) {
        if (dedupeKey != null && notifications.existsByDedupeKey(dedupeKey)) {
            return;
        }
        String markup = keyboard == null ? null : json.writeValueAsString(keyboardJson(keyboard));
        try {
            notifications.save(new Notification(userId, taskId, kind, dedupeKey, html, markup, at));
        } catch (DataIntegrityViolationException duplicate) {
            log.debug("Notification {} already queued", dedupeKey);
        }
    }

    public void sendNow(User user, NotificationKind kind, String html, InlineKeyboard keyboard) {
        schedule(user.getId(), null, kind, clock.instant(), html, keyboard, null);
    }

    @Transactional
    public int cancelPendingForTask(Long taskId) {
        return notifications.cancelPendingForTask(taskId);
    }

    /** Called from the scheduler tick. Returns the number of messages handed to Telegram. */
    public int dispatchDue() {
        Instant now = clock.instant();
        List<Notification> due = notifications.findDue(now);
        int sent = 0;
        for (Notification candidate : due) {
            Notification leased = tx.execute(status -> lease(candidate.getId(), now));
            if (leased == null) {
                continue;
            }
            if (deliver(leased)) {
                sent++;
            }
        }
        return sent;
    }

    private Notification lease(Long id, Instant now) {
        Notification n = notifications.findById(id).orElse(null);
        if (n == null || n.getStatus() != NotificationStatus.SCHEDULED
                || (n.getNextAttemptAt() != null && n.getNextAttemptAt().isAfter(now))) {
            return null;
        }
        User user = users.findById(n.getUserId()).orElse(null);
        if (user == null) {
            n.cancel();
            return null;
        }
        Instant effectiveDue = n.getNextAttemptAt() != null && n.getNextAttemptAt().isAfter(n.getScheduledAt())
                ? n.getNextAttemptAt() : n.getScheduledAt();
        boolean tooLate = effectiveDue.plus(MAX_LATENESS).isBefore(now) && n.getKind().isAccountabilityNudge();
        if (tooLate || (user.isPaused(now) && n.getKind().isAccountabilityNudge())) {
            n.cancel();
            notifications.save(n);
            return null;
        }
        if (n.getKind().isDeferrableInQuietHours() && user.getSettings().isQuietAt(now, user.zone())) {
            n.defer(user.getSettings().quietWindowEnd(now, user.zone()));
            notifications.save(n);
            return null;
        }
        n.lease(now.plus(LEASE));
        return notifications.save(n);
    }

    private boolean deliver(Notification n) {
        User user = users.findById(n.getUserId()).orElseThrow();
        try {
            Message message = telegram.sendMessage(user.getChatId(), n.getText(), parseMarkup(n.getReplyMarkup()));
            tx.executeWithoutResult(status -> notifications.findById(n.getId()).ifPresent(row -> {
                row.markSent(clock.instant(), message == null ? null : message.messageId());
                notifications.save(row);
            }));
            return true;
        } catch (TelegramApiException e) {
            handleFailure(n, e);
            return false;
        } catch (RuntimeException e) {
            log.error("Unexpected error delivering notification {}", n.getId(), e);
            handleFailure(n, new TelegramApiException(e.getMessage(), true, 0, e));
            return false;
        }
    }

    private void handleFailure(Notification n, TelegramApiException e) {
        tx.executeWithoutResult(status -> notifications.findById(n.getId()).ifPresent(row -> {
            if (!e.isRetryable() || row.getAttempts() >= MAX_ATTEMPTS) {
                row.markFailed(truncate(e.getMessage()));
                log.warn("Notification {} failed permanently: {}", row.getId(), e.getMessage());
            } else {
                long backoffSeconds = Math.max(e.getRetryAfterSeconds(), 30L * (1L << Math.min(row.getAttempts(), 4)));
                row.retryLater(clock.instant().plusSeconds(backoffSeconds), truncate(e.getMessage()));
            }
            notifications.save(row);
        }));
    }

    private InlineKeyboard parseMarkup(String markup) {
        if (markup == null) {
            return null;
        }
        Map<?, ?> parsed = json.readValue(markup, Map.class);
        Object rows = parsed.get("rows");
        if (!(rows instanceof List<?> rowList)) {
            return null;
        }
        InlineKeyboard.Builder b = InlineKeyboard.builder();
        for (Object row : rowList) {
            List<?> buttons = (List<?>) row;
            InlineKeyboard.Button[] arr = buttons.stream()
                    .map(o -> (Map<?, ?>) o)
                    .map(m -> InlineKeyboard.btn(String.valueOf(m.get("text")), String.valueOf(m.get("callbackData"))))
                    .toArray(InlineKeyboard.Button[]::new);
            b.row(arr);
        }
        return b.build();
    }

    private static Map<String, Object> keyboardJson(InlineKeyboard keyboard) {
        return Map.of("rows", keyboard.rows().stream()
                .map(row -> row.stream().map(btn -> Map.of("text", btn.text(), "callbackData", btn.callbackData())).toList())
                .toList());
    }

    private static String truncate(String s) {
        return s != null && s.length() > 480 ? s.substring(0, 480) : s;
    }
}
