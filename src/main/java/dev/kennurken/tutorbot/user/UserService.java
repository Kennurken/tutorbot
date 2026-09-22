package dev.kennurken.tutorbot.user;

import dev.kennurken.tutorbot.common.DomainException;
import dev.kennurken.tutorbot.common.NotFoundException;
import dev.kennurken.tutorbot.common.config.AppProperties;
import java.time.Clock;
import java.time.DateTimeException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of("ru", "en");

    private final UserRepository users;
    private final AppProperties appProperties;
    private final Clock clock;

    public UserService(UserRepository users, AppProperties appProperties, Clock clock) {
        this.users = users;
        this.appProperties = appProperties;
        this.clock = clock;
    }

    /** Registers the Telegram user on first contact; refreshes display data afterwards. */
    @Transactional
    public User getOrRegister(TelegramIdentity identity) {
        return users.findByTelegramUserId(identity.telegramUserId())
                .map(existing -> refresh(existing, identity))
                .orElseGet(() -> register(identity));
    }

    private User refresh(User user, TelegramIdentity identity) {
        user.setFirstName(identity.firstName());
        user.setUsername(identity.username());
        return user;
    }

    private User register(TelegramIdentity identity) {
        String language = identity.languageCode() != null && SUPPORTED_LANGUAGES.contains(identity.languageCode())
                ? identity.languageCode()
                : appProperties.defaultLanguage();
        User user = new User(identity.telegramUserId(), identity.chatId(), identity.firstName(),
                identity.username(), language, appProperties.defaultTimezone());
        return users.save(user);
    }

    @Transactional(readOnly = true)
    public User get(Long id) {
        return users.findById(id).orElseThrow(() -> new NotFoundException("User", id));
    }

    @Transactional(readOnly = true)
    public List<User> findAll() {
        return users.findAll();
    }

    @Transactional
    public void setTimezone(User user, String zoneId) {
        try {
            ZoneId.of(zoneId);
        } catch (DateTimeException e) {
            throw new DomainException("Unknown timezone: " + zoneId);
        }
        user.setTimezone(zoneId);
        users.save(user);
    }

    @Transactional
    public void setLanguage(User user, String language) {
        if (!SUPPORTED_LANGUAGES.contains(language)) {
            throw new DomainException("Supported languages: ru, en");
        }
        user.setLanguage(language);
        users.save(user);
    }

    @Transactional
    public void setMode(User user, StrictnessMode mode) {
        user.setMode(mode);
        users.save(user);
    }

    @Transactional
    public void pause(User user, Duration duration) {
        user.setPausedUntil(clock.instant().plus(duration));
        users.save(user);
    }

    @Transactional
    public void resume(User user) {
        user.setPausedUntil(null);
        users.save(user);
    }

    @Transactional
    public void enterRecoveryMode(User user, Duration duration) {
        user.setRecoveryModeUntil(clock.instant().plus(duration));
        users.save(user);
    }

    @Transactional
    public User save(User user) {
        return users.save(user);
    }

    public Instant now() {
        return clock.instant();
    }
}
