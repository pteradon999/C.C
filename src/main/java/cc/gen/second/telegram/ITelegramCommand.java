package cc.gen.second.telegram;

import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

public interface ITelegramCommand {

    void handle(TelegramBot bot, Message message, List<String> args);

    String getName();

    default List<String> getAliases() {
        return List.of();
    }

    String getDescription();
}
