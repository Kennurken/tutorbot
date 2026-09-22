package dev.kennurken.tutorbot.verification;

import dev.kennurken.tutorbot.task.TaskType;
import java.util.List;
import java.util.Map;

/**
 * Questions used when the model is unreachable, so a verification can always start and the
 * user is never blocked by an outage. The evaluation itself still needs the model; without it
 * the answer is kept and evaluated on the next attempt.
 */
public final class FallbackQuestions {

    private static final Map<TaskType, List<String>> EN = Map.of(
            TaskType.THEORY, List.of(
                    "Explain the main idea of \"%s\" in your own words, as if to a colleague.",
                    "Give a concrete example where this applies, and one where it does not.",
                    "What is the most common mistake people make with this, and why?"),
            TaskType.PROGRAMMING, List.of(
                    "Describe what you implemented for \"%s\" and the key design decision you made.",
                    "What happens on invalid input or failure? How is it handled?",
                    "How did you test it, and what would break if the requirements changed?"),
            TaskType.LANGUAGE, List.of(
                    "State the rule for \"%s\" in your own words and give two example sentences.",
                    "Write a sentence that is wrong for this topic and explain why it is wrong.",
                    "When would a learner confuse this with a similar form? Give an example."),
            TaskType.MATH, List.of(
                    "Walk through the solution method for \"%s\" step by step.",
                    "Why is the key step valid? What assumption does it rely on?",
                    "Show an edge case or an alternative method."),
            TaskType.READING, List.of(
                    "Summarise what you read for \"%s\" in 3-5 sentences.",
                    "What was the strongest idea, and do you agree with it? Why?",
                    "Name one specific detail or example from the text."),
            TaskType.PROJECT, List.of(
                    "What exactly did you build for \"%s\" and how does it work?",
                    "What was the hardest part and how did you solve it?",
                    "How did you verify it works? What is still missing?"),
            TaskType.OTHER, List.of(
                    "Describe what you did for \"%s\" and the result.",
                    "What was difficult and what would you do differently?",
                    "What is the next step?"));

    private static final Map<TaskType, List<String>> RU = Map.of(
            TaskType.THEORY, List.of(
                    "Объясни главную идею темы «%s» своими словами, как коллеге.",
                    "Приведи конкретный пример, где это применяется, и один — где нет.",
                    "Какую ошибку чаще всего допускают в этой теме и почему?"),
            TaskType.PROGRAMMING, List.of(
                    "Опиши, что ты реализовал для «%s», и ключевое архитектурное решение.",
                    "Что происходит при невалидном вводе или сбое? Как это обрабатывается?",
                    "Как ты это тестировал и что сломается при изменении требований?"),
            TaskType.LANGUAGE, List.of(
                    "Сформулируй правило по теме «%s» своими словами и приведи два примера предложений.",
                    "Напиши предложение с ошибкой по этой теме и объясни, почему оно неверно.",
                    "С какой похожей формой это путают? Приведи пример."),
            TaskType.MATH, List.of(
                    "Опиши метод решения для «%s» по шагам.",
                    "Почему ключевой шаг корректен? На каком допущении он основан?",
                    "Покажи граничный случай или альтернативный способ."),
            TaskType.READING, List.of(
                    "Перескажи прочитанное по «%s» в 3–5 предложениях.",
                    "Какая идея самая сильная, и согласен ли ты с ней? Почему?",
                    "Назови одну конкретную деталь или пример из текста."),
            TaskType.PROJECT, List.of(
                    "Что именно ты сделал для «%s» и как это работает?",
                    "Что было самым сложным и как ты это решил?",
                    "Как ты проверил, что это работает? Чего ещё не хватает?"),
            TaskType.OTHER, List.of(
                    "Опиши, что ты сделал для «%s», и результат.",
                    "Что было трудно и что бы ты сделал иначе?",
                    "Какой следующий шаг?"));

    private FallbackQuestions() {
    }

    public static String question(TaskType type, int index, String language, String topicOrTitle) {
        Map<TaskType, List<String>> bank = "ru".equals(language) ? RU : EN;
        List<String> list = bank.getOrDefault(type, bank.get(TaskType.OTHER));
        String template = list.get(Math.min(index, list.size() - 1));
        return String.format(template, topicOrTitle);
    }
}
