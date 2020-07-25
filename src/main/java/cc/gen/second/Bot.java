package cc.gen.second;

import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.Activity;

import javax.security.auth.login.LoginException;

public class Bot {
    private Bot() throws LoginException {
        new JDABuilder()
                .setToken(config.get("token"))
                .addEventListeners(new Listener())
                .setActivity(Activity.watching(" на твои страдания"))
                .build();
    }
    public static void main(String[] args) throws LoginException {
        new Bot();
    }

}
