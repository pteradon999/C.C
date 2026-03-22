package cc.gen.second;

import cc.gen.second.telegram.TelegramBot;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import javax.security.auth.login.LoginException;

public class Bot {

    private static final Logger LOGGER = LoggerFactory.getLogger(Bot.class);

    private Bot() throws LoginException, InterruptedException {
        // Start Discord bot
        String discordToken = config.get("token");
        if (discordToken != null && !discordToken.isBlank()) {
            JDABuilder.createDefault(discordToken)
                    .setActivity(Activity.watching("на твои страдания"))
                    .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                    .addEventListeners(new Listener())
                    .build()
                    .awaitReady();
            LOGGER.info("Discord bot started");
        } else {
            LOGGER.info("No Discord token configured, skipping Discord bot");
        }

        // Start Telegram bot
        String telegramToken = config.get("telegram_token");
        String telegramUsername = config.get("telegram_username");
        if (telegramToken != null && !telegramToken.isBlank()) {
            try {
                TelegramBotsApi api = new TelegramBotsApi(DefaultBotSession.class);
                api.registerBot(new TelegramBot(telegramToken, telegramUsername != null ? telegramUsername : "CCBot"));
                LOGGER.info("Telegram bot started as @{}", telegramUsername);
            } catch (Exception e) {
                LOGGER.error("Failed to start Telegram bot", e);
            }
        } else {
            LOGGER.info("No Telegram token configured, skipping Telegram bot");
        }
    }

    public static void main(String[] args) throws LoginException, InterruptedException {
        new Bot();
    }
}
