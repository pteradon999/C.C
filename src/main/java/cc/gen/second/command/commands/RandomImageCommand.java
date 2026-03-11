package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import cc.gen.second.config;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class RandomImageCommand implements ICommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(RandomImageCommand.class);

    // Cache: channel ID -> list of image URLs
    private final ConcurrentHashMap<String, List<String>> imageCache = new ConcurrentHashMap<>();

    // Max messages to scan when building the cache
    private static final int MAX_HISTORY_SCAN = 1000;

    @Override
    public void handle(CommandContext ctx) {
        // Handle "refresh" subcommand for prefix commands
        if (!ctx.isSlash() && ctx.getArgs() != null && !ctx.getArgs().isEmpty()
                && ctx.getArgs().get(0).equalsIgnoreCase("refresh")) {
            String refreshTarget = ctx.getArgs().size() > 1 ? ctx.getArgs().get(1) : config.get("RANDOM_IMAGE_CHANNEL_ID");
            if (refreshTarget != null) {
                // Support <#id> mention format
                if (refreshTarget.startsWith("<#") && refreshTarget.endsWith(">")) {
                    refreshTarget = refreshTarget.substring(2, refreshTarget.length() - 1);
                }
                imageCache.remove(refreshTarget);
                reply(ctx, "Cache cleared for channel `" + refreshTarget + "`. Next use will re-fetch.");
            } else {
                reply(ctx, "No channel to refresh. Provide a channel ID or set `RANDOM_IMAGE_CHANNEL_ID` in .env.");
            }
            return;
        }

        // Determine source channel ID
        String channelId = resolveChannelId(ctx);
        if (channelId == null || channelId.isEmpty()) {
            reply(ctx, "No source channel specified. Use `_randimg <channel_id>` or set `RANDOM_IMAGE_CHANNEL_ID` in .env.");
            return;
        }

        // Resolve the channel from the bot's JDA instance
        TextChannel sourceChannel;
        if (ctx.isSlash()) {
            sourceChannel = ctx.getSlashEvent().getJDA().getTextChannelById(channelId);
        } else {
            sourceChannel = ctx.getMessageEvent().getJDA().getTextChannelById(channelId);
        }

        if (sourceChannel == null) {
            reply(ctx, "Could not find text channel with ID `" + channelId + "`. Make sure the bot has access to it.");
            return;
        }

        // Check cache first
        List<String> cached = imageCache.get(channelId);
        if (cached != null && !cached.isEmpty()) {
            sendRandomImage(ctx, cached);
            return;
        }

        // Fetch and cache
        reply(ctx, "Building image cache for **#" + sourceChannel.getName() + "**... this may take a moment.");

        sourceChannel.getIterableHistory()
                .takeAsync(MAX_HISTORY_SCAN)
                .thenAccept(messages -> {
                    List<String> urls = new ArrayList<>();
                    for (Message msg : messages) {
                        for (Message.Attachment att : msg.getAttachments()) {
                            if (att.isImage()) {
                                urls.add(att.getUrl());
                            }
                        }
                    }

                    if (urls.isEmpty()) {
                        ctx.getChannel().sendMessage("No images found in **#" + sourceChannel.getName() + "**.").queue();
                        return;
                    }

                    imageCache.put(channelId, Collections.unmodifiableList(urls));
                    LOGGER.info("Cached {} image URLs from #{} ({})", urls.size(), sourceChannel.getName(), channelId);
                    sendRandomImage(ctx, urls);
                })
                .exceptionally(err -> {
                    LOGGER.error("Failed to fetch history from channel {}", channelId, err);
                    ctx.getChannel().sendMessage("Failed to fetch channel history: " + err.getMessage()).queue();
                    return null;
                });
    }

    private String resolveChannelId(CommandContext ctx) {
        // Try command argument first
        if (ctx.isSlash()) {
            var opt = ctx.getSlashEvent().getOption("channel");
            if (opt != null) {
                return opt.getAsChannel().getId();
            }
        } else {
            List<String> args = ctx.getArgs();
            if (args != null && !args.isEmpty()) {
                String arg = args.get(0).trim();
                // Support both raw ID and <#123456> mention format
                if (arg.startsWith("<#") && arg.endsWith(">")) {
                    return arg.substring(2, arg.length() - 1);
                }
                return arg;
            }
        }

        // Fall back to .env default
        return config.get("RANDOM_IMAGE_CHANNEL_ID");
    }

    private void sendRandomImage(CommandContext ctx, List<String> urls) {
        String url = urls.get(ThreadLocalRandom.current().nextInt(urls.size()));
        ctx.getChannel().sendMessage(url).queue();
    }

    private void reply(CommandContext ctx, String text) {
        if (ctx.isSlash()) {
            ctx.getSlashEvent().reply(text).queue();
        } else {
            ctx.getChannel().sendMessage(text).queue();
        }
    }

    @Override
    public String getName() {
        return "_randimg";
    }

    @Override
    public List<String> getAliases() {
        return List.of("randimg", "randomimage", "_randomimage");
    }

    @Override
    public String getHelp() {
        return "Posts a random image from a specified channel. " +
                "Usage: `_randimg [channel_id]` — defaults to RANDOM_IMAGE_CHANNEL_ID from .env. " +
                "Use `_randimg refresh [channel_id]` to rebuild the cache.";
    }

    @Override
    public String getDescription() {
        return "Post a random image from a channel";
    }

    @Override
    public CommandData getCommandData() {
        return Commands.slash("randimg", "Post a random image from a channel")
                .addOptions(new OptionData(OptionType.CHANNEL, "channel", "Source channel to pick from (defaults to configured channel)", false));
    }

    /**
     * Clears the image cache for a channel, forcing a re-fetch on next use.
     */
    public void refreshCache(String channelId) {
        imageCache.remove(channelId);
    }
}
