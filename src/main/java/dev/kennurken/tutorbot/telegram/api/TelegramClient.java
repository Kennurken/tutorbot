package dev.kennurken.tutorbot.telegram.api;

import dev.kennurken.tutorbot.messaging.InlineKeyboard;
import dev.kennurken.tutorbot.telegram.TelegramProperties;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.ApiResponse;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.BotCommand;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.Message;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.TgUser;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.Update;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

/**
 * Minimal Bot API client. A hand-rolled client over {@link RestClient} is ~150 lines and
 * exposes exactly the five calls we use; a library would add a large dependency surface and
 * its own update-loop opinions. The token never appears in logs: only the method name does.
 */
@Component
public class TelegramClient {

    private static final Logger log = LoggerFactory.getLogger(TelegramClient.class);
    private static final ParameterizedTypeReference<ApiResponse<Message>> MESSAGE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<Boolean>> BOOL =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<TgUser>> USER =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<List<Update>>> UPDATES =
            new ParameterizedTypeReference<>() { };

    private final RestClient client;
    private final RestClient longPollClient;

    public TelegramClient(RestClient.Builder builder, TelegramProperties properties) {
        String base = "https://api.telegram.org/bot" + properties.token();
        this.client = builder.clone().baseUrl(base).requestFactory(factory(Duration.ofSeconds(20))).build();
        this.longPollClient = builder.clone().baseUrl(base)
                .requestFactory(factory(Duration.ofSeconds(properties.pollingTimeoutSeconds() + 15L))).build();
    }

    private static JdkClientHttpRequestFactory factory(Duration readTimeout) {
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        f.setReadTimeout(readTimeout);
        return f;
    }

    public Message sendMessage(long chatId, String html, InlineKeyboard keyboard) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("text", html);
        body.put("parse_mode", "HTML");
        body.put("disable_web_page_preview", true);
        if (keyboard != null) {
            body.put("reply_markup", keyboardJson(keyboard));
        }
        return call("sendMessage", body, MESSAGE);
    }

    public void answerCallbackQuery(String callbackQueryId, String text) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("callback_query_id", callbackQueryId);
        if (text != null) {
            body.put("text", text);
        }
        try {
            call("answerCallbackQuery", body, BOOL);
        } catch (TelegramApiException e) {
            log.debug("answerCallbackQuery failed: {}", e.getMessage());
        }
    }

    /** Removes the buttons under a message once one of them was used. */
    public void clearKeyboard(long chatId, long messageId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("chat_id", chatId);
        body.put("message_id", messageId);
        body.put("reply_markup", Map.of("inline_keyboard", List.of()));
        try {
            call("editMessageReplyMarkup", body, MESSAGE);
        } catch (TelegramApiException e) {
            log.debug("clearKeyboard failed: {}", e.getMessage());
        }
    }

    public void setWebhook(String url, String secretToken) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("url", url);
        body.put("secret_token", secretToken);
        body.put("allowed_updates", List.of("message", "callback_query"));
        body.put("drop_pending_updates", false);
        call("setWebhook", body, BOOL);
    }

    public void deleteWebhook() {
        call("deleteWebhook", Map.of("drop_pending_updates", false), BOOL);
    }

    public List<Update> getUpdates(long offset, int timeoutSeconds) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("offset", offset);
        body.put("timeout", timeoutSeconds);
        body.put("allowed_updates", List.of("message", "callback_query"));
        List<Update> updates = call(longPollClient, "getUpdates", body, UPDATES);
        return updates == null ? List.of() : updates;
    }

    public void setMyCommands(List<BotCommand> commands) {
        call("setMyCommands", Map.of("commands", commands), BOOL);
    }

    public TgUser getMe() {
        return call("getMe", Map.of(), USER);
    }

    private <T> T call(String method, Map<String, Object> body, ParameterizedTypeReference<ApiResponse<T>> type) {
        return call(client, method, body, type);
    }

    private <T> T call(RestClient rc, String method, Map<String, Object> body,
                       ParameterizedTypeReference<ApiResponse<T>> type) {
        try {
            ApiResponse<T> response = rc.post().uri("/" + method).body(body).retrieve().body(type);
            if (response == null || !response.ok()) {
                String desc = response == null ? "empty response" : response.description();
                throw new TelegramApiException("Telegram " + method + " failed: " + desc, false, 0, null);
            }
            return response.result();
        } catch (HttpStatusCodeException e) {
            int status = e.getStatusCode().value();
            int retryAfter = 0;
            boolean retryable = status == 429 || status >= 500;
            if (status == 429) {
                retryAfter = 5;
            }
            log.warn("Telegram {} -> HTTP {}: {}", method, status, truncate(e.getResponseBodyAsString()));
            throw new TelegramApiException("Telegram " + method + " HTTP " + status, retryable, retryAfter, e);
        } catch (ResourceAccessException e) {
            throw new TelegramApiException("Telegram unreachable on " + method + ": " + e.getMessage(), true, 0, e);
        }
    }

    static Map<String, Object> keyboardJson(InlineKeyboard keyboard) {
        List<List<Map<String, String>>> rows = new ArrayList<>();
        for (List<InlineKeyboard.Button> row : keyboard.rows()) {
            List<Map<String, String>> out = new ArrayList<>();
            for (InlineKeyboard.Button b : row) {
                out.add(Map.of("text", b.text(), "callback_data", b.callbackData()));
            }
            rows.add(out);
        }
        return Map.of("inline_keyboard", rows);
    }

    private static String truncate(String s) {
        return s != null && s.length() > 200 ? s.substring(0, 200) : String.valueOf(s);
    }
}
