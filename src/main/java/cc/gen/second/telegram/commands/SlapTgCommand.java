package cc.gen.second.telegram.commands;

import cc.gen.second.telegram.ITelegramCommand;
import cc.gen.second.telegram.TelegramBot;
import cc.gen.second.utils.CounterStore;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

public class SlapTgCommand implements ITelegramCommand {

    @Override
    public void handle(TelegramBot bot, Message message, List<String> args) {
        int count = CounterStore.increment("slaps");
        bot.sendText(message.getChatId(),
                "💥 П-простите меня, я постараюсь быть лучше...\n"
                        + "Семпаи были недовольны С.С. *" + count + "* раз/a.");
    }

    @Override
    public String getName() {
        return "slap";
    }

    @Override
    public String getDescription() {
        return "Шлёпнуть С.С.";
    }
}
