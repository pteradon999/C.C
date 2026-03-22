package cc.gen.second.telegram.commands;

import cc.gen.second.telegram.ITelegramCommand;
import cc.gen.second.telegram.TelegramBot;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.ArrayList;
import java.util.List;

public class FormatTagsTgCommand implements ITelegramCommand {

    @Override
    public void handle(TelegramBot bot, Message message, List<String> args) {
        if (args.isEmpty()) {
            bot.sendText(message.getChatId(),
                    "Использование: `/formattags <данные>`\nФормат входных данных — строки вида `tagname count`");
            return;
        }

        String inputText = String.join(" ", args);
        String[] lines = inputText.split("\\\\n|\\n");
        List<String> tags = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (line.equals("?") || line.isEmpty()) continue;
            String[] parts = line.split(" ");
            if (parts.length >= 2) {
                StringBuilder tag = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0) tag.append(" ");
                    tag.append(parts[i]);
                }
                tags.add(tag.toString());
            }
        }

        if (tags.isEmpty()) {
            bot.sendText(message.getChatId(), "❌ Не найдено валидных тегов.");
        } else {
            String result = String.join(", ", tags);
            bot.sendText(message.getChatId(), "✅ Tags:\n```\n" + result + "\n```");
        }
    }

    @Override
    public String getName() {
        return "formattags";
    }

    @Override
    public List<String> getAliases() {
        return List.of("ft");
    }

    @Override
    public String getDescription() {
        return "Форматировать теги из списка с количеством";
    }
}
