package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RatemeCommand implements ICommand {

    @Override
    public void handle(CommandContext ctx) {
        User author = ctx.getAuthor();
        boolean selfRate;
        User target;

        if (ctx.isSlash()) {
            target = author;
            selfRate = true;
        } else {
            List<User> mentioned = ctx.getMessageEvent().getMessage().getMentions().getUsers();
            if (!mentioned.isEmpty()) {
                target = mentioned.get(0);
                selfRate = target.getIdLong() == author.getIdLong();
            } else {
                target = author;
                selfRate = true;
            }
        }

        long seed = Long.hashCode(target.getIdLong()) ^ LocalDate.now().hashCode();
        Random rng = new Random(seed);

        Map<String, List<String>> ratings = RatingData.getRatings();
        int tier = RatingData.rollWeightedTier(rng);
        List<String> comments = ratings.get(String.valueOf(tier));
        if (comments == null || comments.isEmpty()) {
            sendReply(ctx, tier + "/10");
            return;
        }

        String comment = comments.get(rng.nextInt(comments.size()));

        String message;
        if (selfRate) {
            message = comment;
        } else {
            String displayName = target.getEffectiveName();
            message = "I would rate " + displayName + " as " + comment;
        }

        sendReply(ctx, message);
    }

    private void sendReply(CommandContext ctx, String message) {
        if (ctx.isSlash()) {
            ctx.getSlashEvent().reply(message).queue();
        } else {
            ctx.getChannel().sendMessage(message).queue();
        }
    }

    @Override
    public CommandData getCommandData() {
        return null;
    }

    @Override
    public String getName() {
        return "_rateme";
    }

    @Override
    public String getDescription() {
        return "Rate a user from 0 to 10 with C.C.'s commentary (same result per day)";
    }

    @Override
    public String getHelp() {
        return "Usage: C.C_rateme or C.C_rateme @user";
    }
}
