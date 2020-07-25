package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.Icommand;
import net.dv8tion.jda.api.JDA;

import java.sql.*;

public class PatCommand implements Icommand {
    @Override
    public String handle(CommandContext ctx) throws SQLException {

        try (Connection con = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/tags", "root_m", "Pteradon99")) {
            // the mysql insert statement
            ResultSet rs = null;
            int cnt = 0;
            Statement preparedStmt = null;
            String count = "SELECT pats FROM tags.love";
            preparedStmt = con.createStatement();
            rs = preparedStmt.executeQuery(count);
            while (rs.next()) {
                cnt = rs.getInt("pats");
            }
            String query = "UPDATE tags.love SET pats = pats + 1";
            PreparedStatement upd = con.prepareStatement(query);
            upd.execute();
            JDA jda = ctx.getJDA();
            int finalCnt = cnt;
            jda.getRestPing().queue(
                    (ping) -> ctx.getChannel()
                            .sendMessageFormat("*Спасибо вам, семпай "+ ctx.getAuthor().getAsMention() + "* \n" + "*Семпаи погладили С.С. " + finalCnt + " раз/a* \n" +
                                    "http://pa1.narvii.com/5807/1e8d5eea1a2c2a4ac8ce35e8ddb06730810e70b4_hq.gif").queue());

        }
        return null;
    }


    @Override
    public String getName() {
        return "_pat";
    }
}
