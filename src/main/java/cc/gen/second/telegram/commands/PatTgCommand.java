package cc.gen.second.telegram.commands;

import cc.gen.second.telegram.ITelegramCommand;
import cc.gen.second.telegram.TelegramBot;
import cc.gen.second.utils.CounterStore;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

public class PatTgCommand implements ITelegramCommand {

    @Override
    public void handle(TelegramBot bot, Message message, List<String> args) {
        String userName = message.getFrom().getFirstName();
        int count = CounterStore.increment("pats");
        bot.sendText(message.getChatId(),
                "🥰 Спасибо вам, семпай " + userName + "!\n"
                        + "Семпаи погладили С.С. *" + count + "* раз(a).");
    }

    @Override
    public String getName() {
        return "pat";
    }

    @Override
    public String getDescription() {
        return "Погладить С.С.";
    }
}
