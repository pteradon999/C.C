package cc.gen.second;

import cc.gen.second.utils.TagStore;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Listener extends ListenerAdapter {

    private static final Logger LOGGER = LoggerFactory.getLogger(Listener.class);
    private final CommandManager manager = new CommandManager();

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        LOGGER.info("{} is ready", event.getJDA().getSelfUser().getAsTag());
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot() || event.isWebhookMessage() || !event.isFromGuild()) {
            return;
        }

        User user = event.getAuthor();
        String prefix = config.get("prefix");
        String tagPrefix = config.get("tag_prefix");
        if (prefix == null || tagPrefix == null) {
            LOGGER.warn("prefix or tag_prefix not configured — ignoring message");
            return;
        }
        String raw = event.getMessage().getContentRaw();
        MessageChannel channel = event.getChannel();

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
                TagStore.create(user.getName(), tagName, tagText);
                channel.sendMessage("✅ **Tag created successfully!**").queue();
            } else {
                channel.sendMessage("❌ Usage: `" + tagPrefix + " create <name> <content>`").queue();
            }
            return;
        }

        // Tag retrieval: C!C <name>
        if (raw.startsWith(tagPrefix + " ")) {
            String tagName = raw.substring((tagPrefix + " ").length());
            String tagContent = TagStore.read(tagName);

            if (tagContent != null) {
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
            } catch (Exception e) {
                LOGGER.error("Error handling command", e);
                channel.sendMessage("❌ Command failed: " + e.getMessage()).queue();
            }
        }
    }
}
