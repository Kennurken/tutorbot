package dev.kennurken.tutorbot.telegram;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.kennurken.tutorbot.support.AbstractIT;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@AutoConfigureMockMvc
@TestPropertySource(properties = {"telegram.webhook-secret=s3cret", "scheduler.tick-secret=t1ck"})
class WebhookIT extends AbstractIT {

    private static final String UPDATE = """
            {"update_id": 5, "message": {"message_id": 1, "from": {"id": 100, "is_bot": false, "first_name": "E"},
             "chat": {"id": 100, "type": "private"}, "text": "/status", "date": 0}}
            """;

    @Autowired
    MockMvc mvc;

    @Test
    void rejectsWrongSecret() throws Exception {
        mvc.perform(post("/telegram/webhook").contentType(MediaType.APPLICATION_JSON).content(UPDATE))
                .andExpect(status().isUnauthorized());
        mvc.perform(post("/telegram/webhook").contentType(MediaType.APPLICATION_JSON).content(UPDATE)
                        .header("X-Telegram-Bot-Api-Secret-Token", "wrong"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void acceptsCorrectSecretImmediately() throws Exception {
        mvc.perform(post("/telegram/webhook").contentType(MediaType.APPLICATION_JSON).content(UPDATE)
                        .header("X-Telegram-Bot-Api-Secret-Token", "s3cret"))
                .andExpect(status().isOk());
    }

    @Test
    void tickEndpointNeedsItsOwnSecret() throws Exception {
        mvc.perform(post("/internal/tick")).andExpect(status().isUnauthorized());
        mvc.perform(post("/internal/tick").header("X-Tick-Secret", "t1ck")).andExpect(status().isOk());
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/actuator/health"))
                .andExpect(status().isOk());
    }
}
