package dev.kennurken.tutorbot.telegram;

import dev.kennurken.tutorbot.messaging.InlineKeyboard;
import dev.kennurken.tutorbot.telegram.api.TelegramClient;

/** Sends replies to one chat. Handlers never touch the raw client, so they stay testable. */
public class Replier {

    private final TelegramClient client;
    private final long chatId;

    public Replier(TelegramClient client, long chatId) {
        this.client = client;
        this.chatId = chatId;
    }

    public void send(String html) {
        client.sendMessage(chatId, html, null);
    }

    public void send(String html, InlineKeyboard keyboard) {
        client.sendMessage(chatId, html, keyboard);
    }

    public long chatId() {
        return chatId;
    }
}
