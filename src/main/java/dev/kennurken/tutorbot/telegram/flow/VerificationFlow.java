package dev.kennurken.tutorbot.telegram.flow;

import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Html;
import dev.kennurken.tutorbot.messaging.Keyboards;
import dev.kennurken.tutorbot.messaging.TaskFormatter;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.telegram.Replier;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.verification.VerificationPolicy;
import dev.kennurken.tutorbot.verification.VerificationService;
import java.util.List;
import org.springframework.stereotype.Component;

/** Chat-side rendering of the verification loop: questions in, verdicts out. */
@Component
public class VerificationFlow {

    private final VerificationService verification;
    private final BotMessages msg;
    private final TaskFormatter fmt;
    private final Keyboards keyboards;

    public VerificationFlow(VerificationService verification, BotMessages msg, TaskFormatter fmt, Keyboards keyboards) {
        this.verification = verification;
        this.msg = msg;
        this.fmt = fmt;
        this.keyboards = keyboards;
    }

    public void start(User user, Task task, Replier reply) {
        VerificationService.StartResult result = verification.start(user, task);
        StringBuilder sb = new StringBuilder(msg.get(user, "verification.start", fmt.header(task))).append("\n\n");
        if (!result.aiAvailable()) {
            sb.append(msg.get(user, "verification.fallback")).append("\n\n");
        }
        sb.append(question(user, 1, result.session().getMaxQuestions(), result.question()));
        reply.send(sb.toString());
    }

    public void answer(User user, String text, Replier reply) {
        VerificationService.AnswerResult r = verification.answer(user, text);
        switch (r.kind()) {
            case TOO_SHORT -> reply.send(msg.get(user, "verification.tooshort") + "\n\n"
                    + question(user, r.questionNo(), r.maxQuestions(), r.nextQuestion()));
            case AI_UNAVAILABLE -> reply.send(msg.get(user, "verification.unavailable"));
            case NEXT_QUESTION -> {
                StringBuilder sb = new StringBuilder();
                if (r.feedback() != null && !r.feedback().isBlank()) {
                    sb.append(Html.esc(r.feedback())).append("\n\n");
                }
                sb.append(question(user, r.questionNo(), r.maxQuestions(), r.nextQuestion()));
                reply.send(sb.toString());
            }
            case FINISHED -> finished(user, r, reply);
        }
    }

    private void finished(User user, VerificationService.AnswerResult r, Replier reply) {
        VerificationPolicy.Verdict v = r.verdict();
        Task task = r.task();
        StringBuilder sb = new StringBuilder();
        if (r.feedback() != null && !r.feedback().isBlank()) {
            sb.append(Html.esc(r.feedback())).append("\n\n");
        }
        String key = switch (v.status()) {
            case PASSED -> "verification.pass";
            case FAILED -> "verification.fail";
            default -> "verification.uncertain";
        };
        sb.append(msg.get(user, key, fmt.header(task), Math.round(v.score() * 100), Math.round(v.confidence() * 100)));
        if (v.summary() != null && !v.summary().isBlank()) {
            sb.append("\n").append(Html.esc(v.summary()));
        }
        appendList(sb, msg.get(user, "verification.strengths"), v.strengths());
        appendList(sb, msg.get(user, "verification.gaps"), v.knowledgeGaps());
        if (v.status() == dev.kennurken.tutorbot.verification.SessionStatus.PASSED) {
            reply.send(sb.toString());
        } else {
            reply.send(sb.toString(), keyboards.verificationFailed(user, task));
        }
    }

    private static void appendList(StringBuilder sb, String title, List<String> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        sb.append("\n\n").append(title).append("\n");
        for (String item : items) {
            sb.append("• ").append(Html.esc(item)).append("\n");
        }
    }

    private String question(User user, int no, int max, String text) {
        return msg.get(user, "verification.question", no, max) + "\n" + Html.esc(text);
    }
}
