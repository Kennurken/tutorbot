package dev.kennurken.tutorbot.user;

/** The subset of Telegram's user object the domain cares about. */
public record TelegramIdentity(long telegramUserId, long chatId, String firstName, String username,
                               String languageCode) {
}
