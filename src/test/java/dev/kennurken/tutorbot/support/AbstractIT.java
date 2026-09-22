package dev.kennurken.tutorbot.support;

import dev.kennurken.tutorbot.telegram.api.TelegramClient;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes;
import dev.kennurken.tutorbot.user.TelegramIdentity;
import dev.kennurken.tutorbot.user.User;
import dev.kennurken.tutorbot.user.UserService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * Integration tests run against a real PostgreSQL (profile "test", see application-test.yml)
 * with Flyway-migrated schema. Telegram is mocked (no network); the AI provider is the fake.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestClockConfig.class)
public abstract class AbstractIT {

    private static final List<String> TABLES = List.of("verification_turns", "verification_sessions", "consequences",
            "notifications", "task_events", "tasks", "recurrence_rules", "knowledge_topics", "weekly_reviews",
            "ai_interactions", "conversation_states", "telegram_updates", "goals", "users");

    @Autowired
    protected JdbcTemplate jdbc;

    @Autowired
    protected MutableClock clock;

    @Autowired
    protected UserService userService;

    @MockitoBean
    protected TelegramClient telegram;

    @BeforeEach
    void resetDatabase() {
        jdbc.execute("TRUNCATE TABLE " + String.join(", ", TABLES) + " RESTART IDENTITY CASCADE");
        clock.set(TestClockConfig.START);
        Mockito.reset(telegram);
        Mockito.when(telegram.sendMessage(Mockito.anyLong(), Mockito.anyString(), Mockito.any()))
                .thenReturn(new TelegramTypes.Message(1L, null, new TelegramTypes.Chat(1L, "private"), "", 0));
    }

    protected User registerUser() {
        return userService.getOrRegister(new TelegramIdentity(100L, 100L, "Eldos", "eldos", "ru"));
    }
}
