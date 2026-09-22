package dev.kennurken.tutorbot.telegram.handler.callback;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.messaging.Keyboards;
import dev.kennurken.tutorbot.messaging.TaskFormatter;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.telegram.handler.CallbackContext;
import dev.kennurken.tutorbot.telegram.handler.CallbackHandler;
import org.springframework.stereotype.Component;

/**
 * [Review material]: the task stays pending verification; the user re-reads and comes back
 * with [Verify now]. No new task is created (the consequence engine may already have added a
 * review task for tomorrow).
 */
@Component
public class ReviewMaterialCallback implements CallbackHandler {

    private final TaskService tasks;
    private final TaskFormatter fmt;
    private final Keyboards keyboards;
    private final BotMessages msg;

    public ReviewMaterialCallback(TaskService tasks, TaskFormatter fmt, Keyboards keyboards, BotMessages msg) {
        this.tasks = tasks;
        this.fmt = fmt;
        this.keyboards = keyboards;
        this.msg = msg;
    }

    @Override
    public String action() {
        return Callbacks.REVIEW_MATERIAL;
    }

    @Override
    public void handle(CallbackContext ctx) {
        Task task = tasks.retryVerification(ctx.user(), ctx.longArg(0));
        ctx.reply().send(msg.get(ctx.user(), "verification.review_material", fmt.header(task)),
                keyboards.verificationPending(ctx.user(), task));
    }
}
