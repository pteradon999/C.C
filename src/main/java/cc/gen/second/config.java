package cc.gen.second;

import io.github.cdimascio.dotenv.Dotenv;

public class config {
    private static final Dotenv dotenv = Dotenv.configure()
            .ignoreIfMalformed()
            .ignoreIfMissing()
            .load();

    public static String get(String key) {
        return dotenv.get(key.toUpperCase());
    }
}
