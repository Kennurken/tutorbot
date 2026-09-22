package dev.kennurken.tutorbot.ai.provider;

/**
 * The only thing the application knows about LLMs. Implementations: the OpenAI-compatible
 * gateway (Vercel AI Gateway -> Grok) and a deterministic fake for tests and offline dev.
 */
public interface AiProvider {

    AiResponse complete(AiRequest request);

    String name();
}
