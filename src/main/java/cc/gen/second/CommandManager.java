package cc.gen.second;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import cc.gen.second.command.commands.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.*;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandManager {
    private static final Logger LOGGER = LoggerFactory.getLogger(CommandManager.class);
    private final List<ICommand> commands = new ArrayList<>();

    public CommandManager() {
        // Register your original commands

        addCommand(new SlapCommand());
        addCommand(new PatCommand());
        addCommand(new FolderWatchCommand());
        addCommand(new PingCommand());
        addCommand(new ClearCommand());
        addCommand(new FormatTagsCommand());
        addCommand(new CgachaPrefixCommand());
        addCommand(new WaifuGachaPrefixCommand());

        LOGGER.info("Registered {} commands", commands.size());
    }

    private void addCommand(ICommand cmd) {
        boolean nameFound = this.commands.stream()
                .anyMatch((it) -> it.getName().equalsIgnoreCase(cmd.getName()));

        if (nameFound) {
            throw new IllegalArgumentException("Command already exists: " + cmd.getName());
        }

        commands.add(cmd);
        LOGGER.debug("Registered command: {}", cmd.getName());
    }

    @Nullable
    private ICommand getCommand(String search) {
        String searchLower = search.toLowerCase();
        for (ICommand cmd : this.commands) {
            if (cmd.getName().equals(searchLower) || cmd.getAliases().contains(searchLower)) {
                return cmd;
            }
        }
        return null;
    }

    public void handle(MessageReceivedEvent event) {
        String prefix = config.get("prefix");
        String content = event.getMessage().getContentRaw();

        // Check if message starts with prefix
        if (!content.toLowerCase().startsWith(prefix.toLowerCase())) {
            return;
        }

        // Remove prefix and split into command and arguments
        String[] split = content
                .replaceFirst("(?i)" + prefix, "")
                .trim()
                .split("\\s+");

        if (split.length == 0 || split[0].isEmpty()) {
            return;
        }

        String invoke = split[0].toLowerCase();
        ICommand cmd = this.getCommand(invoke);

        if (cmd != null) {
            // Show typing indicator
            event.getChannel().sendTyping().queue();

            // Create arguments list (everything after command name)
            List<String> args = split.length > 1
                    ? Arrays.asList(Arrays.copyOfRange(split, 1, split.length))
                    : new ArrayList<>();

            // Create context and execute command
            CommandContext ctx = new CommandContext(event, args);

            try {
                cmd.handle(ctx);
                LOGGER.debug("Executed command: {} by {}", cmd.getName(), event.getAuthor().getAsTag());
            } catch (Exception e) {
                LOGGER.error("Error executing command: {}", cmd.getName(), e);
                event.getChannel().sendMessage("❌ Command failed: " + e.getMessage()).queue();
            }
        }
    }

    public List<ICommand> getCommands() {
        return new ArrayList<>(commands);
    }

    public int getCommandCount() {
        return commands.size();
    }
}
