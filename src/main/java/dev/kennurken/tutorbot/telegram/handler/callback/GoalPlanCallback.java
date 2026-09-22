package dev.kennurken.tutorbot.telegram.handler.callback;

import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.telegram.flow.GoalPlanFlow;
import dev.kennurken.tutorbot.telegram.handler.CallbackContext;
import dev.kennurken.tutorbot.telegram.handler.CallbackHandler;
import org.springframework.stereotype.Component;

@Component
public class GoalPlanCallback implements CallbackHandler {

    private final GoalPlanFlow flow;

    public GoalPlanCallback(GoalPlanFlow flow) {
        this.flow = flow;
    }

    @Override
    public String action() {
        return Callbacks.GOAL_PLAN;
    }

    @Override
    public void handle(CallbackContext ctx) {
        flow.confirm(ctx.user(), "yes".equals(ctx.arg(0)), ctx.reply());
    }
}
