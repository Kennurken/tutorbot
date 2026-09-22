package dev.kennurken.tutorbot.knowledge;

import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.verification.SessionStatus;
import dev.kennurken.tutorbot.verification.VerificationFinished;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Feeds verification outcomes into the knowledge profile. UNCERTAIN verdicts are not evidence. */
@Component
public class KnowledgeEventListener {

    private final KnowledgeService knowledge;

    public KnowledgeEventListener(KnowledgeService knowledge) {
        this.knowledge = knowledge;
    }

    @EventListener
    public void onVerificationFinished(VerificationFinished event) {
        if (event.outcome() == SessionStatus.UNCERTAIN) {
            return;
        }
        Task task = event.task();
        String topic = task.getTopic() != null && !task.getTopic().isBlank() ? task.getTopic() : task.getTitle();
        knowledge.recordAssessment(task.getUserId(), task.getSubject(), topic, event.score(), event.confidence());
    }
}
