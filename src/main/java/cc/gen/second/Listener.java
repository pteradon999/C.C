package cc.gen.second;

import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.*;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.jetbrains.annotations.*;
import javax.annotation.*;
import java.sql.*;

public class Listener extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(Listener.class);
    private final CommandManager manager = new CommandManager();

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        LOGGER.info("{} is ready", event.getJDA().getSelfUser().getAsTag());
       /* try {
            Connection con = DriverManager.getConnection(
                    "jdbc:mysql://localhost:3306/tags?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    "root_m", "Pteradon99");
            LOGGER.info("Database connection achieved");
            con.close();
        } catch (Exception e) {
            LOGGER.error("Database connection failed", e);
        } */
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        // Skip if bot message, webhook, or not from guild
        if (event.getAuthor().isBot() || event.isWebhookMessage() || !event.isFromGuild()) {
            return;
        }

        User user = event.getAuthor();
        String prefix = config.get("prefix");
        String tagPrefix = config.get("tag_prefix");
        String raw = event.getMessage().getContentRaw();
        MessageChannel channel = event.getChannel();
        Message msg = event.getMessage();

        // Shutdown command (owner only)
        if (raw.equalsIgnoreCase(prefix + "_shutdown")
                && user.getId().equals(config.get("owner_id"))) {
            LOGGER.info("Shutdown command received from owner");
            channel.sendMessage("🔄 Shutting down...").queue();
            event.getJDA().shutdown();
            return;
        }

        // Tag creation: C!C create <name> <content>
        if (raw.startsWith(tagPrefix + " create ")) {
            String createPrefix = tagPrefix + " create ";
            String content = raw.substring(createPrefix.length());

            int spaceIndex = content.indexOf(" ");
            if (spaceIndex > 0) {
                String tagName = content.substring(0, spaceIndex);
                String tagText = content.substring(spaceIndex + 1);

                createTag(user.getName(), tagName, tagText);
                channel.sendMessage("✅ **Tag created successfully!**").queue();
            } else {
                channel.sendMessage("❌ Usage: `" + tagPrefix + " create <name> <content>`").queue();
            }
            return;
        }

        // Tag retrieval: C!C <name>
        if (raw.startsWith(tagPrefix + " ")) {
            String tagName = raw.substring((tagPrefix + " ").length());
            String tagContent = readTag(tagName);

            if (!tagContent.equals("succsessfullyfailed")) {
                channel.sendMessage(tagContent).queue();
            } else {
                channel.sendMessage("❌ Tag not found: " + tagName).queue();
            }
            return;
        }

        // Command handling
        if (raw.startsWith(prefix)) {
            try {
                manager.handle(event);
            } catch (SQLException e) {
                LOGGER.error("Error handling command", e);
                channel.sendMessage("❌ Command failed: " + e.getMessage()).queue();
            }
        }
    }

    private String readTag(String tagName) {
        String result = "succsessfullyfailed";

        try (Connection con = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/tags?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "root_m", "Pteradon99")) {

            // Use prepared statement to prevent SQL injection
            String query = "SELECT tag_text FROM ttags WHERE tag_name = ?";
            try (PreparedStatement stmt = con.prepareStatement(query)) {
                stmt.setString(1, tagName);
                ResultSet rs = stmt.executeQuery();

                if (rs.next()) {
                    result = rs.getString("tag_text");
                }
            }
        } catch (Exception e) {
            LOGGER.error("Error reading tag: " + tagName, e);
        }

        return result;
    }

    private void createTag(String author, String tagName, String tagText) {
        try (Connection con = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/tags?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                "root_m", "Pteradon99")) {

            String query = "INSERT INTO ttags (tag_author, tag_name, tag_text) VALUES (?, ?, ?)";
            try (PreparedStatement stmt = con.prepareStatement(query)) {
                stmt.setString(1, author);
                stmt.setString(2, tagName);
                stmt.setString(3, tagText);
                stmt.executeUpdate();
                LOGGER.info("Created tag '{}' by {}", tagName, author);
            }
        } catch (Exception e) {
            LOGGER.error("Error creating tag: " + tagName, e);
        }
    }
}
