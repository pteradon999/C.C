package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class RateitCommand implements ICommand {

    @Override
    public void handle(CommandContext ctx) {
        String subject;
        if (ctx.isSlash()) {
            subject = null;
        } else {
            List<String> args = ctx.getArgs();
            subject = (args != null && !args.isEmpty()) ? String.join(" ", args) : null;
        }

        if (subject == null || subject.isBlank()) {
            sendReply(ctx, "Использование: C.C_rateit <что-нибудь>");
            return;
        }

        Map<String, List<String>> ratings = RatingData.getRatings();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int tier = RatingData.rollWeightedTier(rng);
        List<String> comments = ratings.get(String.valueOf(tier));
        if (comments == null || comments.isEmpty()) {
            sendReply(ctx, "Я бы оценила " + subject + " как " + tier + "/10");
            return;
        }

        String comment = comments.get(rng.nextInt(comments.size()));
        sendReply(ctx, "Я бы оценила " + subject + " как " + comment);
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
        return "_rateit";
    }

    @Override
    public String getDescription() {
        return "Rate anything from 0 to 10 with C.C.'s commentary";
    }

    @Override
    public String getHelp() {
        return "Usage: C.C_rateit <anything>";
    }
}
