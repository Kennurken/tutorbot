package dev.kennurken.tutorbot.task;

public enum Priority {
    CRITICAL(5), HIGH(4), MEDIUM(3), LOW(2), OPTIONAL(1);

    private final int weight;

    Priority(int weight) {
        this.weight = weight;
    }

    public int weight() {
        return weight;
    }
}
