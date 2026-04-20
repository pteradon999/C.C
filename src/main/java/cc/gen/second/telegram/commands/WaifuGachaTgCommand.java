package cc.gen.second.telegram.commands;

import cc.gen.second.telegram.ITelegramCommand;
import cc.gen.second.telegram.TelegramBot;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.io.*;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WaifuGachaTgCommand implements ITelegramCommand {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] TIER_NAMES = {
            "Trash (1)", "Common (2)", "Uncommon (3)", "Rare (4)", "Elite (5)",
            "Epic (6)", "Legendary (7)", "Mythical (8)", "Divine (9)", "Transcendent (10)"
    };
    private static final String[] TIER_EMOJIS = {
            "💩", "🟫", "⚪", "🟢", "🔵", "🟣", "🟠", "🔴", "🟡", "⭐"
    };
    private static final double[] TIER_WEIGHTS = {
            10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0, 10.0
    };

    private static final List<Waifu> WAIFU_CACHE;

    static {
        List<Waifu> loaded = Collections.emptyList();
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = WaifuGachaTgCommand.class.getClassLoader();
            URL u = cl.getResource("characters.json");
            if (u != null) {
                try (InputStream in = u.openStream()) { loaded = parseWaifus(in); }
            } else {
                File f = new File("./characters.json");
                if (f.exists()) { try (FileInputStream fis = new FileInputStream(f)) { loaded = parseWaifus(fis); } }
            }
        } catch (IOException ignored) {}
        WAIFU_CACHE = Collections.unmodifiableList(loaded);
    }

    private static List<Waifu> parseWaifus(InputStream in) throws IOException {
        List<Waifu> out = new ArrayList<>();
        JsonNode root = MAPPER.readTree(in);
        if (root.isArray()) for (JsonNode n : root) out.add(new Waifu(n));
        return out;
    }

    private static class Waifu {
        final String name; final int tier; final String world; final List<String> badges;
        Waifu(JsonNode n) {
            name = n.has("n") ? n.get("n").asText() : "Unknown";
            tier = n.has("t") ? n.get("t").asInt() : 1;
            world = n.has("w") ? n.get("w").asText() : "Unknown";
            badges = new ArrayList<>();
            if (n.has("b") && n.get("b").isArray()) for (JsonNode b : n.get("b")) badges.add(b.asText().toUpperCase(Locale.ROOT));
        }
        String getTierDisplay() {
            int idx = Math.max(0, Math.min(tier-1, TIER_NAMES.length-1));
            return TIER_EMOJIS[idx]+" *"+TIER_NAMES[idx]+"*";
        }
    }

    @Override
    public void handle(TelegramBot bot, Message message, List<String> args) {
        long chatId = message.getChatId();

        // Parse from brackets or from args: /waifugacha [1-10][5][F] or /waifugacha 1-10 5 F
        ParseResult input = parseBracketed(message.getText());
        if (input == null) input = parseFromArgs(args);

        if (input == null) {
            bot.sendText(chatId,
                    "Usage: `/waifugacha [tier-range][count][F/M/All]`\n"
                            + "Example: `/waifugacha [3-7][5][F]`\n"
                            + "Or: `/waifugacha 3-7 5 F`");
            return;
        }

        if (WAIFU_CACHE.isEmpty()) {
            bot.sendText(chatId, "❌ Failed to load `characters.json`");
            return;
        }

        List<Waifu> pool = new ArrayList<>();
        for (Waifu w : WAIFU_CACHE) if (w.tier >= input.minTier && w.tier <= input.maxTier) pool.add(w);

        final String badgeFilter = input.badge;
        if (!badgeFilter.equals("ALL")) pool.removeIf(w -> !w.badges.contains(badgeFilter));

        if (pool.isEmpty()) {
            bot.sendText(chatId, "❌ No waifus in tiers " + input.minTier + "-" + input.maxTier + " with filter " + input.badge);
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append("🎀 *Waifu Gacha* 🎀\n")
                .append("Range: `").append(input.minTier).append("-").append(input.maxTier).append("` • ")
                .append("Count: `").append(input.count).append("` • ")
                .append("Badge: `").append(input.badge).append("`\n")
                .append("══════════════════\n");

        for (int i = 1; i <= input.count; i++) {
            int tier = rollTier(input.minTier, input.maxTier);
            List<Waifu> tierList = new ArrayList<>();
            for (Waifu w : pool) if (w.tier == tier) tierList.add(w);
            if (tierList.isEmpty()) tierList = List.of(pool.get(ThreadLocalRandom.current().nextInt(pool.size())));

            Waifu pick = tierList.get(ThreadLocalRandom.current().nextInt(tierList.size()));
            sb.append("🎲 Roll ").append(i).append(": ").append(pick.getTierDisplay()).append("\n")
                    .append("💖 *").append(pick.name).append("* (").append(pick.world).append(")\n");
            if (!pick.badges.isEmpty()) {
                sb.append("🏷️ ");
                for (int j = 0; j < pick.badges.size(); j++) {
                    if (j > 0) sb.append(" • ");
                    sb.append("`").append(pick.badges.get(j)).append("`");
                }
                sb.append("\n");
            }
            if (pick.tier >= 9) sb.append("✨ Legendary Pull! ✨\n");
            else if (pick.tier >= 7) sb.append("🌟 Rare Find! 🌟\n");
            if (i < input.count) sb.append("\n");
        }

        bot.sendText(chatId, sb.toString());
    }

    @Override public String getName() { return "waifugacha"; }
    @Override public String getDescription() { return "Waifu gacha with tier and gender filters"; }

    // --- Parsing ---

    private static class ParseResult {
        final int minTier, maxTier, count; final String badge;
        ParseResult(int min, int max, int c, String b) { minTier=min; maxTier=max; count=c; badge=b; }
    }

    private ParseResult parseBracketed(String raw) {
        if (raw == null) return null;
        Pattern p = Pattern.compile("\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]");
        Matcher m = p.matcher(raw);
        if (!m.find()) return null;
        int[] range = parseTierRange(m.group(1).trim());
        if (range == null) return null;
        int count;
        try { count = Integer.parseInt(m.group(2).trim()); } catch (Exception e) { return null; }
        count = Math.max(1, Math.min(10, count));
        String badge = m.group(3).trim().toUpperCase(Locale.ROOT);
        if (!badge.equals("F") && !badge.equals("M")) badge = "ALL";
        return new ParseResult(range[0], range[1], count, badge);
    }

    private ParseResult parseFromArgs(List<String> args) {
        if (args.size() < 3) return null;
        int[] range = parseTierRange(args.get(0));
        if (range == null) return null;
        int count;
        try { count = Integer.parseInt(args.get(1)); } catch (Exception e) { return null; }
        count = Math.max(1, Math.min(10, count));
        String badge = args.get(2).trim().toUpperCase(Locale.ROOT);
        if (!badge.equals("F") && !badge.equals("M")) badge = "ALL";
        return new ParseResult(range[0], range[1], count, badge);
    }

    private static int[] parseTierRange(String s) {
        Matcher m = Pattern.compile("(\\d+)-(\\d+)").matcher(s);
        if (!m.find()) return null;
        try {
            int a = Integer.parseInt(m.group(1)), b = Integer.parseInt(m.group(2));
            return new int[]{Math.max(1, Math.min(a,b)), Math.min(10, Math.max(a,b))};
        } catch (Exception e) { return null; }
    }

    private static int rollTier(int min, int max) {
        double[] w = new double[10]; double total = 0;
        for (int i = 1; i <= 10; i++) {
            if (i >= min && i <= max) { w[i-1] = TIER_WEIGHTS[i-1]; total += w[i-1]; }
        }
        if (total == 0) return min + ThreadLocalRandom.current().nextInt(max - min + 1);
        double r = ThreadLocalRandom.current().nextDouble(total), cum = 0;
        for (int i = 1; i <= 10; i++) {
            if (i >= min && i <= max) { cum += w[i-1]; if (r < cum) return i; }
        }
        return max;
    }
}
