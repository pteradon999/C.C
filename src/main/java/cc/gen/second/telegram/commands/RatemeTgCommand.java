package cc.gen.second.telegram.commands;

import cc.gen.second.telegram.ITelegramCommand;
import cc.gen.second.telegram.TelegramBot;
import cc.gen.second.telegram.commands.shared.TgRatingData;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RatemeTgCommand implements ITelegramCommand {

    @Override
    public void handle(TelegramBot bot, Message message, List<String> args) {
        long userId = message.getFrom().getId();
        String displayName = message.getFrom().getFirstName();

        // Seed by user ID + date so the rating is stable per day
        long seed = Long.hashCode(userId) ^ LocalDate.now().hashCode();
        Random rng = new Random(seed);

        Map<String, List<String>> ratings = TgRatingData.getRatings();
        int tier = TgRatingData.rollWeightedTier(rng);
        List<String> comments = ratings.get(String.valueOf(tier));

        String comment;
        if (comments == null || comments.isEmpty()) {
            comment = tier + "/10";
        } else {
            comment = comments.get(rng.nextInt(comments.size()));
        }

        bot.sendText(message.getChatId(), comment);
    }

    @Override
    public String getName() {
        return "rateme";
    }

    @Override
    public String getDescription() {
        return "Rate you from 0 to 10 (result is stable throughout the day)";
    }
}
