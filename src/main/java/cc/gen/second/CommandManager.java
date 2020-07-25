package cc.gen.second;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.Icommand;
import cc.gen.second.command.commands.*;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import javax.annotation.Nullable;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class CommandManager {
    private final List<Icommand> commands = new ArrayList<>();

    public CommandManager() {
        addCommand(new PingCommand());
        addCommand(new RateCommand());
        addCommand(new SlapCommand());
        addCommand(new PatCommand());
        addCommand(new RateAdder());
        addCommand(new ClearCommand());
    }



    private void  addCommand(Icommand cmd) {
        boolean nameFound = this.commands.stream().anyMatch((it) -> it.getName().equalsIgnoreCase(cmd.getName()));
        if (nameFound) {
            throw new IllegalArgumentException("Command already exist");

        }
        commands.add(cmd);
    }
    @Nullable
    private Icommand getCommand(String search){
        String searchLower = search.toLowerCase();
        for (Icommand cmd: this.commands){
            if (cmd.getName().equals(searchLower) || cmd.getAliases().contains(searchLower)) {
                return cmd;
            }
        }
    return null;
    }
    public void handle(GuildMessageReceivedEvent event) throws SQLException {
        String[] split = event.getMessage().getContentRaw()
                .replaceFirst("(?i)"+(config.get("prefix")), "")
                .split("\\s+");

     String invoke = split[0].toLowerCase();
     Icommand cmd = this.getCommand(invoke);

     if (cmd != null){
         event.getChannel().sendTyping().queue();
         List<String> args = Arrays.asList(split).subList(1,split.length);

         CommandContext ctx = new CommandContext(event, args);
         cmd.handle(ctx);
     }
    }
}
