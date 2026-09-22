package dev.kennurken.tutorbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point of the modular monolith.
 *
 * <p>Each top-level package (task, verification, ai, telegram, ...) is a module with its own
 * service layer. Modules talk to each other through services and Spring application events,
 * never through each other's repositories.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
@EnableAsync
public class TutorBotApplication {

    public static void main(String[] args) {
        SpringApplication.run(TutorBotApplication.class, args);
    }
}
