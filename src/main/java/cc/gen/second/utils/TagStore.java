package cc.gen.second.utils;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * JSON-backed tag store, replacing the MySQL tag system.
 * Tags are stored in tags.json as: { "tagname": { "author": "...", "text": "..." } }
 */
public class TagStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(TagStore.class);
    private static final Path FILE = Path.of("tags.json");
    private static JSONObject root;

    static {
        try {
            if (Files.notExists(FILE)) {
                root = new JSONObject();
                save();
                LOGGER.info("Created new tags.json");
            } else {
                String content = new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8);
                root = new JSONObject(content);
                LOGGER.info("Loaded {} tags from tags.json", root.length());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize tag store", e);
        }
    }

    private static void save() throws IOException {
        Files.write(
                FILE,
                root.toString(2).getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    /** Returns the tag text, or null if not found. */
    public static synchronized String read(String tagName) {
        if (!root.has(tagName)) return null;
        return root.getJSONObject(tagName).optString("text", null);
    }

    /** Creates or overwrites a tag, persists immediately. */
    public static synchronized void create(String author, String tagName, String tagText) {
        JSONObject tag = new JSONObject();
        tag.put("author", author);
        tag.put("text", tagText);
        root.put(tagName, tag);
        try {
            save();
            LOGGER.info("Saved tag '{}' by {}", tagName, author);
        } catch (IOException e) {
            LOGGER.error("Failed to persist tag '{}'", tagName, e);
        }
    }
}
