package dev.kennurken.tutorbot.telegram.api;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/** The handful of Bot API objects this bot reads. Everything else is ignored on purpose. */
public final class TelegramTypes {

    private TelegramTypes() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Update(@JsonProperty("update_id") long updateId,
                         Message message,
                         @JsonProperty("callback_query") CallbackQuery callbackQuery) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(@JsonProperty("message_id") long messageId,
                          TgUser from,
                          Chat chat,
                          String text,
                          long date) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TgUser(long id,
                         @JsonProperty("is_bot") boolean bot,
                         @JsonProperty("first_name") String firstName,
                         String username,
                         @JsonProperty("language_code") String languageCode) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(long id, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackQuery(String id, TgUser from, Message message, String data) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiResponse<T>(boolean ok, T result, String description,
                                 @JsonProperty("error_code") Integer errorCode,
                                 ResponseParameters parameters) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ResponseParameters(@JsonProperty("retry_after") Integer retryAfter) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record BotCommand(String command, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record UpdateList(List<Update> updates) {
    }
}
