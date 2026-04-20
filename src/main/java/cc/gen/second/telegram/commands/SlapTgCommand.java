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
                "💥 F-forgive me, I'll try to do better...\n"
                        + "Senpai has been displeased with C.C. *" + count + "* time(s).");
    }

    @Override
    public String getName() {
        return "slap";
    }

    @Override
    public String getDescription() {
        return "Slap C.C.";
    }
}
