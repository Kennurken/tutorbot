package dev.kennurken.tutorbot.telegram;

import dev.kennurken.tutorbot.conversation.ConversationService;
import dev.kennurken.tutorbot.conversation.ConversationState;
import dev.kennurken.tutorbot.messaging.BotMessages;
import dev.kennurken.tutorbot.planning.NaturalLanguageTaskParser;
import dev.kennurken.tutorbot.task.CreateTaskCommand;
import dev.kennurken.tutorbot.task.SkipCategory;
import dev.kennurken.tutorbot.telegram.api.TelegramClient;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.CallbackQuery;
import dev.kennurken.tutorbot.telegram.api.TelegramTypes.Message;
import dev.kennurken.tutorbot.telegram.flow.TaskFlows;
import dev.kennurken.tutorbot.telegram.flow.VerificationFlow;
import dev.kennurken.tutorbot.telegram.handler.CallbackContext;
import dev.kennurken.tutorbot.telegram.handler.CallbackHandler;
import dev.kennurken.tutorbot.telegram.handler.CommandContext;
import dev.kennurken.tutorbot.telegram.handler.CommandHandler;
import dev.kennurken.tutorbot.user.User;
import java.time.Clock;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Routes one update to exactly one handler: a command, a button, or free text interpreted
 * according to the user's conversation state. Contains no business logic.
 */
@Component
public class UpdateDispatcher {

    private final Map<String, CommandHandler> commands;
    private final Map<String, CallbackHandler> callbacks;
    private final ConversationService conversations;
    private final TaskFlows taskFlows;
    private final VerificationFlow verificationFlow;
    private final TelegramClient client;
    private final BotMessages msg;
    private final Clock clock;

    public UpdateDispatcher(List<CommandHandler> commandHandlers, List<CallbackHandler> callbackHandlers,
                            ConversationService conversations, TaskFlows taskFlows, VerificationFlow verificationFlow,
                            TelegramClient client, BotMessages msg, Clock clock) {
        this.commands = commandHandlers.stream().collect(Collectors.toMap(CommandHandler::command, Function.identity()));
        this.callbacks = callbackHandlers.stream().collect(Collectors.toMap(CallbackHandler::action, Function.identity()));
        this.conversations = conversations;
        this.taskFlows = taskFlows;
        this.verificationFlow = verificationFlow;
        this.client = client;
        this.msg = msg;
        this.clock = clock;
    }

    public Map<String, CommandHandler> commands() {
        return commands;
    }

    public void onMessage(User user, Message message) {
        String text = message.text();
        if (text == null || text.isBlank()) {
            return;
        }
        Replier reply = new Replier(client, message.chat().id());
        if (text.startsWith("/")) {
            handleCommand(user, text, reply);
        } else {
            handleText(user, text.trim(), reply);
        }
    }

    private void handleCommand(User user, String text, Replier reply) {
        String[] parts = text.trim().split("\\s+", 2);
        String name = parts[0].substring(1).toLowerCase(Locale.ROOT);
        int at = name.indexOf('@');
        if (at > 0) {
            name = name.substring(0, at);
        }
        String args = parts.length > 1 ? parts[1] : "";
        CommandHandler handler = commands.get(name);
        if (handler == null) {
            reply.send(msg.get(user, "error.unknown_command"));
            return;
        }
        handler.handle(new CommandContext(user, args, reply));
    }

    private void handleText(User user, String text, Replier reply) {
        ConversationService.Snapshot state = conversations.current(user.getId());
        switch (state.state()) {
            case IN_VERIFICATION -> verificationFlow.answer(user, text, reply);
            case AWAITING_SKIP_REASON -> taskFlows.finishSkip(user, state.taskId(),
                    SkipCategory.valueOf(state.text("category")), text, reply);
            case AWAITING_TASK_TEXT -> taskFlows.proposeFromText(user, text, reply);
            case AWAITING_TASK_CONFIRMATION -> {
                String lower = text.toLowerCase(Locale.ROOT);
                if (lower.equals("да") || lower.equals("yes") || lower.equals("y") || lower.equals("ок") || lower.equals("ok")) {
                    taskFlows.confirmDraft(user, true, reply);
                } else if (lower.equals("нет") || lower.equals("no") || lower.equals("n")) {
                    taskFlows.confirmDraft(user, false, reply);
                } else {
                    taskFlows.proposeFromText(user, text, reply);
                }
            }
            case AWAITING_RESCHEDULE_TIME -> {
                Optional<CreateTaskCommand> parsed = NaturalLanguageTaskParser.parse(text + " task", clock.instant(), user.zone());
                if (parsed.isEmpty() || parsed.get().scheduledAt() == null) {
                    reply.send(msg.get(user, "resch.badtime"));
                } else {
                    taskFlows.rescheduleTo(user, state.taskId(), parsed.get().scheduledAt(), reply);
                }
            }
            case IDLE, AWAITING_GOAL_PLAN_CONFIRMATION -> taskFlows.proposeFromText(user, text, reply);
        }
    }

    public void onCallback(User user, CallbackQuery query) {
        String data = query.data() == null ? "" : query.data();
        List<String> parts = Arrays.asList(data.split(":"));
        String action = parts.isEmpty() ? "" : parts.get(0);
        List<String> args = parts.size() > 1 ? parts.subList(1, parts.size()) : List.of();
        long chatId = query.message() != null ? query.message().chat().id() : user.getChatId();
        Replier reply = new Replier(client, chatId);
        CallbackHandler handler = callbacks.get(action);
        client.answerCallbackQuery(query.id(), null);
        if (handler == null) {
            return;
        }
        if (query.message() != null) {
            client.clearKeyboard(chatId, query.message().messageId());
        }
        handler.handle(new CallbackContext(user, action, args, query, reply));
    }

    /** Sets the conversation to "expecting a task description" (used by a bare /add). */
    public void expectTaskText(User user) {
        conversations.set(user.getId(), ConversationState.AWAITING_TASK_TEXT, null);
    }
}
