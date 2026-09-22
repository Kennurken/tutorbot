package dev.kennurken.tutorbot.verification;

import dev.kennurken.tutorbot.task.Task;
import java.util.List;

/** Published after a session ends with a verdict. Knowledge and consequence modules listen. */
public record VerificationFinished(Task task, SessionStatus outcome, double score, double confidence,
                                   List<String> knowledgeGaps, List<String> strengths, String summary) {
}
