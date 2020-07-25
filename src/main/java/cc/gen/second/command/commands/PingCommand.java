package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.Icommand;
import net.dv8tion.jda.api.JDA;

public class PingCommand implements Icommand {
    @Override
    public String handle (CommandContext ctx) {
        JDA jda = ctx.getJDA();

        jda.getRestPing().queue(
                (ping) -> ctx.getChannel()
                .sendMessageFormat("Reset ping %sms\nWS ping %sms",ping,jda.getGatewayPing()).queue()
        );
        return null;
    }

    @Override
    public String getName(){
        return "_ping";
    }

}
