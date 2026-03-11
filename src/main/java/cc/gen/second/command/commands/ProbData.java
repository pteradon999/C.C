package cc.gen.second.command.commands;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;

/**
 * Loads and caches probability response data from prob.json.
 */
final class ProbData {

    private static final Logger LOGGER = LoggerFactory.getLogger(ProbData.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static volatile Map<String, List<String>> responses;

    private ProbData() {}

    static Map<String, List<String>> getResponses() {
        if (responses == null) {
            synchronized (ProbData.class) {
                if (responses == null) {
                    try (InputStream in = ProbData.class.getResourceAsStream("/prob.json")) {
                        if (in == null) {
                            throw new IOException("prob.json not found in resources");
                        }
                        responses = MAPPER.readValue(in, new TypeReference<Map<String, List<String>>>() {});
                        LOGGER.info("Loaded prob.json with {} percent keys", responses.size());
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to load prob.json", e);
                    }
                }
            }
        }
        return responses;
    }
}
