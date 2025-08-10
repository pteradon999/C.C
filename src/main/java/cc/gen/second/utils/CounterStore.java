package cc.gen.second.utils;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

/**
 * Simple JSON-backed counter store for “pats” and “slaps”
 */
public class CounterStore {
    private static final Path FILE = Path.of("counters.json");
    private static JSONObject root;

    static {
        try {
            if (Files.notExists(FILE)) {
                // File doesn’t exist — create initial JSON
                root = new JSONObject()
                        .put("pats", 0)
                        .put("slaps", 0);
                save();
            } else {
                // Read existing file (Java 8 compatible)
                String content = new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8);
                root = new JSONObject(content);

                // Ensure keys exist
                if (!root.has("pats")) root.put("pats", 0);
                if (!root.has("slaps")) root.put("slaps", 0);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize counter store", e);
        }
    }

    /** Persist the current JSON to disk */
    private static void save() throws IOException {
        String jsonString = root.toString(2); // pretty-print with 2-space indent
        Files.write(
                FILE,
                jsonString.getBytes(StandardCharsets.UTF_8),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING
        );
    }

    /**
     * Increment the given counter key by 1, persist, and return new value.
     * @param key "pats" or "slaps"
     * @return new counter value
     */
    public static synchronized int increment(String key) {
        int val = root.optInt(key, 0) + 1;
        root.put(key, val);
        try {
            save();
        } catch (IOException e) {
            // Log and ignore
            e.printStackTrace();
        }
        return val;
    }
}
