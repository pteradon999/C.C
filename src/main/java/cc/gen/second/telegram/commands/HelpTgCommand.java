package cc.gen.second.telegram.commands;

import cc.gen.second.telegram.ITelegramCommand;
import cc.gen.second.telegram.TelegramBot;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

public class HelpTgCommand implements ITelegramCommand {

    private final TelegramBot bot;

    public HelpTgCommand(TelegramBot bot) {
        this.bot = bot;
    }

    @Override
    public void handle(TelegramBot b, Message message, List<String> args) {
        StringBuilder sb = new StringBuilder();
        sb.append("*C.C — Telegram Bot*\n");
        sb.append("════════════════════\n\n");

        for (ITelegramCommand cmd : bot.getCommands()) {
            sb.append("/").append(cmd.getName()).append(" — ").append(cmd.getDescription()).append("\n");
        }

        b.sendText(message.getChatId(), sb.toString());
    }

    @Override
    public String getName() {
        return "help";
    }

    @Override
    public String getDescription() {
        return "Список команд";
    }
}
