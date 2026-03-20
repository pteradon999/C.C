package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import cc.gen.second.utils.CounterStore;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public class SlapCommand implements ICommand {
    @Override
    public void handle(CommandContext ctx) {
        int count = CounterStore.increment("slaps");
        String message = String.format(
                "💥 П-простите меня, я постараюсь быть лучше...\n" +
                        "Семпаи были недовольны С.С. **%d** раз/a.",
                count
        );
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
        return "slap";
    }

    @Override
    public String getDescription() {
        return "Slap the bot (increments slap counter)";
    }

    @Override
    public String getHelp() {
        return "";
    }
}
