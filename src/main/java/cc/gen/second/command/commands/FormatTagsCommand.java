package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.ArrayList;
import java.util.List;

public class FormatTagsCommand implements ICommand {

    @Override
    public void handle(CommandContext ctx) {
        if (!ctx.isSlash()) {
            ctx.getChannel().sendMessage("This command is slash-only. Use `/formattags` instead.").queue();
            return;
        }
        SlashCommandInteractionEvent event = ctx.getSlashEvent();
        String inputText = event.getOption("text").getAsString();

        String[] lines = inputText.split("\\r?\\n");
        List<String> tags = new ArrayList<>();

        for (String line : lines) {
            line = line.trim();
            if (line.equals("?") || line.isEmpty()) continue;

            String[] parts = line.split(" ");
            if (parts.length >= 2) {
                StringBuilder tag = new StringBuilder();
                for (int i = 0; i < parts.length - 1; i++) {
                    if (i > 0) tag.append(" ");
                    tag.append(parts[i]);
                }
                tags.add(tag.toString());
            }
        }

        String result = String.join(", ", tags);
        if (result.isEmpty()) {
            event.reply("❌ No valid tags found. Make sure the format is correct!").setEphemeral(true).queue();
        } else {
            event.reply("✅ Tags:\n```\n" + result + "\n```").queue();
        }
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("formattags", "Format ? tag count into comma-separated tags")
                .addOption(OptionType.STRING, "text", "Input tag data, like '?\\n2girls 1.2M'", true);
    }

    @Override
    public String getName() {
        return "ft";
    }

    @Override
    public String getDescription() {
        return "Takes tag lines like '?\\n2girls 1.2M' and returns a cleaned list";
    }

    @Override
    public String getHelp() {
        return "Use `/formattags text:<your list>` to parse a Danbooru-style tag-count list and extract tags only.";
    }
}
