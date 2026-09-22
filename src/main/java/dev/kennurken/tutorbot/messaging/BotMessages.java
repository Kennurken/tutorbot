package dev.kennurken.tutorbot.messaging;

import dev.kennurken.tutorbot.user.StrictnessMode;
import dev.kennurken.tutorbot.user.User;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.stereotype.Component;

/**
 * Looks up user-facing strings by key, language and (for a few high-frequency messages)
 * strictness mode: key "task.start" resolves to "task.start.hardcore" first, then "task.start".
 * Tone lives in resource bundles; rules live in code.
 */
@Component
public class BotMessages {

    private final MessageSource messages;

    public BotMessages(MessageSource messages) {
        this.messages = messages;
    }

    public String get(User user, String key, Object... args) {
        return get(user.getLanguage(), user.getMode(), key, args);
    }

    public String get(String language, StrictnessMode mode, String key, Object... args) {
        Locale locale = Locale.forLanguageTag(language == null ? "en" : language);
        if (mode != null && mode != StrictnessMode.NORMAL) {
            try {
                return messages.getMessage(key + "." + mode.name().toLowerCase(Locale.ROOT), args, locale);
            } catch (NoSuchMessageException ignored) {
                // fall through to the neutral wording
            }
        }
        return messages.getMessage(key, args, locale);
    }
}
