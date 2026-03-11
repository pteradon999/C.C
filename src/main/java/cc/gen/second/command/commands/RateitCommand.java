package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class RateitCommand implements ICommand {

    private static final Logger LOGGER = LoggerFactory.getLogger(RateitCommand.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int[] WEIGHTS = {3, 5, 7, 10, 12, 18, 15, 12, 8, 5, 5};
    private static final int TOTAL_WEIGHT;

    static {
        int sum = 0;
        for (int w : WEIGHTS) sum += w;
        TOTAL_WEIGHT = sum;
    }

    private final Map<String, List<String>> ratings;

    public RateitCommand() {
        try (InputStream in = getClass().getResourceAsStream("/rateit.json")) {
            if (in == null) {
                throw new IOException("rateit.json not found in resources");
            }
            ratings = MAPPER.readValue(in, new TypeReference<Map<String, List<String>>>() {});
            LOGGER.info("Loaded rateit.json with {} tiers", ratings.size());
        } catch (IOException e) {
            throw new RuntimeException("Failed to load rateit.json", e);
        }
    }

    @Override
    public void handle(CommandContext ctx) {
        User author = ctx.getAuthor();
        boolean selfRate;
        User target;

        if (ctx.isSlash()) {
            target = author;
            selfRate = true;
        } else {
            List<User> mentioned = ctx.getMessageEvent().getMessage().getMentions().getUsers();
            if (!mentioned.isEmpty()) {
                target = mentioned.get(0);
                selfRate = target.getIdLong() == author.getIdLong();
            } else {
                target = author;
                selfRate = true;
            }
        }

        long seed = Long.hashCode(target.getIdLong()) ^ LocalDate.now().hashCode();
        Random rng = new Random(seed);

        int tier = rollWeightedTier(rng);
        List<String> comments = ratings.get(String.valueOf(tier));
        if (comments == null || comments.isEmpty()) {
            String fallback = tier + "/10";
            sendReply(ctx, fallback);
            return;
        }

        String comment = comments.get(rng.nextInt(comments.size()));

        String message;
        if (selfRate) {
            message = comment;
        } else {
            String displayName = target.getEffectiveName();
            message = "Я бы оценила " + displayName + " как " + comment;
        }

        sendReply(ctx, message);
    }

    private int rollWeightedTier(Random rng) {
        int roll = rng.nextInt(TOTAL_WEIGHT);
        int cumulative = 0;
        for (int i = 0; i < WEIGHTS.length; i++) {
            cumulative += WEIGHTS[i];
            if (roll < cumulative) {
                return i;
            }
        }
        return WEIGHTS.length - 1;
    }

    private void sendReply(CommandContext ctx, String message) {
        if (ctx.isSlash()) {
            ctx.getSlashEvent().reply(message).queue();
        } else {
            ctx.getChannel().sendMessage(message).queue();
        }
    }

    @Override
    public CommandData getCommandData() {
        return null;
    }

    @Override
    public String getName() {
        return "_rateit";
    }

    @Override
    public String getDescription() {
        return "Rate a user from 0 to 10 with C.C.'s commentary";
    }

    @Override
    public String getHelp() {
        return "Usage: C.C_rateit or C.C_rateit @user";
    }
}
