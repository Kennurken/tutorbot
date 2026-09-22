package dev.kennurken.tutorbot.ai.dto;

import java.util.List;

/** Everything the examiner prompt needs for one turn. Built by the verification module. */
public record VerificationContext(
        String userLanguage,
        String taskTitle,
        String taskType,
        String subject,
        String topic,
        String description,
        List<String> knownGaps,
        int difficulty,
        int minQuestions,
        int maxQuestions,
        int answeredQuestions,
        boolean mustFinishNow,
        boolean mustContinue,
        List<Turn> transcript,
        String lastAnswer) {

    public record Turn(String question, String answer) {
    }
}
