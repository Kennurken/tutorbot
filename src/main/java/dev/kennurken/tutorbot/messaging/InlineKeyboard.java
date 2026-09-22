package dev.kennurken.tutorbot.messaging;

import java.util.ArrayList;
import java.util.List;

/** Transport-neutral inline keyboard; the Telegram client serialises it. */
public record InlineKeyboard(List<List<Button>> rows) {

    public record Button(String text, String callbackData) {
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final List<List<Button>> rows = new ArrayList<>();

        public Builder row(Button... buttons) {
            rows.add(List.of(buttons));
            return this;
        }

        public InlineKeyboard build() {
            return new InlineKeyboard(List.copyOf(rows));
        }
    }

    public static Button btn(String text, String callbackData) {
        return new Button(text, callbackData);
    }
}
