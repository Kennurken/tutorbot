package dev.kennurken.tutorbot.task.event;

import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskStatus;

/**
 * In-process Spring event published after every task transition. Other modules
 * (notification, consequence, knowledge) react to it without the task module knowing them.
 * Listeners run synchronously inside the same transaction, so a failing listener rolls back
 * the transition too: consistent by construction.
 */
public record TaskStatusChanged(Task task, TaskStatus from, TaskStatus to, Actor actor) {
}
