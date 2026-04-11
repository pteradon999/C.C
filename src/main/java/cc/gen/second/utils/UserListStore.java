package cc.gen.second.utils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * JSON-backed store for the pickuser name list.
 * Stored in users.json as: { "users": ["Гарри", "Алемаз", ...] }
 */
public class UserListStore {
    private static final Logger LOGGER = LoggerFactory.getLogger(UserListStore.class);
    private static final Path FILE = Path.of("users.json");
    private static JSONObject root;

    private static final List<String> DEFAULT_USERS = Arrays.asList(
            "Гарри", "Алемаз", "Лаэрт", "Роксик", "Варно", "Локон", "Вий", "Корги",
            "Птера", "Данте", "Айзек", "КИИ", "Жид", "Дорн", "Рей", "Сидиус",
            "Отинус", "Рак", "Вильм", "Барбус", "Ефим", "Оскар", "Ма", "Баламут",
            "Терран", "Тигра", "Астероид", "Франк", "Флюкт", "Волк", "Рейвен",
            "Лейзон", "Гера", "Сфено", "Декстер", "Иваныч", "Неофит", "Чехов",
            "Димтри", "Тедд", "Мадара", "КХМ", "Сирин", "Енот"
    );

    static {
        try {
            if (Files.notExists(FILE)) {
                root = new JSONObject();
                root.put("users", new JSONArray(DEFAULT_USERS));
                save();
                LOGGER.info("Created new users.json with {} default users", DEFAULT_USERS.size());
            } else {
                String content = new String(Files.readAllBytes(FILE), StandardCharsets.UTF_8);
                root = new JSONObject(content);
                if (!root.has("users")) {
                    root.put("users", new JSONArray(DEFAULT_USERS));
                    save();
                }
                LOGGER.info("Loaded {} users from users.json", root.getJSONArray("users").length());
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize user list store", e);
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

    /** Returns a snapshot of the current user list. */
    public static synchronized List<String> getAll() {
        JSONArray arr = root.getJSONArray("users");
        List<String> out = new ArrayList<>(arr.length());
        for (int i = 0; i < arr.length(); i++) {
            out.add(arr.getString(i));
        }
        return out;
    }

    /**
     * Adds a name to the list (case-insensitive dedupe). Returns true if added,
     * false if it was already present.
     */
    public static synchronized boolean add(String name) {
        JSONArray arr = root.getJSONArray("users");
        for (int i = 0; i < arr.length(); i++) {
            if (arr.getString(i).equalsIgnoreCase(name)) {
                return false;
            }
        }
        arr.put(name);
        try {
            save();
            LOGGER.info("Added user '{}' to users.json", name);
        } catch (IOException e) {
            LOGGER.error("Failed to persist new user '{}'", name, e);
        }
        return true;
    }

    /**
     * Removes a name from the list (case-insensitive). Returns true if removed,
     * false if not found.
     */
    public static synchronized boolean remove(String name) {
        JSONArray arr = root.getJSONArray("users");
        for (int i = 0; i < arr.length(); i++) {
            if (arr.getString(i).equalsIgnoreCase(name)) {
                arr.remove(i);
                try {
                    save();
                    LOGGER.info("Removed user '{}' from users.json", name);
                } catch (IOException e) {
                    LOGGER.error("Failed to persist user removal '{}'", name, e);
                }
                return true;
            }
        }
        return false;
    }
}
