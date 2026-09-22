package dev.kennurken.tutorbot.ai;

/** A typed request: which prompt (and version), for whom, and what shape the answer must have. */
public record AiCall<T>(
        String purpose,
        String promptVersion,
        String systemPrompt,
        String userPrompt,
        Class<T> responseType,
        Long userId,
        int maxTokens,
        double temperature) {

    public static <T> AiCall<T> of(String purpose, String promptVersion, String systemPrompt, String userPrompt,
                                   Class<T> responseType, Long userId) {
        return new AiCall<>(purpose, promptVersion, systemPrompt, userPrompt, responseType, userId, 2500, 0.2);
    }
}
