package dev.kennurken.tutorbot.telegram.handler;

/** One bot command. Registered by name; the dispatcher never contains command logic itself. */
public interface CommandHandler {

    /** Without the leading slash, e.g. "done". */
    String command();

    /** Shown in Telegram's command menu (English; short). */
    String description();

    void handle(CommandContext ctx);

    /** Commands not worth listing in the menu (aliases, admin) return false. */
    default boolean listed() {
        return true;
    }
}
