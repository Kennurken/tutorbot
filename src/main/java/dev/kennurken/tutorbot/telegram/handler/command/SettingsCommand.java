package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.common.DomainException;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.messaging.Keyboards;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import dev.kennurken.tutorbot.user.StrictnessMode;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.user.UserService;
import java.util.Locale;
import org.springframework.stereotype.Component;

/**
 * /settings — show; /settings mode strict | timezone Asia/Almaty | lang en | limit 5 |
 * minutes 180 | consequences on/off | extra 10 | max_extra 30 | reason on/off | questions 4 | morning 08:00
 */
@Component
public class SettingsCommand implements CommandHandler {

    private final UserService users;
    private final Keyboards keyboards;
    private final BotMessages msg;

    public SettingsCommand(UserService users, Keyboards keyboards, BotMessages msg) {
        this.users = users;
        this.keyboards = keyboards;
        this.msg = msg;
    }

    @Override
    public String command() {
        return "settings";
    }

    @Override
    public String description() {
        return "Show or change settings: /settings mode strict";
    }

    @Override
    public void handle(CommandContext ctx) {
        User user = ctx.user();
        String[] argv = ctx.argv();
        if (argv.length == 0) {
            ctx.reply().send(render(user));
            return;
        }
        if (argv.length == 1 && argv[0].equalsIgnoreCase("mode")) {
            ctx.reply().send(msg.get(user, "settings.mode.choose"), keyboards.modes());
            return;
        }
        if (argv.length < 2) {
            ctx.reply().send(msg.get(user, "settings.usage"));
            return;
        }
        String key = argv[0].toLowerCase(Locale.ROOT);
        String value = argv[1];
        switch (key) {
            case "mode" -> users.setMode(user, parseEnum(value));
            case "timezone", "tz" -> users.setTimezone(user, value);
            case "lang", "language" -> users.setLanguage(user, value.toLowerCase(Locale.ROOT));
            case "limit" -> user.getSettings().setDailyTaskLimit(intIn(value, 1, 20));
            case "minutes" -> user.getSettings().setMaxDailyStudyMinutes(intIn(value, 15, 600));
            case "questions" -> user.getSettings().setVerificationMaxQuestions(intIn(value, 2, 8));
            case "morning" -> user.getSettings().setMorningPlanTime(java.time.LocalTime.parse(value));
            case "evening" -> user.getSettings().setEveningSummaryTime(offOr(value));
            case "quiz" -> user.getSettings().setQuizTime(offOr(value));
            case "quiet" -> {
                if (isOff(value)) {
                    user.getSettings().setQuietHours(null, null);
                } else {
                    String[] range = value.split("-");
                    if (range.length != 2) {
                        throw new DomainException("Format: /settings quiet 23:00-08:00 or /settings quiet off");
                    }
                    user.getSettings().setQuietHours(java.time.LocalTime.parse(range[0]), java.time.LocalTime.parse(range[1]));
                }
            }
            case "consequences" -> user.getConsequencePolicy().setEnabled(bool(value));
            case "extra" -> user.getConsequencePolicy().setMissedExtraMinutes(intIn(value, 0, 60));
            case "max_extra" -> user.getConsequencePolicy().setMaxExtraMinutes(intIn(value, 0, 120));
            case "reason" -> user.getConsequencePolicy().setRequireSkipReason(bool(value));
            case "review_task" -> user.getConsequencePolicy().setReviewTaskOnFail(bool(value));
            default -> throw new DomainException(msg.get(user, "settings.usage"));
        }
        users.save(user);
        ctx.reply().send(msg.get(user, "settings.saved") + "\n\n" + render(user));
    }

    private String render(User u) {
        String quiet = u.getSettings().hasQuietHours()
                ? u.getSettings().getQuietHoursStart() + "-" + u.getSettings().getQuietHoursEnd() : msg.get(u, "off");
        return msg.get(u, "settings.text",
                u.getMode().name(), u.getTimezone(), u.getLanguage(),
                u.getSettings().getDailyTaskLimit(), u.getSettings().getMaxDailyStudyMinutes(),
                u.getSettings().getVerificationMaxQuestions(), u.getSettings().getMorningPlanTime().toString(),
                msg.get(u, u.getConsequencePolicy().isEnabled() ? "on" : "off"),
                u.getConsequencePolicy().getMissedExtraMinutes(), u.getConsequencePolicy().getMaxExtraMinutes(),
                msg.get(u, u.getConsequencePolicy().isReviewTaskOnFail() ? "on" : "off"),
                msg.get(u, u.getConsequencePolicy().isRequireSkipReason() ? "on" : "off"),
                quiet, timeOrOff(u, u.getSettings().getEveningSummaryTime()), timeOrOff(u, u.getSettings().getQuizTime()));
    }

    private String timeOrOff(User u, java.time.LocalTime t) {
        return t == null ? msg.get(u, "off") : t.toString();
    }

    private static boolean isOff(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "off", "none", "выкл", "нет" -> true;
            default -> false;
        };
    }

    private static java.time.LocalTime offOr(String value) {
        if (isOff(value)) {
            return null;
        }
        try {
            return java.time.LocalTime.parse(value);
        } catch (java.time.format.DateTimeParseException e) {
            throw new DomainException("Expected HH:mm or off, got: " + value);
        }
    }

    private static StrictnessMode parseEnum(String value) {
        try {
            return StrictnessMode.valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new DomainException("Mode must be normal, strict or hardcore");
        }
    }

    private static int intIn(String value, int min, int max) {
        try {
            int v = Integer.parseInt(value);
            if (v < min || v > max) {
                throw new DomainException("Value must be between " + min + " and " + max);
            }
            return v;
        } catch (NumberFormatException e) {
            throw new DomainException("Expected a number, got: " + value);
        }
    }

    private static boolean bool(String value) {
        return switch (value.toLowerCase(Locale.ROOT)) {
            case "on", "true", "yes", "1", "да", "вкл" -> true;
            case "off", "false", "no", "0", "нет", "выкл" -> false;
            default -> throw new DomainException("Expected on/off, got: " + value);
        };
    }
}
