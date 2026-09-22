package dev.kennurken.tutorbot.telegram.flow;

import dev.kennurken.tutorbot.common.DomainException;
import dev.kennurken.tutorbot.common.time.TimeFormats;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.telegram.Replier;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.user.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class PauseFlow {

    private final UserService users;
    private final BotMessages msg;
    private final Clock clock;

    public PauseFlow(UserService users, BotMessages msg, Clock clock) {
        this.users = users;
        this.msg = msg;
        this.clock = clock;
    }

    public void apply(User user, String preset, Replier reply) {
        Instant now = clock.instant();
        switch (preset.toLowerCase(Locale.ROOT)) {
            case "off", "resume" -> {
                users.resume(user);
                reply.send(msg.get(user, "pause.off"));
                return;
            }
            case "1h" -> users.pause(user, Duration.ofHours(1));
            case "24h" -> users.pause(user, Duration.ofHours(24));
            case "today" -> {
                ZonedDateTime endOfDay = now.atZone(user.zone()).toLocalDate().plusDays(1).atStartOfDay(user.zone());
                users.pause(user, Duration.between(now, endOfDay.toInstant()));
            }
            default -> {
                Duration d = parse(preset);
                if (d == null) {
                    throw new DomainException(msg.get(user, "pause.usage"));
                }
                users.pause(user, d);
            }
        }
        reply.send(msg.get(user, "pause.set", TimeFormats.smart(user.getPausedUntil(), user.zone(), now)));
    }

    private static Duration parse(String s) {
        try {
            if (s.endsWith("h")) {
                return Duration.ofHours(Long.parseLong(s.substring(0, s.length() - 1)));
            }
            if (s.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(s.substring(0, s.length() - 1)));
            }
            if (s.endsWith("d")) {
                return Duration.ofDays(Long.parseLong(s.substring(0, s.length() - 1)));
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return null;
    }
}
