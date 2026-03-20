package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

public class ProbCommand implements ICommand {

    @Override
    public void handle(CommandContext ctx) {
        String subject = null;

        if (!ctx.isSlash()) {
            List<String> args = ctx.getArgs();
            if (args != null && !args.isEmpty()) {
                subject = String.join(" ", args);
            }
        }

        if (subject == null || subject.isBlank()) {
            String usage = "Использование: C.C_prob [текст]\nНапример: C.C_prob завтра будет солнце";
            if (ctx.isSlash()) {
                ctx.getSlashEvent().reply(usage).queue();
            } else {
                ctx.getChannel().sendMessage(usage).queue();
            }
            return;
        }

        Map<String, List<String>> responses = ProbData.getResponses();
        ThreadLocalRandom rng = ThreadLocalRandom.current();

        int percent = rng.nextInt(101); // 0-100 inclusive
        List<String> comments = responses.get(String.valueOf(percent));
        String comment = (comments != null && !comments.isEmpty())
                ? comments.get(rng.nextInt(comments.size()))
                : "";

        String message = "Я считаю, что вероятность того, что " + subject
                + " — " + percent + "% " + comment;

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
        return "_prob";
    }

    @Override
    public String getDescription() {
        return "Predict the probability of anything with C.C.'s wisdom";
    }

    @Override
    public String getHelp() {
        return "Usage: C.C_prob [text] — predicts probability of something happening";
    }
}
