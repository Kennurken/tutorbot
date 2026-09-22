package dev.kennurken.tutorbot.messaging;

/** Telegram HTML parse mode: user-supplied text must be escaped before being embedded. */
public final class Html {

    private Html() {
    }

    public static String esc(String s) {
        if (s == null) {
            return "";
        }
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String b(String s) {
        return "<b>" + esc(s) + "</b>";
    }

    public static String code(String s) {
        return "<code>" + esc(s) + "</code>";
    }
}
