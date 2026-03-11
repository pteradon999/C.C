package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

import java.util.Collections;
import java.util.List;

public class PingCommand implements ICommand {

    @Override
    public void handle(CommandContext ctx) {
        long gatewayPing = ctx.isSlash()
                ? ctx.getSlashEvent().getJDA().getGatewayPing()
                : ctx.getMessageEvent().getJDA().getGatewayPing();

        String reply = "Pong! `" + gatewayPing + " ms`";

        if (ctx.isSlash()) {
            ctx.getSlashEvent().reply(reply).queue();
        } else {
            ctx.getChannel().sendMessage(reply).queue();
        }
    }

    @Override
    public String getName() {
        // IMPORTANT: no prefix here — CommandManager strips the prefix already
        return "ping";
    }

    @Override
    public List<String> getAliases() {
        return Collections.singletonList("pong");
    }

    @Override
    public String getHelp() {
        return "Checks bot latency. Usage: `"+ /* your prefix here if you print it */ "ping`";
    }

    @Override
    public CommandData getCommandData() {
        // Optional: enables /ping as a slash command if you register it elsewhere
        return Commands.slash("ping", "Checks bot latency");
    }
}
