package dev.kennurken.tutorbot.telegram.handler.callback;

import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.telegram.flow.TaskFlows;
import dev.kennurken.tutorbot.telegram.handler.CallbackContext;
import dev.kennurken.tutorbot.telegram.handler.CallbackHandler;
import org.springframework.stereotype.Component;

@Component
public class SkipCallback implements CallbackHandler {

    private final TaskFlows flows;

    public SkipCallback(TaskFlows flows) {
        this.flows = flows;
    }

    @Override
    public String action() {
        return Callbacks.SKIP;
    }

    @Override
    public void handle(CallbackContext ctx) {
        flows.askSkipCategory(ctx.user(), ctx.longArg(0), ctx.reply());
    }
}
