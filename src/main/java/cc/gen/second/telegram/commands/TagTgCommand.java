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
                    "Usage:\n"
                            + "`/tag create <name> <content>` — create a tag\n"
                            + "`/tag <name>` — retrieve a tag");
            return;
        }

        if (args.get(0).equalsIgnoreCase("create")) {
            if (args.size() < 3) {
                bot.sendText(chatId, "❌ Usage: `/tag create <name> <content>`");
                return;
            }
            String tagName = args.get(1);
            String tagText = String.join(" ", args.subList(2, args.size()));
            String author = message.getFrom().getFirstName();
            TagStore.create(author, tagName, tagText);
            bot.sendText(chatId, "✅ Tag created!");
        } else {
            String tagName = args.get(0);
            String content = TagStore.read(tagName);
            if (content != null) {
                bot.sendText(chatId, content);
            } else {
                bot.sendText(chatId, "❌ Tag not found: " + tagName);
            }
        }
    }

    @Override
    public String getName() {
        return "tag";
    }

    @Override
    public String getDescription() {
        return "Create or retrieve a tag";
    }
}
