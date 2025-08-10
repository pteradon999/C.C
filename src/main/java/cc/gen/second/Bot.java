package cc.gen.second;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.requests.GatewayIntent;

import javax.security.auth.login.LoginException;

public class Bot {
    private Bot() throws LoginException, InterruptedException {
        JDABuilder.createDefault(cc.gen.second.config.get("token"))
                .setActivity(Activity.watching("на твои страдания"))
                .enableIntents(GatewayIntent.GUILD_MESSAGES, GatewayIntent.MESSAGE_CONTENT)
                .addEventListeners(new Listener())
                .build()
                .awaitReady(); // Wait for bot to be ready
    }

    public static void main(String[] args) throws LoginException, InterruptedException {
        new Bot();
    }
}
