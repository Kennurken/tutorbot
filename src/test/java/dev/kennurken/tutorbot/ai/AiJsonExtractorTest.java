package dev.kennurken.tutorbot.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AiJsonExtractorTest {

    @Test
    void stripsCodeFences() {
        String raw = "```json\n{\"a\": 1}\n```";
        assertThat(AiJsonExtractor.extractObject(raw)).isEqualTo("{\"a\": 1}");
    }

    @Test
    void ignoresProseAroundTheObject() {
        String raw = "Sure! Here is the JSON:\n{\"verdict\":\"PASS\"}\nHope this helps.";
        assertThat(AiJsonExtractor.extractObject(raw)).isEqualTo("{\"verdict\":\"PASS\"}");
    }

    @Test
    void rejectsOutputWithoutAnObject() {
        assertThatThrownBy(() -> AiJsonExtractor.extractObject("I cannot do that")).isInstanceOf(AiUnavailableException.class);
        assertThatThrownBy(() -> AiJsonExtractor.extractObject(null)).isInstanceOf(AiUnavailableException.class);
    }
}
