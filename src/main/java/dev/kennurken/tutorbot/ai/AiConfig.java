package dev.kennurken.tutorbot.ai;

import dev.kennurken.tutorbot.ai.provider.AiProvider;
import dev.kennurken.tutorbot.ai.provider.FakeAiProvider;
import dev.kennurken.tutorbot.ai.provider.OpenAiCompatibleAiProvider;
import dev.kennurken.tutorbot.ai.provider.ResilientAiProvider;
import java.time.Clock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration
public class AiConfig {

    private static final Logger log = LoggerFactory.getLogger(AiConfig.class);

    /** The single {@link AiProvider} bean the rest of the app sees: resilience wraps the real one. */
    @Bean
    public AiProvider aiProvider(AiProperties properties, RestClient.Builder restClientBuilder, Clock clock) {
        if ("fake".equalsIgnoreCase(properties.provider())) {
            log.warn("AI provider = fake: no real model calls will be made");
            return new FakeAiProvider();
        }
        if (properties.apiKey() == null || properties.apiKey().isBlank()) {
            log.warn("AI_API_KEY is empty: real model calls will fail and fallbacks will be used");
        }
        return new ResilientAiProvider(new OpenAiCompatibleAiProvider(restClientBuilder, properties), properties, clock);
    }
}
