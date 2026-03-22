package cc.gen.second.telegram.commands;

import cc.gen.second.telegram.ITelegramCommand;
import cc.gen.second.telegram.TelegramBot;
import cc.gen.second.utils.TagStore;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;

public class TagTgCommand implements ITelegramCommand {

    @Override
    public void handle(TelegramBot bot, Message message, List<String> args) {
        long chatId = message.getChatId();

        if (args.isEmpty()) {
            bot.sendText(chatId,
                    "Использование:\n"
                            + "`/tag create <имя> <содержимое>` — создать тег\n"
                            + "`/tag <имя>` — получить тег");
            return;
        }

        if (args.get(0).equalsIgnoreCase("create")) {
            if (args.size() < 3) {
                bot.sendText(chatId, "❌ Использование: `/tag create <имя> <содержимое>`");
                return;
            }
            String tagName = args.get(1);
            String tagText = String.join(" ", args.subList(2, args.size()));
            String author = message.getFrom().getFirstName();
            TagStore.create(author, tagName, tagText);
            bot.sendText(chatId, "✅ Тег создан!");
        } else {
            String tagName = args.get(0);
            String content = TagStore.read(tagName);
            if (content != null) {
                bot.sendText(chatId, content);
            } else {
                bot.sendText(chatId, "❌ Тег не найден: " + tagName);
            }
        }
    }

    @Override
    public String getName() {
        return "tag";
    }

    @Override
    public String getDescription() {
        return "Создать или получить тег";
    }
}
