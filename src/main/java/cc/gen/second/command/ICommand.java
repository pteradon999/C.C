package cc.gen.second.command;

import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.sql.SQLException;
import java.util.List;

/**
 * Interface for all bot commands
 */
public interface ICommand {

    /**
     * Handle command execution
     * @param ctx The command context
     * @throws SQLException if database error occurs
     */
    void handle(CommandContext ctx) throws SQLException;

    CommandData getCommandData();

    /**
     * Get the command name
     * @return Command name
     */
    String getName();

    /**
     * Get command aliases
     * @return List of aliases (empty list if none)
     */
    default List<String> getAliases() {
        return List.of();
    }

    /**
     * Get command description
     * @return Command description
     */
    default String getDescription() {
        return "No description provided";
    }

    /**
     * Get command usage
     * @return Usage example
     */
    default String getUsage() {
        return getName();
    }

    String getHelp();
}
