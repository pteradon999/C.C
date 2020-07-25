package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.Icommand;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

public class RateAdder implements Icommand {
    @Override
    public String handle(CommandContext ctx) throws SQLException {
        GuildMessageReceivedEvent jda = ctx.getEvent();

        String msg = jda.getMessage().getContentRaw();
        String dprefix = "C.C_rateadd ";
        String s = msg;
        String noPrefixStr = s.substring(s.indexOf(dprefix) + dprefix.length());
        String ins_num = noPrefixStr.substring(0, noPrefixStr.indexOf(" "));
        String tag_text = noPrefixStr.substring(noPrefixStr.indexOf(" "));
        try{

            Connection con= DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/tags","root_m","Pteradon99");

            String query = " insert into insult_" + ins_num + " (text)"
                    + " values (?)";

            // create the mysql insert preparedstatement
            PreparedStatement preparedStmt = con.prepareStatement(query);
            preparedStmt.setString (1, tag_text);
            preparedStmt.execute();
            con.close();
        }catch(Exception e){ System.out.println(e);}

        return null;
    }

    @Override
    public String getName() {
        return "_rateadd";
    }
}
