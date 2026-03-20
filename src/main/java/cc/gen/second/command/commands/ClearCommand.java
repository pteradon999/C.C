package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.interactions.commands.build.Commands;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class ClearCommand implements ICommand {

    @Override
    public void handle(CommandContext ctx) {
        // Only works in guild text channels
        if (!(ctx.getChannel() instanceof TextChannel)) {
            ctx.getChannel().sendMessage("❌ This command can only be used in server text channels.").queue();
            return;
        }

        TextChannel channel = (TextChannel) ctx.getChannel();

        // User permission check
        Member invoker = ctx.isSlash()
                ? ctx.getSlashEvent().getMember()
                : ctx.getMessageEvent().getMember();
        if (invoker == null || !invoker.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            reply(ctx, "❌ You need the **Manage Messages** permission to use this command.");
            return;
        }

        // Bot permission check
        Member self = channel.getGuild().getSelfMember();
        if (!self.hasPermission(channel, Permission.MESSAGE_MANAGE)) {
            ctx.getChannel().sendMessage("❌ I need the **Manage Messages** permission here.").queue();
            return;
        }

        // Amount parsing (prefix or slash)
        int amount = 1;
        if (ctx.isSlash()) {
            if (ctx.getSlashEvent().getOption("amount") != null) {
                amount = ctx.getSlashEvent().getOption("amount").getAsInt();
            }
        } else {
            List<String> args = ctx.getArgs();
            if (args != null && !args.isEmpty()) {
                try {
                    amount = Integer.parseInt(args.get(0));
                } catch (NumberFormatException ignored) { /* keep default 1 */ }
            }
        }

        // Clamp between 1 and 100
        if (amount < 1) amount = 1;
        if (amount > 100) amount = 100;

        // Retrieve and delete
        final int toDelete = amount;
        channel.getHistory().retrievePast(toDelete).queue(messages -> {
            if (messages.isEmpty()) {
                reply(ctx, "Nothing to delete.");
                return;
            }

            for (Message m : messages) {
                m.delete().queue(null, err -> {}); // ignore failures (e.g., permissions on some messages)
            }

            // Optionally delete the invoking message too (prefix case)
            if (!ctx.isSlash() && ctx.getMessageEvent() != null) {
                try { ctx.getMessageEvent().getMessage().delete().queue(); } catch (Exception ignored) {}
            }

            // Short-lived confirmation
            channel.sendMessage("🧹 Deleted " + messages.size() + " message(s).")
                    .queue(msg -> msg.delete().queueAfter(5, TimeUnit.SECONDS));
        }, error -> reply(ctx, "Failed to fetch message history: " + error.getMessage()));
    }

    private void reply(CommandContext ctx, String text) {
        if (ctx.isSlash()) {
            ctx.getSlashEvent().reply(text).setEphemeral(true).queue();
        } else {
            ctx.getChannel().sendMessage(text).queue();
        }
    }

    @Override
    public String getName() {
        // Keep your usual style; add an alias without underscore for safety
        return "_purge";
    }

    @Override
    public List<String> getAliases() {
        return java.util.Arrays.asList("purge");
    }

    @Override
    public String getHelp() {
        return "Deletes recent messages in this channel. Usage: `_purge 10` (1–100).";
    }

    @Override
    public CommandData getCommandData() {
        // Optional: slash variant /purge amount:<1..100>
        return Commands.slash("purge", "Delete recent messages in this channel")
                .addOptions(new OptionData(OptionType.INTEGER, "amount", "How many messages to delete (1–100)", false)
                        .setRequiredRange(1, 100));
    }
}
