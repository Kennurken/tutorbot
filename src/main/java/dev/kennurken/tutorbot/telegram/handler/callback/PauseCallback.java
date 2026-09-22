package dev.kennurken.tutorbot.telegram.handler.callback;

import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.telegram.flow.PauseFlow;
import dev.kennurken.tutorbot.telegram.handler.CallbackContext;
import dev.kennurken.tutorbot.telegram.handler.CallbackHandler;
import org.springframework.stereotype.Component;

@Component
public class PauseCallback implements CallbackHandler {

    private final PauseFlow pauseFlow;

    public PauseCallback(PauseFlow pauseFlow) {
        this.pauseFlow = pauseFlow;
    }

    @Override
    public String action() {
        return Callbacks.PAUSE;
    }

    @Override
    public void handle(CallbackContext ctx) {
        pauseFlow.apply(ctx.user(), ctx.arg(0), ctx.reply());
    }
}
