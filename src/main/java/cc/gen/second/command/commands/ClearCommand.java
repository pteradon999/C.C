package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.Icommand;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClearCommand implements Icommand {
    @Override
    public String handle(CommandContext ctx) throws SQLException {
        GuildMessageReceivedEvent jda = ctx.getEvent();
        String msg = jda.getMessage().getContentRaw();
        String dprefix = "C.C_purge ";
        String s = msg;
        Integer noPrefixStr = Integer.valueOf(s.substring(s.indexOf(dprefix) + dprefix.length()))+2;
        String permission ="MANAGE_MESSAGES";
        JDA jdaa = ctx.getJDA();
        int delay = 200;

        if (jda.getMember().hasPermission(Permission.MANAGE_CHANNEL)) {
            try {
                ExCountdown(jda, delay);
                if (noPrefixStr > 100) {
                    for (int i = 1; i <= noPrefixStr / 100; i++) {
                        List<Message> messages = jda.getChannel().getHistory().retrievePast(100).complete();
                        jda.getChannel().deleteMessages(messages).queueAfter(delay, TimeUnit.MILLISECONDS);
                    }
                    List<Message> messages = jda.getChannel().getHistory().retrievePast(noPrefixStr % 100).complete();
                    jda.getChannel().deleteMessages(messages).queueAfter(delay, TimeUnit.MILLISECONDS);
                } else {
                    List<Message> messages = jda.getChannel().getHistory().retrievePast(noPrefixStr).complete();
                    jda.getChannel().deleteMessages(messages).queueAfter(delay, TimeUnit.MILLISECONDS);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            jdaa.getRestPing().queue(
                    (ping) -> ctx.getChannel()
                            .sendMessageFormat("***You have no rights to authorize Exterminatus!***").queue());
        }
        return null;
    }


    @Override
    public String getName() {
        return "_purge";
    }
    public void ExCountdown(GuildMessageReceivedEvent ctx, int delay) throws InterruptedException {
        JDA jda = ctx.getJDA();
        ctx.getChannel().sendMessage("***Exterminatus authorized \n Commencing Now***").queue();
    }
}


