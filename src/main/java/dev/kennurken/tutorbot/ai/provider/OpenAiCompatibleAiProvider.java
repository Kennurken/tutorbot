package dev.kennurken.tutorbot.ai.provider;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import dev.kennurken.tutorbot.ai.AiProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Talks the OpenAI chat-completions dialect, which Vercel AI Gateway exposes for every model
 * it routes (Grok included). Switching to OpenAI, Anthropic-via-gateway or a local
 * OpenAI-compatible server is a configuration change, not a code change.
 */
public class OpenAiCompatibleAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleAiProvider.class);

    private final RestClient client;
    private final AiProperties properties;

    public OpenAiCompatibleAiProvider(RestClient.Builder builder, AiProperties properties) {
        this.properties = properties;
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.timeout());
        this.client = builder
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Bearer " + properties.apiKey())
                .build();
    }

    @Override
    public String name() {
        return "openai-compatible";
    }

    @Override
    public AiResponse complete(AiRequest request) {
        String model = request.modelOverride() != null ? request.modelOverride() : properties.model();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        List<Map<String, String>> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", request.systemPrompt()));
        messages.add(Map.of("role", "user", "content", request.userPrompt()));
        body.put("messages", messages);
        body.put("temperature", request.temperature());
        body.put("max_tokens", request.maxTokens());
        if (request.jsonMode()) {
            body.put("response_format", Map.of("type", "json_object"));
        }

        long started = System.nanoTime();
        try {
            ChatCompletion completion = client.post()
                    .uri("/chat/completions")
                    .body(body)
                    .retrieve()
                    .body(ChatCompletion.class);
            long latencyMs = (System.nanoTime() - started) / 1_000_000;
            if (completion == null || completion.choices() == null || completion.choices().isEmpty()
                    || completion.choices().get(0).message() == null) {
                throw new AiException("Empty completion from " + model, true);
            }
            String content = completion.choices().get(0).message().content();
            Usage usage = completion.usage();
            return new AiResponse(content == null ? "" : content, model,
                    usage == null ? null : usage.promptTokens(),
                    usage == null ? null : usage.completionTokens(),
                    latencyMs);
        } catch (HttpClientErrorException e) {
            HttpStatusCode status = e.getStatusCode();
            boolean retryable = status.value() == 429 || status.value() == 408;
            log.warn("AI call failed: {} {}", status.value(), truncate(e.getResponseBodyAsString()));
            throw new AiException("AI provider returned " + status.value(), retryable, e);
        } catch (HttpServerErrorException e) {
            log.warn("AI provider error: {} {}", e.getStatusCode().value(), truncate(e.getResponseBodyAsString()));
            throw new AiException("AI provider returned " + e.getStatusCode().value(), true, e);
        } catch (ResourceAccessException e) {
            throw new AiException("AI provider unreachable: " + e.getMessage(), true, e);
        }
    }

    private static String truncate(String s) {
        if (s == null) {
            return "";
        }
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record ChatCompletion(List<Choice> choices, Usage usage) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Usage(@JsonProperty("prompt_tokens") Integer promptTokens,
                 @JsonProperty("completion_tokens") Integer completionTokens) {
    }
}
