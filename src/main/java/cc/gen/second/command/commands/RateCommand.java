package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.Icommand;
import net.dv8tion.jda.api.JDA;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Random;

public class RateCommand implements Icommand {


    @Override
    public String handle(CommandContext ctx) {
        Random r = new Random();
        String output = new String();
        //Integer cnt = new Integer;
        try{

            try (Connection con = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/tags", "root_m", "Pteradon99")) {
                // the mysql insert statement
                ResultSet rs = null;
                int cnt = 0;
                Statement preparedStmt = null;
                String count = "SELECT SUM(TABLE_ROWS) FROM INFORMATION_SCHEMA.TABLES WHERE TABLE_SCHEMA = 'tags.insult_" + r.nextInt(10) + "'";
                System.out.println(count);
                preparedStmt = con.createStatement();
                rs = preparedStmt.executeQuery(count);
                while (rs.next()) {
                 cnt = rs.getInt("SUM(TABLE_ROWS)");
              }
                int k = r.nextInt(cnt);
              //  String query = "SELECT text FROM tags.insult_ + r WHERE idinsult_+r= " + "'" +  + "'";
                // create the mysql insert preparedstatement
                preparedStmt = con.createStatement();
                //rs = preparedStmt.executeQuery(query);
                while (rs.next()) output = rs.getString("tag_text");
                con.close();
            }

        }catch(Exception e){ System.out.println(e);}

        JDA jda = ctx.getJDA();
        jda.getRestPing().queue(
                (ping) -> ctx.getChannel()
                        .sendMessageFormat("It worked through pain").queue());
        return output;
    }

    @Override
    public String getName() {
        return "_rateit";
    }
}
