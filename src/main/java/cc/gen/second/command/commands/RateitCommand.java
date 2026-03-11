package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class RateitCommand implements ICommand {

    @Override
    public void handle(CommandContext ctx) {
        Map<String, List<String>> ratings = RatingData.getRatings();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int tier = RatingData.rollWeightedTier(rng);
        List<String> comments = ratings.get(String.valueOf(tier));
        String comment = (comments != null && !comments.isEmpty())
                ? comments.get(rng.nextInt(comments.size()))
                : tier + "/10";

        String subject = null;

        if (!ctx.isSlash()) {
            List<User> mentioned = ctx.getMessageEvent().getMessage().getMentions().getUsers();
            if (!mentioned.isEmpty()) {
                User target = mentioned.get(0);
                if (target.getIdLong() != ctx.getAuthor().getIdLong()) {
                    subject = target.getEffectiveName();
                }
            } else {
                List<String> args = ctx.getArgs();
                if (args != null && !args.isEmpty()) {
                    subject = String.join(" ", args);
                }
            }
        }

        String message;
        if (subject != null && !subject.isBlank()) {
            message = "Я бы оценила " + subject + " как " + comment;
        } else {
            message = comment;
        }

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
        return "_rateit";
    }

    @Override
    public String getDescription() {
        return "Rate anything or anyone from 0 to 10 with C.C.'s commentary";
    }

    @Override
    public String getHelp() {
        return "Usage: C.C_rateit, C.C_rateit @user, or C.C_rateit <anything>";
    }
}
