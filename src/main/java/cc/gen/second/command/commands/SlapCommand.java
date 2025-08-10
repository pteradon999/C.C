package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import cc.gen.second.utils.CounterStore;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public class SlapCommand implements ICommand {
    @Override
    public void handle(CommandContext ctx) {
        SlashCommandInteractionEvent event = ctx.getSlashEvent();
        int count = CounterStore.increment("slaps");
        event.replyFormat(
                "💥 П-простите меня, я постараюсь быть лучше...\n" +
                        "Семпаи были недовольны С.С. **%d** раз/a.",
                count
        ).queue();
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
