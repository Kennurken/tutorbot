package dev.kennurken.tutorbot.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import org.junit.jupiter.api.Test;

class UserSettingsTest {

    private static final ZoneId ALMATY = ZoneId.of("Asia/Almaty");

    private static Instant local(int hour, int minute) {
        return LocalDateTime.of(2026, 9, 21, hour, minute).atZone(ALMATY).toInstant();
    }

    @Test
    void quietWindowWrapsMidnight() {
        UserSettings s = new UserSettings();
        s.setQuietHours(LocalTime.of(23, 0), LocalTime.of(8, 0));

        assertThat(s.isQuietAt(local(23, 30), ALMATY)).isTrue();
        assertThat(s.isQuietAt(local(2, 0), ALMATY)).isTrue();
        assertThat(s.isQuietAt(local(7, 59), ALMATY)).isTrue();
        assertThat(s.isQuietAt(local(8, 0), ALMATY)).isFalse();
        assertThat(s.isQuietAt(local(15, 0), ALMATY)).isFalse();
        assertThat(s.quietWindowEnd(local(23, 30), ALMATY))
                .isEqualTo(LocalDateTime.of(2026, 9, 22, 8, 0).atZone(ALMATY).toInstant());
    }

    @Test
    void daytimeWindowAndOff() {
        UserSettings s = new UserSettings();
        s.setQuietHours(LocalTime.of(13, 0), LocalTime.of(14, 0));
        assertThat(s.isQuietAt(local(13, 30), ALMATY)).isTrue();
        assertThat(s.isQuietAt(local(14, 0), ALMATY)).isFalse();

        s.setQuietHours(null, null);
        assertThat(s.hasQuietHours()).isFalse();
        assertThat(s.isQuietAt(local(3, 0), ALMATY)).isFalse();
    }
}
