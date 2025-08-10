// src/main/java/cc/gen/second/commands/SlashCommand.java
package cc.gen.second.commands;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

public interface SlashCommand {
    // Called when the slash command is used
    void execute(SlashCommandInteractionEvent event);

    // Defines name, description, options for registration
    CommandData getCommandData();

    String getName();
    String getDescription();
}
