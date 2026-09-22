package dev.kennurken.tutorbot.common.time;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/** Rendering helpers: every user-facing time is converted into the user's zone here. */
public final class TimeFormats {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private TimeFormats() {
    }

    public static String time(Instant instant, ZoneId zone) {
        return TIME.format(instant.atZone(zone));
    }

    public static String dateTime(Instant instant, ZoneId zone) {
        return DATE_TIME.format(instant.atZone(zone));
    }

    public static String date(LocalDate date) {
        return DATE.format(date);
    }

    /** "19:00" if the instant is today in the zone, otherwise "22.09 19:00". */
    public static String smart(Instant instant, ZoneId zone, Instant now) {
        ZonedDateTime target = instant.atZone(zone);
        if (target.toLocalDate().equals(now.atZone(zone).toLocalDate())) {
            return TIME.format(target);
        }
        return DATE_TIME.format(target);
    }
}
