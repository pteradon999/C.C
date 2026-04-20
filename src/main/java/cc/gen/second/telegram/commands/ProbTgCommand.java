package cc.gen.second.telegram.commands;

import cc.gen.second.telegram.ITelegramCommand;
import cc.gen.second.telegram.TelegramBot;
import cc.gen.second.telegram.commands.shared.TgProbData;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ProbTgCommand implements ITelegramCommand {

    @Override
    public void handle(TelegramBot bot, Message message, List<String> args) {
        if (args.isEmpty()) {
            bot.sendText(message.getChatId(),
                    "Usage: /prob [text]\nExample: /prob tomorrow it will be sunny");
            return;
        }

        String subject = String.join(" ", args);

        Map<String, List<String>> responses = TgProbData.getResponses();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int percent = rng.nextInt(101);
        List<String> comments = responses.get(String.valueOf(percent));
        String comment = (comments != null && !comments.isEmpty())
                ? comments.get(rng.nextInt(comments.size()))
                : "";

        String reply = "I believe the probability of " + subject
                + " is " + percent + "% " + comment;

        bot.sendText(message.getChatId(), reply);
    }

    @Override
    public String getName() {
        return "prob";
    }

    @Override
    public String getDescription() {
        return "Predict the probability of an event";
    }
}
