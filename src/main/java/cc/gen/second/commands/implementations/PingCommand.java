package cc.gen.second.commands.implementations;

import cc.gen.second.commands.SlashCommand;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;

public class PingCommand implements SlashCommand {

    @Override
    public void execute(SlashCommandInteractionEvent event) {
        // Defer the reply so we can measure response time
        long start = System.currentTimeMillis();
        event.deferReply().queue(hook -> {
            long responseTime = System.currentTimeMillis() - start;
            long gatewayPing = event.getJDA().getGatewayPing();

            hook.editOriginalFormat(
                    "🏓 **Pong!**\n" +
                            "📡 Response Time: %d ms\n" +
                            "🌐 Gateway Ping: %d ms",
                    responseTime, gatewayPing
            ).queue();
        });
    }

    @Override
    public CommandData getCommandData() {
        // Register this as a /ping command
        return Commands.slash(getName(), getDescription());
    }

    @Override
    public String getName() {
        return "ping";
    }

    @Override
    public String getDescription() {
        return "Shows bot latency";
    }
}
