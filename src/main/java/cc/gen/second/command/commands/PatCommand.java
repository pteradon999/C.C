package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import cc.gen.second.utils.CounterStore;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public class PatCommand implements ICommand {
    @Override
    public void handle(CommandContext ctx) {
        SlashCommandInteractionEvent event = ctx.getSlashEvent();
        int count = CounterStore.increment("pats");
        event.replyFormat(
                "🥰 Спасибо вам, семпай %s!\n" +
                        "Семпаи погладили С.С. **%d** раз(a).\n" +
                        "http://pa1.narvii.com/5807/1e8d5eea1a2c2a4ac8ce35e8ddb06730810e70b4_hq.gif",
                event.getUser().getAsMention(), count
        ).queue();
    }

    @Override
    public CommandData getCommandData() {
        return null;
    }

    @Override
    public String getName() {
        return "pat";
    }

    @Override
    public String getDescription() {
        return "Pat the bot (increments pat counter)";
    }

    @Override
    public String getHelp() {
        return "";
    }
}
