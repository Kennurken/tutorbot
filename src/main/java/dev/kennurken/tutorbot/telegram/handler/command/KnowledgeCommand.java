package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.knowledge.KnowledgeService;
import dev.kennurken.tutorbot.knowledge.KnowledgeTopic;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Callbacks;
import dev.kennurken.tutorbot.messaging.Html;
import dev.kennurken.tutorbot.messaging.InlineKeyboard;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeCommand implements CommandHandler {

    private static final int MAX_REVIEW_BUTTONS = 5;

    private final KnowledgeService knowledge;
    private final BotMessages msg;

    public KnowledgeCommand(KnowledgeService knowledge, BotMessages msg) {
        this.knowledge = knowledge;
        this.msg = msg;
    }

    @Override
    public String command() {
        return "knowledge";
    }

    @Override
    public String description() {
        return "Knowledge profile per subject and topic";
    }

    @Override
    public void handle(CommandContext ctx) {
        List<KnowledgeTopic> topics = knowledge.profile(ctx.user());
        if (topics.isEmpty()) {
            ctx.reply().send(msg.get(ctx.user(), "knowledge.empty"));
            return;
        }
        StringBuilder sb = new StringBuilder(msg.get(ctx.user(), "knowledge.header")).append("\n");
        InlineKeyboard.Builder reviewButtons = InlineKeyboard.builder();
        int buttons = 0;
        String currentSubject = null;
        for (KnowledgeTopic t : topics) {
            if (!t.getSubject().equals(currentSubject)) {
                currentSubject = t.getSubject();
                sb.append("\n<b>").append(Html.esc(currentSubject)).append("</b>\n");
            }
            double retention = knowledge.retentionNow(t);
            sb.append(" ├ ").append(Html.esc(t.getTopic())).append(": ")
                    .append(Math.round(t.getEstimatedMastery() * 100)).append("%")
                    .append(" (").append(msg.get(ctx.user(), "knowledge.line", Math.round(retention * 100),
                            Math.round(t.getConfidence() * 100), t.getSampleCount())).append(")");
            if (retention < 0.5 && t.getEstimatedMastery() >= 0.3) {
                sb.append(" ").append(msg.get(ctx.user(), "knowledge.review"));
                if (buttons < MAX_REVIEW_BUTTONS) {
                    reviewButtons.row(InlineKeyboard.btn(msg.get(ctx.user(), "btn.review.topic", t.getTopic()),
                            Callbacks.of(Callbacks.REVIEW_TOPIC, t.getId())));
                    buttons++;
                }
            }
            sb.append("\n");
        }
        sb.append("\n").append(msg.get(ctx.user(), "knowledge.legend"));
        ctx.reply().send(sb.toString(), buttons > 0 ? reviewButtons.build() : null);
    }
}
