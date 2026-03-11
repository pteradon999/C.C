package cc.gen.second.command.commands;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Shared rating logic and data for rateit/rateme commands.
 */
final class RatingData {

    private static final Logger LOGGER = LoggerFactory.getLogger(RatingData.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    static final int[] WEIGHTS = {3, 5, 7, 10, 12, 18, 15, 12, 8, 5, 5};
    static final int TOTAL_WEIGHT;

    static {
        int sum = 0;
        for (int w : WEIGHTS) sum += w;
        TOTAL_WEIGHT = sum;
    }

    private static volatile Map<String, List<String>> ratings;

    private RatingData() {}

    static Map<String, List<String>> getRatings() {
        if (ratings == null) {
            synchronized (RatingData.class) {
                if (ratings == null) {
                    try (InputStream in = RatingData.class.getResourceAsStream("/rateit.json")) {
                        if (in == null) {
                            throw new IOException("rateit.json not found in resources");
                        }
                        ratings = MAPPER.readValue(in, new TypeReference<Map<String, List<String>>>() {});
                        LOGGER.info("Loaded rateit.json with {} tiers", ratings.size());
                    } catch (IOException e) {
                        throw new RuntimeException("Failed to load rateit.json", e);
                    }
                }
            }
        }
        return ratings;
    }

    static int rollWeightedTier(Random rng) {
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
}
