package cc.gen.second.telegram.commands;

import cc.gen.second.telegram.ITelegramCommand;
import cc.gen.second.telegram.TelegramBot;
import cc.gen.second.telegram.commands.shared.TgRatingData;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class RateitTgCommand implements ITelegramCommand {

    @Override
    public void handle(TelegramBot bot, Message message, List<String> args) {
        Map<String, List<String>> ratings = TgRatingData.getRatings();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int tier = TgRatingData.rollWeightedTier(rng);
        List<String> comments = ratings.get(String.valueOf(tier));
        String comment = (comments != null && !comments.isEmpty())
                ? comments.get(rng.nextInt(comments.size()))
                : tier + "/10";

        String subject = args.isEmpty() ? null : String.join(" ", args);

        String reply;
        if (subject != null && !subject.isBlank()) {
            reply = "I would rate " + subject + " as " + comment;
        } else {
            reply = comment;
        }

        bot.sendText(message.getChatId(), reply);
    }

    @Override
    public String getName() {
        return "rateit";
    }

    @Override
    public String getDescription() {
        return "Rate anything from 0 to 10";
    }
}
