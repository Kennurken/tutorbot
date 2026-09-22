package dev.kennurken.tutorbot.user;

import jakarta.persistence.Column;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "telegram_user_id", nullable = false, unique = true)
    private long telegramUserId;

    @Column(name = "chat_id", nullable = false)
    private long chatId;

    private String firstName;

    private String username;

    @Column(nullable = false)
    private String language;

    /** IANA zone id, e.g. "Asia/Almaty". All instants are UTC; this is applied at the edges. */
    @Column(nullable = false)
    private String timezone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private StrictnessMode mode = StrictnessMode.NORMAL;

    @Embedded
    private UserSettings settings = new UserSettings();

    @Embedded
    private ConsequencePolicy consequencePolicy = new ConsequencePolicy();

    private Instant pausedUntil;

    private Instant recoveryModeUntil;

    private LocalDate lastMorningPlanDate;

    private LocalDate lastEveningSummaryDate;

    private LocalDate lastQuizDate;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    protected User() {
        // JPA
    }

    public User(long telegramUserId, long chatId, String firstName, String username,
                String language, String timezone) {
        this.telegramUserId = telegramUserId;
        this.chatId = chatId;
        this.firstName = firstName;
        this.username = username;
        this.language = language;
        this.timezone = timezone;
    }

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public ZoneId zone() {
        return ZoneId.of(timezone);
    }

    public boolean isPaused(Instant now) {
        return pausedUntil != null && pausedUntil.isAfter(now);
    }

    public boolean isInRecoveryMode(Instant now) {
        return recoveryModeUntil != null && recoveryModeUntil.isAfter(now);
    }

    public LocalDate today(Instant now) {
        return now.atZone(zone()).toLocalDate();
    }

    public Long getId() {
        return id;
    }

    public long getTelegramUserId() {
        return telegramUserId;
    }

    public long getChatId() {
        return chatId;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getLanguage() {
        return language;
    }

    public void setLanguage(String language) {
        this.language = language;
    }

    public String getTimezone() {
        return timezone;
    }

    public void setTimezone(String timezone) {
        this.timezone = timezone;
    }

    public StrictnessMode getMode() {
        return mode;
    }

    public void setMode(StrictnessMode mode) {
        this.mode = mode;
    }

    public UserSettings getSettings() {
        return settings;
    }

    public ConsequencePolicy getConsequencePolicy() {
        return consequencePolicy;
    }

    public Instant getPausedUntil() {
        return pausedUntil;
    }

    public void setPausedUntil(Instant pausedUntil) {
        this.pausedUntil = pausedUntil;
    }

    public Instant getRecoveryModeUntil() {
        return recoveryModeUntil;
    }

    public void setRecoveryModeUntil(Instant recoveryModeUntil) {
        this.recoveryModeUntil = recoveryModeUntil;
    }

    public LocalDate getLastMorningPlanDate() {
        return lastMorningPlanDate;
    }

    public void setLastMorningPlanDate(LocalDate lastMorningPlanDate) {
        this.lastMorningPlanDate = lastMorningPlanDate;
    }

    public LocalDate getLastEveningSummaryDate() {
        return lastEveningSummaryDate;
    }

    public void setLastEveningSummaryDate(LocalDate lastEveningSummaryDate) {
        this.lastEveningSummaryDate = lastEveningSummaryDate;
    }

    public LocalDate getLastQuizDate() {
        return lastQuizDate;
    }

    public void setLastQuizDate(LocalDate lastQuizDate) {
        this.lastQuizDate = lastQuizDate;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
