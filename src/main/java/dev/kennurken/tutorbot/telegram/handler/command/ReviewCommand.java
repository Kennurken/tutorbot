package dev.kennurken.tutorbot.telegram.handler.command;

import dev.kennurken.tutorbot.review.WeeklyReview;
import dev.kennurken.tutorbot.review.WeeklyReviewService;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import org.springframework.stereotype.Component;

@Component
public class ReviewCommand implements CommandHandler {

    private static final Duration FRESH = Duration.ofHours(6);

    private final WeeklyReviewService reviews;
    private final Clock clock;

    public ReviewCommand(WeeklyReviewService reviews, Clock clock) {
        this.reviews = reviews;
        this.clock = clock;
    }

    @Override
    public String command() {
        return "review";
    }

    @Override
    public String description() {
        return "Weekly review (this week, or /review last)";
    }

    @Override
    public void handle(CommandContext ctx) {
        LocalDate today = ctx.user().today(clock.instant());
        LocalDate weekStart = WeeklyReviewService.weekStartOf(today);
        if (ctx.hasArgs() && ctx.args().trim().equalsIgnoreCase("last")) {
            weekStart = weekStart.minusWeeks(1);
        }
        final LocalDate ws = weekStart;
        WeeklyReview review = reviews.find(ctx.user(), ws)
                .filter(r -> r.getCreatedAt().plus(FRESH).isAfter(clock.instant()))
                .orElseGet(() -> reviews.generate(ctx.user(), ws));
        ctx.reply().send(reviews.render(ctx.user(), review));
    }
}
