package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.Icommand;
import net.dv8tion.jda.api.JDA;

import java.sql.*;

public class SlapCommand implements Icommand {
    @Override
    public String handle(CommandContext ctx) throws SQLException {

        try (Connection con = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/tags", "root_m", "Pteradon99")) {
            // the mysql insert statement
            ResultSet rs = null;
            int cnt = 0;
            Statement preparedStmt = null;
            String count = "SELECT slaps FROM tags.love";
            preparedStmt = con.createStatement();
            rs = preparedStmt.executeQuery(count);
            while (rs.next()) {
                cnt = rs.getInt("slaps");
            }
            String query = "UPDATE tags.love SET slaps = slaps + 1";
            PreparedStatement upd = con.prepareStatement(query);
            upd.execute();
            JDA jda = ctx.getJDA();
            int finalCnt = cnt;
            jda.getRestPing().queue(
                    (ping) -> ctx.getChannel()
                            .sendMessageFormat("*П-простите меня, я постараюсь быть лучше...*\n *Семпаи были недовольны С.С. уже " + finalCnt + " раз/a*").queue());

        }
        return null;
    }
    @Override
    public String getName(){
        return "_slap";
    }

}
