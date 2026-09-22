package dev.kennurken.tutorbot.telegram.handler.callback;

import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.telegram.flow.TaskFlows;
import dev.kennurken.tutorbot.telegram.handler.CallbackContext;
import dev.kennurken.tutorbot.telegram.handler.CallbackHandler;
import org.springframework.stereotype.Component;

@Component
public class TaskConfirmCallback implements CallbackHandler {

    private final TaskFlows flows;

    public TaskConfirmCallback(TaskFlows flows) {
        this.flows = flows;
    }

    @Override
    public String action() {
        return Callbacks.TASK_CONFIRM;
    }

    @Override
    public void handle(CallbackContext ctx) {
        flows.confirmDraft(ctx.user(), "yes".equals(ctx.arg(0)), ctx.reply());
    }
}
