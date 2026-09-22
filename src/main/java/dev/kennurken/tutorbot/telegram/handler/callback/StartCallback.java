package dev.kennurken.tutorbot.telegram.handler.callback;

import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.telegram.flow.TaskFlows;
import dev.kennurken.tutorbot.telegram.handler.CallbackContext;
import dev.kennurken.tutorbot.telegram.handler.CallbackHandler;
import org.springframework.stereotype.Component;

@Component
public class StartCallback implements CallbackHandler {

    private final TaskFlows flows;

    public StartCallback(TaskFlows flows) {
        this.flows = flows;
    }

    @Override
    public String action() {
        return Callbacks.START;
    }

    @Override
    public void handle(CallbackContext ctx) {
        flows.start(ctx.user(), ctx.longArg(0), ctx.reply());
    }
}
