package dev.kennurken.tutorbot.ai.provider;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Deterministic stand-in used in tests and when {@code ai.provider=fake}. It reads a few
 * markers the prompts always contain (answered/max question counters, the last answer) so
 * the verification loop can be exercised end-to-end without network access.
 */
public class FakeAiProvider implements AiProvider {

    private static final Pattern ANSWERED = Pattern.compile("answered_questions:\\s*(\\d+)");
    private static final Pattern MIN = Pattern.compile("min_questions:\\s*(\\d+)");
    private static final Pattern LAST_ANSWER = Pattern.compile("LAST_ANSWER:\\s*(.*?)\\s*END_LAST_ANSWER", Pattern.DOTALL);
    /** Padding without substance, the classic anti-gaming case; scored low like a real examiner would. */
    private static final Pattern VAGUE = Pattern.compile(
            "understood everything|trust me|nothing to add|всё понял|все понял|я всё знаю", Pattern.CASE_INSENSITIVE);

    @Override
    public String name() {
        return "fake";
    }

    @Override
    public AiResponse complete(AiRequest request) {
        String content = switch (request.purpose()) {
            case "verification_step" -> verificationStep(request.userPrompt());
            case "task_intent" -> "{\"intent\":\"UNKNOWN\",\"confidence\":0.2,\"task\":null}";
            case "weekly_review" -> """
                    {"summary":"Fake weekly summary.","observations":["Fake observation."],
                     "recommendations":[{"change":"Keep sessions under 45 minutes","reason":"Fake reason"}]}""";
            case "goal_plan" -> """
                    {"summary":"Fake path.","steps":[
                      {"title":"Step one: basics","subject":"Java","topic":"basics","type":"THEORY","minutes":30},
                      {"title":"Step two: practice","subject":"Java","topic":"practice","type":"PROGRAMMING","minutes":45},
                      {"title":"Step three: mini project","subject":"Java","topic":"project","type":"PROJECT","minutes":60}]}""";
            case "skip_analysis" -> "{\"category\":\"OTHER\",\"insight\":\"Fake insight\",\"suggestion\":\"Fake suggestion\"}";
            default -> "{}";
        };
        return new AiResponse(content, "fake-model", 10, 10, 1);
    }

    private String verificationStep(String prompt) {
        int answered = intOf(ANSWERED, prompt, 0);
        int min = intOf(MIN, prompt, 2);
        Matcher m = LAST_ANSWER.matcher(prompt);
        String answer = m.find() ? m.group(1) : "";
        int words = answer.isBlank() ? 0 : answer.trim().split("\\s+").length;
        boolean substantive = words >= 8 && !VAGUE.matcher(answer).find();
        double score = substantive ? 0.85 : 0.2;
        boolean finish = answered + 1 >= min; // this answer counts
        if (!finish) {
            return """
                    {"evaluation":{"score":%s,"feedback":"Fake feedback.","evidenceOfUnderstanding":%s},
                     "decision":"CONTINUE","nextQuestion":"Fake follow-up question %d?","difficultyDelta":0,"finalVerdict":null}
                    """.formatted(score, substantive, answered + 1);
        }
        String verdict = score >= 0.7 ? "PASS" : "FAIL";
        return """
                {"evaluation":{"score":%s,"feedback":"Fake feedback.","evidenceOfUnderstanding":%s},
                 "decision":"FINISH","nextQuestion":null,"difficultyDelta":0,
                 "finalVerdict":{"verdict":"%s","score":%s,"confidence":0.8,"knowledgeGaps":[],"strengths":["fake"],"summary":"Fake summary."}}
                """.formatted(score, substantive, verdict, score);
    }

    private static int intOf(Pattern p, String text, int fallback) {
        Matcher m = p.matcher(text);
        return m.find() ? Integer.parseInt(m.group(1)) : fallback;
    }
}
