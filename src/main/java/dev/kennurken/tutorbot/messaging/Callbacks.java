package dev.kennurken.tutorbot.messaging;

/** Callback-data vocabulary shared by keyboards (producers) and callback handlers (consumers). */
public final class Callbacks {

    public static final String START = "start";
    public static final String DONE = "done";
    public static final String SKIP = "skip";
    public static final String SKIP_CATEGORY = "skipcat";
    public static final String RESCHEDULE = "resch";
    public static final String RETRY = "retry";
    public static final String REVIEW_MATERIAL = "review";
    public static final String TASK_CONFIRM = "taskok";
    public static final String PAUSE = "pause";
    public static final String MODE = "mode";
    public static final String ABANDON = "abandon";
    public static final String NOOP = "noop";

    private Callbacks() {
    }

    public static String of(String action, Object... args) {
        StringBuilder sb = new StringBuilder(action);
        for (Object a : args) {
            sb.append(':').append(a);
        }
        return sb.toString();
    }
}
