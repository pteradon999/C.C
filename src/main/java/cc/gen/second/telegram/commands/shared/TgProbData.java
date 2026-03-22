package cc.gen.second.telegram.commands.shared;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

public final class TgProbData {

    private static final Logger LOGGER = LoggerFactory.getLogger(TgProbData.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static volatile Map<String, List<String>> responses;

    private TgProbData() {}

    public static Map<String, List<String>> getResponses() {
        if (responses == null) {
            synchronized (TgProbData.class) {
                if (responses == null) {
                    try (InputStream in = TgProbData.class.getResourceAsStream("/prob.json")) {
                        if (in == null) throw new IOException("prob.json not found");
                        responses = MAPPER.readValue(in, new TypeReference<Map<String, List<String>>>() {});
                        LOGGER.info("Loaded prob.json for Telegram");
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to load prob.json", e);
                    }
                }
            }
        }
        return responses;
    }
}
