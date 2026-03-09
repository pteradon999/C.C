package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import cc.gen.second.config;
import cc.gen.second.utils.DeviantArtClient;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Posts an image to DeviantArt from Discord.
 *
 * Usage: _dapost [image_url] --name "Title" --tags "tag1,tag2" --ai --mature --desc "Description"
 *
 * The image can be provided as:
 *  - A URL argument
 *  - An attachment on the Discord message
 *
 * Owner-only command.
 */
public class DeviantArtPostCommand implements ICommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(DeviantArtPostCommand.class);
    private static final String OWNER_ID = config.get("OWNER_ID");

    private final DeviantArtClient daClient = new DeviantArtClient();

    @Override
    public void handle(CommandContext ctx) {
        // Owner-only check
        if (!ctx.getAuthor().getId().equals(OWNER_ID)) {
            ctx.getChannel().sendMessage("You are not authorized to use this command.").queue();
            return;
        }

        if (!daClient.isConfigured()) {
            ctx.getChannel().sendMessage("DeviantArt API is not configured. Set DA_CLIENT_ID, DA_CLIENT_SECRET, and DA_REFRESH_TOKEN in .env").queue();
            return;
        }

        List<String> args = ctx.getArgs();
        if (args == null || args.isEmpty()) {
            ctx.getChannel().sendMessage(getHelp()).queue();
            return;
        }

        // Parse flags from args
        ParsedArgs parsed = parseArgs(args);

        // Resolve image URL: explicit arg or Discord attachment
        String imageUrl = parsed.imageUrl;
        if (imageUrl == null && ctx.getMessageEvent() != null) {
            List<Message.Attachment> attachments = ctx.getMessageEvent().getMessage().getAttachments();
            for (Message.Attachment att : attachments) {
                if (att.isImage()) {
                    imageUrl = att.getUrl();
                    break;
                }
            }
        }

        if (imageUrl == null) {
            ctx.getChannel().sendMessage("Provide an image URL or attach an image to the message.").queue();
            return;
        }

        if (parsed.name == null || parsed.name.isEmpty()) {
            ctx.getChannel().sendMessage("Title is required. Use `--name \"Your Title\"`").queue();
            return;
        }

        String finalImageUrl = imageUrl;
        ctx.getChannel().sendMessage("Uploading to DeviantArt...").queue();

        // Run in a separate thread to avoid blocking JDA
        Thread uploader = new Thread(() -> {
            try {
                String deviationUrl = daClient.postDeviation(
                        finalImageUrl,
                        parsed.name,
                        parsed.tags,
                        parsed.ai,
                        parsed.mature,
                        parsed.description
                );
                ctx.getChannel().sendMessage("Published to DeviantArt: " + deviationUrl).queue();
            } catch (Exception e) {
                LOGGER.error("Failed to post to DeviantArt", e);
                ctx.getChannel().sendMessage("Failed to post: " + e.getMessage()).queue();
            }
        }, "DA-Upload");
        uploader.setDaemon(true);
        uploader.start();
    }

    @Override
    public CommandData getCommandData() {
        return null;
    }

    @Override
    public String getName() {
        return "dapost";
    }

    @Override
    public List<String> getAliases() {
        return List.of("da", "deviantart");
    }

    @Override
    public String getHelp() {
        return "Post an image to DeviantArt.\n" +
                "Usage: `_dapost [image_url] --name \"Title\" --tags \"tag1,tag2\" --ai --mature --desc \"Description\"`\n" +
                "  `--name` / `-n`  : Deviation title (required)\n" +
                "  `--tags` / `-t`  : Comma-separated tags\n" +
                "  `--ai`           : Mark as AI-generated\n" +
                "  `--mature` / `-m`: Mark as mature content\n" +
                "  `--desc` / `-d`  : Artist description/comment\n" +
                "You can also attach an image to the message instead of providing a URL.";
    }

    /**
     * Parses command arguments into structured fields.
     * Handles quoted strings spanning multiple args (e.g., --name "My Cool Art").
     */
    private ParsedArgs parseArgs(List<String> args) {
        ParsedArgs result = new ParsedArgs();
        int i = 0;

        while (i < args.size()) {
            String arg = args.get(i);

            switch (arg.toLowerCase()) {
                case "--name":
                case "-n":
                    result.name = readQuotedValue(args, i + 1);
                    i += countTokens(args, i + 1);
                    break;
                case "--tags":
                case "-t":
                    String tagStr = readQuotedValue(args, i + 1);
                    if (tagStr != null) {
                        result.tags.addAll(Arrays.asList(tagStr.split(",")));
                    }
                    i += countTokens(args, i + 1);
                    break;
                case "--desc":
                case "-d":
                    result.description = readQuotedValue(args, i + 1);
                    i += countTokens(args, i + 1);
                    break;
                case "--ai":
                    result.ai = true;
                    i++;
                    break;
                case "--mature":
                case "-m":
                    result.mature = true;
                    i++;
                    break;
                default:
                    // If it looks like a URL, treat as image URL
                    if (result.imageUrl == null && (arg.startsWith("http://") || arg.startsWith("https://"))) {
                        result.imageUrl = arg;
                    }
                    i++;
                    break;
            }
        }

        return result;
    }

    /**
     * Reads a value that may be quoted across multiple tokens.
     * E.g., args = ["\"My", "Cool", "Art\""] → "My Cool Art"
     */
    private String readQuotedValue(List<String> args, int start) {
        if (start >= args.size()) return null;

        String first = args.get(start);

        // Not quoted — single token value
        if (!first.startsWith("\"")) {
            return first;
        }

        // Quoted — collect until closing quote
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < args.size(); i++) {
            String token = args.get(i);
            if (sb.length() > 0) sb.append(' ');
            sb.append(token);

            if (token.endsWith("\"") && (i > start || token.length() > 1)) {
                break;
            }
        }

        // Strip surrounding quotes
        String val = sb.toString();
        if (val.startsWith("\"")) val = val.substring(1);
        if (val.endsWith("\"")) val = val.substring(0, val.length() - 1);
        return val;
    }

    /**
     * Counts how many tokens a quoted value consumes (for advancing the index).
     */
    private int countTokens(List<String> args, int start) {
        if (start >= args.size()) return 1;

        String first = args.get(start);
        if (!first.startsWith("\"")) return 2; // flag + single value

        for (int i = start; i < args.size(); i++) {
            String token = args.get(i);
            if (token.endsWith("\"") && (i > start || token.length() > 1)) {
                return 1 + (i - start + 1);
            }
        }
        return 1 + (args.size() - start); // unclosed quote, consume rest
    }

    private static class ParsedArgs {
        String imageUrl;
        String name;
        List<String> tags = new ArrayList<>();
        boolean ai;
        boolean mature;
        String description;
    }
}
