package cc.gen.second.telegram;

import cc.gen.second.config;
import cc.gen.second.telegram.commands.*;
import cc.gen.second.utils.TagStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.commands.SetMyCommands;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.commands.BotCommand;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class TelegramBot extends TelegramLongPollingBot {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelegramBot.class);
    private static final int TELEGRAM_MESSAGE_LIMIT = 4096;

    private final String botUsername;
    private final List<ITelegramCommand> commands = new ArrayList<>();

    public TelegramBot(String token, String username) {
        super(token);
        this.botUsername = username;

        addCommand(new PingTgCommand());
        addCommand(new SlapTgCommand());
        addCommand(new PatTgCommand());
        addCommand(new RateitTgCommand());
        addCommand(new RatemeTgCommand());
        addCommand(new ProbTgCommand());
        addCommand(new CgachaTgCommand());
        addCommand(new WaifuGachaTgCommand());
        addCommand(new FormatTagsTgCommand());
        addCommand(new TagTgCommand());
        addCommand(new HelpTgCommand(this));

        LOGGER.info("Registered {} Telegram commands", commands.size());
        registerCommandMenu();
    }

    private void addCommand(ITelegramCommand cmd) {
        commands.add(cmd);
    }

    private void registerCommandMenu() {
        List<BotCommand> botCommands = new ArrayList<>();
        for (ITelegramCommand cmd : commands) {
            botCommands.add(new BotCommand(cmd.getName(), cmd.getDescription()));
        }
        try {
            execute(new SetMyCommands(botCommands, null, null));
            LOGGER.info("Registered {} commands with Telegram menu", botCommands.size());
        } catch (TelegramApiException e) {
            LOGGER.warn("Failed to register command menu with Telegram: {}", e.getMessage());
        }
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        var message = update.getMessage();
        String text = message.getText().trim();

        if (!text.startsWith("/")) {
            return;
        }

        // Parse: /command@botname arg1 arg2 ...
        String[] parts = text.split("\\s+");
        String commandPart = parts[0].substring(1); // remove leading /
        // Strip @botname suffix
        int atIndex = commandPart.indexOf('@');
        if (atIndex > 0) {
            commandPart = commandPart.substring(0, atIndex);
        }
        String commandName = commandPart.toLowerCase();

        List<String> args = parts.length > 1
                ? Arrays.asList(Arrays.copyOfRange(parts, 1, parts.length))
                : new ArrayList<>();

        ITelegramCommand cmd = findCommand(commandName);
        if (cmd != null) {
            try {
                cmd.handle(this, message, args);
                LOGGER.debug("Executed Telegram command: {} by {}",
                        cmd.getName(), message.getFrom().getUserName());
            } catch (Exception e) {
                LOGGER.error("Error executing Telegram command: {}", cmd.getName(), e);
                sendText(message.getChatId(), "❌ Ошибка: " + e.getMessage());
            }
        }
    }

    private ITelegramCommand findCommand(String name) {
        for (ITelegramCommand cmd : commands) {
            if (cmd.getName().equals(name)) return cmd;
            if (cmd.getAliases().contains(name)) return cmd;
        }
        return null;
    }

    public void sendText(long chatId, String text) {
        for (String chunk : splitMessage(text, TELEGRAM_MESSAGE_LIMIT)) {
            SendMessage msg = new SendMessage();
            msg.setChatId(chatId);
            msg.setText(chunk);
            msg.setParseMode("Markdown");
            msg.setDisableWebPagePreview(true);
            try {
                execute(msg);
            } catch (TelegramApiException e) {
                // Fallback: try without Markdown in case of parse errors
                msg.setParseMode(null);
                try {
                    execute(msg);
                } catch (TelegramApiException ex) {
                    LOGGER.error("Failed to send Telegram message", ex);
                }
            }
        }
    }

    public List<ITelegramCommand> getCommands() {
        return commands;
    }

    private static List<String> splitMessage(String text, int limit) {
        if (text.length() <= limit) return List.of(text);
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + limit);
            if (end < text.length()) {
                int nl = text.lastIndexOf('\n', end);
                if (nl > start && end - nl < 300) end = nl + 1;
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }
}
