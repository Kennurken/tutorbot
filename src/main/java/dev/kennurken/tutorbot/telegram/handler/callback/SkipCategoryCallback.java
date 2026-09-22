package dev.kennurken.tutorbot.telegram.handler.callback;

import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.task.SkipCategory;
import dev.kennurken.tutorbot.telegram.flow.TaskFlows;
import dev.kennurken.tutorbot.telegram.handler.CallbackContext;
import dev.kennurken.tutorbot.telegram.handler.CallbackHandler;
import org.springframework.stereotype.Component;

@Component
public class SkipCategoryCallback implements CallbackHandler {

    private final TaskFlows flows;

    public SkipCategoryCallback(TaskFlows flows) {
        this.flows = flows;
    }

    @Override
    public String action() {
        return Callbacks.SKIP_CATEGORY;
    }

    @Override
    public void handle(CallbackContext ctx) {
        flows.skipWithCategory(ctx.user(), ctx.longArg(0), SkipCategory.valueOf(ctx.arg(1)), ctx.reply());
    }
}
