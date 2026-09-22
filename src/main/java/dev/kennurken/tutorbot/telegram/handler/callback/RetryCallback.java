package dev.kennurken.tutorbot.telegram.handler.callback;

import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.task.TaskService;
import dev.kennurken.tutorbot.telegram.flow.VerificationFlow;
import dev.kennurken.tutorbot.telegram.handler.CallbackContext;
import dev.kennurken.tutorbot.telegram.handler.CallbackHandler;
import org.springframework.stereotype.Component;

/** [Retry] after a failed / uncertain / expired verification: a new session for the same task. */
@Component
public class RetryCallback implements CallbackHandler {

    private final TaskService tasks;
    private final VerificationFlow verificationFlow;

    public RetryCallback(TaskService tasks, VerificationFlow verificationFlow) {
        this.tasks = tasks;
        this.verificationFlow = verificationFlow;
    }

    @Override
    public String action() {
        return Callbacks.RETRY;
    }

    @Override
    public void handle(CallbackContext ctx) {
        Task task = tasks.requireOwned(ctx.user(), ctx.longArg(0));
        verificationFlow.start(ctx.user(), task, ctx.reply());
    }
}
