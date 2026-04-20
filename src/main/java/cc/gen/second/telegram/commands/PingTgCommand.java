package cc.gen.second.telegram.commands;

import cc.gen.second.telegram.ITelegramCommand;
import cc.gen.second.telegram.TelegramBot;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

public class PingTgCommand implements ITelegramCommand {

    @Override
    public void handle(TelegramBot bot, Message message, List<String> args) {
        long start = System.currentTimeMillis();
        bot.sendText(message.getChatId(), "Pong! 🏓");
    }

    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public List<String> getAliases() {
        return List.of("pong");
    }

    @Override
    public String getDescription() {
        return "Check if C.C. is alive";
    }
}
