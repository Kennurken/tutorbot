package dev.kennurken.tutorbot.telegram.handler;

public interface CallbackHandler {

    String action();

    void handle(CallbackContext ctx);
}
