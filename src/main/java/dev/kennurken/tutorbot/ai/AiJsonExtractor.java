package dev.kennurken.tutorbot.ai;

/**
 * Models sometimes wrap JSON in prose or code fences even in JSON mode. This pulls out the
 * outermost object; anything else is rejected by the parser and validator afterwards.
 */
public final class AiJsonExtractor {

    private AiJsonExtractor() {
    }

    public static String extractObject(String raw) {
        if (raw == null) {
            throw new AiUnavailableException("Empty model output");
        }
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            text = firstNewline > 0 ? text.substring(firstNewline + 1) : text.substring(3);
            int fence = text.lastIndexOf("```");
            if (fence >= 0) {
                text = text.substring(0, fence);
            }
            text = text.trim();
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new AiUnavailableException("No JSON object in model output");
        }
        return text.substring(start, end + 1);
    }
}
