package cc.gen.second;

import me.duncte123.botcommons.BotCommons;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.MessageChannel;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.ReadyEvent;
import net.dv8tion.jda.api.events.message.guild.GuildMessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.annotation.Nonnull;
import java.sql.*;

public class Listener extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(Listener.class);
    private final CommandManager manager = new CommandManager();
    @Override
    public void onReady(@Nonnull ReadyEvent event) {
        LOGGER.info("{} is ready", event.getJDA().getSelfUser().getAsTag());
        try{
            Connection con= DriverManager.getConnection("jdbc:mysql://localhost/tags?UseSSL = false","root_m","Pteradon99");
//here sonoo is database name, root is username and password
            LOGGER.info("Connection achieved");
            con.close();
        }catch(Exception e){ System.out.println(e);
        }
    }

    @Override
    public void onGuildMessageReceived(@Nonnull GuildMessageReceivedEvent event) {
        User user = event.getAuthor();

        if (user.isBot() || event.isWebhookMessage()) {
            return;
        }
        User User = event.getAuthor();
        String prefix = config.get("prefix");
        String tag_prefix = config.get("tag_prefix");
        String raw = event.getMessage().getContentRaw();
        MessageChannel channel = event.getChannel();
        Message Msg = event.getMessage();
        if (raw.equalsIgnoreCase(prefix + "_shutdown")
                && user.getId().equals(config.get("owner_id"))) {
            LOGGER.info("Shutting down");
            event.getJDA().shutdown();
            BotCommons.shutdown(event.getJDA());

            return;
        }
        if (raw.contains(tag_prefix +" create ")){
            String dprefix = "C!C create ";
            String s = Msg.getContentStripped();
            String noPrefixStr = s.substring(s.indexOf(dprefix) + dprefix.length());
            String tag_name = noPrefixStr.substring(0, noPrefixStr.indexOf(" "));
            String tag_text = noPrefixStr.substring(noPrefixStr.indexOf(" "));
            Createtag(User.getName(),tag_name,tag_text);
            channel.sendMessage("***Tag created***").queue();

        } else {
            if (raw.startsWith(tag_prefix)) {
                String dprefix = "C!C ";
                String s = Msg.getContentStripped();
                String noPrefixStr = s.substring(s.indexOf(dprefix) + dprefix.length());// Блок достает из команды тег
                channel.sendMessage(Readtag(User.getName(), noPrefixStr)).queue();
            }
        }

        if (raw.startsWith(prefix)) {
            try {
                manager.handle(event);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
    }

    private String Readtag(String name, String noPrefixStr) {
        String Rtext = "succsessfullyfailed";
        try{

            Connection con= DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/tags","root_m","Pteradon99");
//here sonoo is database name, root is username and password
            // the mysql insert statement
            ResultSet rs = null;
            Statement preparedStmt = null;
            String query = "SELECT tag_text FROM tags.ttags WHERE tag_name="+ "'" + noPrefixStr + "'";

            // create the mysql insert preparedstatement
            preparedStmt = con.createStatement();
            rs = preparedStmt.executeQuery(query);
            while(rs.next()){
                Rtext = rs.getString("tag_text");

            }
            con.close();
            return Rtext;
        }catch(Exception e){ System.out.println(e);}
        return Rtext;
    }

    private void Createtag(String name, String tag_name, String tag_text) {
        try{

            Connection con= DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/tags","root_m","Pteradon99");

            String query = " insert into ttags (tag_author, tag_name, tag_text)"
                    + " values (?, ?, ?)";

            // create the mysql insert preparedstatement
            PreparedStatement preparedStmt = con.prepareStatement(query);
            preparedStmt.setString (1, name);
            preparedStmt.setString (2, tag_name);
            preparedStmt.setString (3, tag_text);
            preparedStmt.execute();
            con.close();
        }catch(Exception e){ System.out.println(e);}
    }


}
