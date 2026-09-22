package dev.kennurken.tutorbot.messaging;

import static dev.kennurken.tutorbot.messaging.InlineKeyboard.btn;

import dev.kennurken.tutorbot.task.SkipCategory;
import dev.kennurken.tutorbot.task.Task;
import dev.kennurken.tutorbot.user.StrictnessMode;
import dev.kennurken.tutorbot.user.User;
import org.springframework.stereotype.Component;

/** All inline keyboards in one place so wording and callback data stay consistent. */
@Component
public class Keyboards {

    private final BotMessages msg;

    public Keyboards(BotMessages msg) {
        this.msg = msg;
    }

    public InlineKeyboard taskStart(User u, Task t) {
        return InlineKeyboard.builder()
                .row(btn(msg.get(u, "btn.start"), Callbacks.of(Callbacks.START, t.getId())),
                        btn(msg.get(u, "btn.done"), Callbacks.of(Callbacks.DONE, t.getId())))
                .row(btn(msg.get(u, "btn.skip"), Callbacks.of(Callbacks.SKIP, t.getId())),
                        btn(msg.get(u, "btn.later"), Callbacks.of(Callbacks.RESCHEDULE, t.getId(), "30m")))
                .build();
    }

    public InlineKeyboard started(User u, Task t) {
        return InlineKeyboard.builder()
                .row(btn(msg.get(u, "btn.done"), Callbacks.of(Callbacks.DONE, t.getId())),
                        btn(msg.get(u, "btn.skip"), Callbacks.of(Callbacks.SKIP, t.getId())))
                .build();
    }

    public InlineKeyboard missed(User u, Task t) {
        return InlineKeyboard.builder()
                .row(btn(msg.get(u, "btn.resch.2h"), Callbacks.of(Callbacks.RESCHEDULE, t.getId(), "2h")),
                        btn(msg.get(u, "btn.resch.tomorrow"), Callbacks.of(Callbacks.RESCHEDULE, t.getId(), "tmrw")))
                .row(btn(msg.get(u, "btn.skip.reason"), Callbacks.of(Callbacks.SKIP, t.getId())))
                .build();
    }

    public InlineKeyboard verificationFailed(User u, Task t) {
        return InlineKeyboard.builder()
                .row(btn(msg.get(u, "btn.retry"), Callbacks.of(Callbacks.RETRY, t.getId())),
                        btn(msg.get(u, "btn.review"), Callbacks.of(Callbacks.REVIEW_MATERIAL, t.getId())))
                .row(btn(msg.get(u, "btn.resch.tomorrow"), Callbacks.of(Callbacks.RESCHEDULE, t.getId(), "tmrw")))
                .build();
    }

    public InlineKeyboard verificationPending(User u, Task t) {
        return InlineKeyboard.builder()
                .row(btn(msg.get(u, "btn.verify.now"), Callbacks.of(Callbacks.RETRY, t.getId())))
                .build();
    }

    public InlineKeyboard skipCategories(User u, long taskId) {
        return InlineKeyboard.builder()
                .row(cat(u, taskId, SkipCategory.OBJECTIVE_REASON), cat(u, taskId, SkipCategory.EMERGENCY))
                .row(cat(u, taskId, SkipCategory.TOO_TIRED), cat(u, taskId, SkipCategory.TOO_DIFFICULT))
                .row(cat(u, taskId, SkipCategory.FORGOT), cat(u, taskId, SkipCategory.BAD_SCHEDULE))
                .row(cat(u, taskId, SkipCategory.DID_NOT_WANT_TO), cat(u, taskId, SkipCategory.OTHER))
                .build();
    }

    private InlineKeyboard.Button cat(User u, long taskId, SkipCategory c) {
        return btn(msg.get(u, "skip.cat." + c.name()), Callbacks.of(Callbacks.SKIP_CATEGORY, taskId, c.name()));
    }

    public InlineKeyboard reschedulePresets(User u, long taskId) {
        return InlineKeyboard.builder()
                .row(btn("+30m", Callbacks.of(Callbacks.RESCHEDULE, taskId, "30m")),
                        btn("+2h", Callbacks.of(Callbacks.RESCHEDULE, taskId, "2h")))
                .row(btn(msg.get(u, "btn.resch.evening"), Callbacks.of(Callbacks.RESCHEDULE, taskId, "eve")),
                        btn(msg.get(u, "btn.resch.tomorrow"), Callbacks.of(Callbacks.RESCHEDULE, taskId, "tmrw")))
                .build();
    }

    public InlineKeyboard confirmTask(User u) {
        return InlineKeyboard.builder()
                .row(btn(msg.get(u, "btn.yes"), Callbacks.of(Callbacks.TASK_CONFIRM, "yes")),
                        btn(msg.get(u, "btn.no"), Callbacks.of(Callbacks.TASK_CONFIRM, "no")))
                .build();
    }

    public InlineKeyboard pausePresets(User u) {
        return InlineKeyboard.builder()
                .row(btn("1h", Callbacks.of(Callbacks.PAUSE, "1h")),
                        btn(msg.get(u, "btn.pause.today"), Callbacks.of(Callbacks.PAUSE, "today")),
                        btn("24h", Callbacks.of(Callbacks.PAUSE, "24h")))
                .row(btn(msg.get(u, "btn.pause.off"), Callbacks.of(Callbacks.PAUSE, "off")))
                .build();
    }

    public InlineKeyboard modes() {
        InlineKeyboard.Builder b = InlineKeyboard.builder();
        for (StrictnessMode m : StrictnessMode.values()) {
            b.row(btn(m.name(), Callbacks.of(Callbacks.MODE, m.name())));
        }
        return b.build();
    }
}
