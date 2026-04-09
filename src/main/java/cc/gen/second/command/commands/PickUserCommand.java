package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PickUserCommand implements ICommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(PickUserCommand.class);
    private static final int MAX_SCAN = 5000;

    @Override
    public void handle(CommandContext ctx) {
        MessageChannelUnion channel = ctx.getChannel();
        OffsetDateTime cutoff = OffsetDateTime.now().minusHours(24);

        channel.getIterableHistory()
                .takeAsync(MAX_SCAN)
                .thenAccept(messages -> {
                    LinkedHashMap<String, String> activeUsers = new LinkedHashMap<>();
                    for (Message msg : messages) {
                        if (msg.getTimeCreated().isBefore(cutoff)) continue;
                        User author = msg.getAuthor();
                        if (author.isBot()) continue;
                        activeUsers.putIfAbsent(author.getId(), author.getEffectiveName());
                    }

                    if (activeUsers.isEmpty()) {
                        channel.sendMessage("За последние 24 часа активных пользователей не найдено.").queue();
                        return;
                    }

                    List<String> nicks = new ArrayList<>(activeUsers.values());
                    String picked = nicks.get(ThreadLocalRandom.current().nextInt(nicks.size()));
                    channel.sendMessage("🎲 Случайный активный юзер: **" + picked + "**").queue();
                })
                .exceptionally(err -> {
                    LOGGER.error("Failed to scan channel history for active users", err);
                    channel.sendMessage("Не удалось получить историю канала: " + err.getMessage()).queue();
                    return null;
                });
    }

    @Override
    public CommandData getCommandData() {
        return null;
    }

    @Override
    public String getName() {
        return "_pickuser";
    }

    @Override
    public List<String> getAliases() {
        return List.of("pickuser", "_randuser");
    }

    @Override
    public String getDescription() {
        return "Pick a random user active in this channel in the last 24h";
    }

    @Override
    public String getHelp() {
        return "Usage: C.C_pickuser — выбирает случайного юзера, писавшего в этот канал за 24ч";
    }
}
