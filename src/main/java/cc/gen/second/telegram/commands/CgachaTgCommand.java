package cc.gen.second.telegram.commands;

import cc.gen.second.telegram.ITelegramCommand;
import cc.gen.second.telegram.TelegramBot;
import org.telegram.telegrambots.meta.api.objects.Message;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CgachaTgCommand implements ITelegramCommand {

    private static final DecimalFormat ONE_DEC = new DecimalFormat("0.0");

    private static final String[] TIER_NAMES = {
            "Trash (0.x)", "Common (1.x)", "Uncommon (2.x)", "Rare (3.x)", "Elite (4.x)",
            "Epic (5.x)", "Legendary (6.x)", "Mythical (7.x)", "Divine (8.x)", "Transcendent (9.x)"
    };

    private static final double[] TIER_WEIGHTS = {
            5.0, 12.6, 14.0, 17.0, 19.0, 17.0, 11.0, 3.0, 1.0, 0.4
    };

    private static final Map<Category, List<GachaEntry>> CACHE = new EnumMap<>(Category.class);

    static {
        for (Category c : new Category[]{Category.ABILITY, Category.FAMILIAR, Category.ITEM, Category.SKILL, Category.TRAIT}) {
            try {
                CACHE.put(c, loadEntriesFor(c));
            } catch (IOException e) {
                CACHE.put(c, Collections.emptyList());
            }
        }
    }

    private enum Category {
        ABILITY("ability"), FAMILIAR("familiar"), ITEM("item"), SKILL("skill"), TRAIT("trait"), ALL("all");
        final String fileBase;
        Category(String f) { this.fileBase = f; }

        static Optional<Category> fromUser(String s) {
            if (s == null) return Optional.empty();
            switch (s.trim().toLowerCase(Locale.ROOT)) {
                case "ability": return Optional.of(ABILITY);
                case "familiar": return Optional.of(FAMILIAR);
                case "item": return Optional.of(ITEM);
                case "skill": return Optional.of(SKILL);
                case "trait": return Optional.of(TRAIT);
                case "all": return Optional.of(ALL);
                default: return Optional.empty();
            }
        }
        static Category randomSpecific() {
            Category[] v = {ABILITY, FAMILIAR, ITEM, SKILL, TRAIT};
            return v[ThreadLocalRandom.current().nextInt(v.length)];
        }
    }

    private static class GachaEntry {
        final String name; final double threshold; final String description;
        GachaEntry(String n, double t, String d) { name=n; threshold=t; description=d; }
        int tierInt() { return (int)Math.floor(threshold); }
    }

    private static class RarityRange {
        final double min, max;
        RarityRange(double min, double max) {
            this.min = Math.max(0.0, Math.min(min, max));
            this.max = Math.min(9.9, Math.max(min, max));
        }
        boolean contains(double v) { return v >= min && v <= max; }
        public String toString() { return ONE_DEC.format(min)+"-"+ONE_DEC.format(max); }
    }

    @Override
    public void handle(TelegramBot bot, Message message, List<String> args) {
        long chatId = message.getChatId();

        // Parse: /cgacha [Category] [Range] [Count]
        // or /cgacha category range count
        // or /cgacha [Category][Range][Count]
        String fullText = message.getText();
        ParseResult input = parseBracketed(fullText);

        if (input == null) {
            // Try parsing from args: /cgacha ability 5-7 3
            input = parseFromArgs(args);
        }

        if (input == null) {
            bot.sendText(chatId,
                    "Usage: `/cgacha [Category][Count]`\n"
                            + "With filter: `/cgacha [Category][min-max][Count]`\n"
                            + "Or: `/cgacha ability 5-7 3`\n"
                            + "Categories: All, Ability, Familiar, Item, Skill, Trait");
            return;
        }

        final RarityRange filterRange = input.range;
        Map<Category, List<GachaEntry>> data = new EnumMap<>(Category.class);
        for (Category c : new Category[]{Category.ABILITY, Category.FAMILIAR, Category.ITEM, Category.SKILL, Category.TRAIT}) {
            List<GachaEntry> entries = new ArrayList<>(CACHE.getOrDefault(c, Collections.emptyList()));
            if (filterRange != null) {
                entries.removeIf(e -> !filterRange.contains(e.threshold));
            }
            data.put(c, entries);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("*C.C Tier Gacha*\n")
                .append("Category: `").append(input.category.name().toLowerCase()).append("` • Rolls: `")
                .append(input.count).append("`");
        if (input.range != null) sb.append(" • Range: `").append(input.range).append("`");
        sb.append("\n────────────────────────\n");

        for (int i = 1; i <= input.count; i++) {
            Category actual = (input.category == Category.ALL) ? Category.randomSpecific() : input.category;
            List<GachaEntry> pool = data.getOrDefault(actual, Collections.emptyList());

            if (pool.isEmpty()) {
                sb.append("*Roll ").append(i).append("* — `").append(actual.name().toLowerCase())
                        .append("` — ⚠️ No entries in range\n");
                continue;
            }

            if (input.range != null) {
                GachaEntry pick = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
                sb.append("*🎲 Roll ").append(i).append(":* `").append(actual.name().toLowerCase()).append("` — ")
                        .append("Range: *").append(input.range).append("*\n")
                        .append("→ *").append(pick.name).append("* (").append(ONE_DEC.format(pick.threshold)).append(")\n");
                if (pick.description != null && !pick.description.isBlank()) {
                    String desc = pick.description.startsWith("#") ? pick.description.substring(1).trim() : pick.description.trim();
                    sb.append("> ").append(desc).append("\n");
                }
            } else {
                int baseTier = rollWeightedTier(TIER_WEIGHTS);
                int d20 = 1 + ThreadLocalRandom.current().nextInt(20);
                int finalTier = baseTier;
                String swingNote = "";
                boolean specialPrinted = false;

                if (d20 == 20) {
                    if (baseTier < 9) { finalTier = baseTier + 1; swingNote = "nat20 ⇒ tier up"; }
                    else {
                        sb.append("*🎲 Roll ").append(i).append(":* `").append(actual.name().toLowerCase()).append("` ")
                                .append("— *You Win. You have every other perk in the gacha.* ")
                                .append("`base=").append(TIER_NAMES[baseTier]).append(" d20=").append(d20).append("`\n\n");
                        specialPrinted = true;
                    }
                } else if (d20 == 1) {
                    if (baseTier > 0) { finalTier = baseTier - 1; swingNote = "nat1 ⇒ tier down"; }
                    else {
                        sb.append("*🎲 Roll ").append(i).append(":* `").append(actual.name().toLowerCase()).append("` ")
                                .append("— *You are dead. On the spot, inevitably, irreversibly.* ")
                                .append("`base=").append(TIER_NAMES[baseTier]).append(" d20=").append(d20).append("`\n\n");
                        specialPrinted = true;
                    }
                } else {
                    swingNote = "d20=" + d20 + " ⇒ no change";
                }

                if (specialPrinted) continue;

                GachaEntry pick = pickRandomInTierOrNearest(pool, finalTier);
                sb.append("*🎲 Roll ").append(i).append(":* `").append(actual.name().toLowerCase()).append("` — ")
                        .append("Tier: *").append(TIER_NAMES[finalTier]).append("*  ")
                        .append("`base=").append(TIER_NAMES[baseTier]).append(", ").append(swingNote).append("`\n")
                        .append("→ *").append(pick.name).append("* (").append(ONE_DEC.format(pick.threshold)).append(")\n");
                if (pick.description != null && !pick.description.isBlank()) {
                    String desc = pick.description.startsWith("#") ? pick.description.substring(1).trim() : pick.description.trim();
                    sb.append("> ").append(desc).append("\n");
                }
            }
            if (i != input.count) sb.append("\n");
        }

        bot.sendText(chatId, sb.toString());
    }

    @Override public String getName() { return "cgacha"; }
    @Override public String getDescription() { return "C.C Tier Gacha with d20"; }

    // --- Parsing ---

    private static class ParseResult {
        final Category category; final int count; final RarityRange range;
        ParseResult(Category c, int n, RarityRange r) { category=c; count=n; range=r; }
    }

    private ParseResult parseBracketed(String raw) {
        if (raw == null) return null;
        Matcher m = Pattern.compile("\\[(.+?)\\]").matcher(raw);
        List<String> parts = new ArrayList<>();
        while (m.find()) parts.add(m.group(1).trim());
        if (parts.isEmpty()) return null;

        Category cat = Category.fromUser(parts.get(0)).orElse(Category.ALL);
        RarityRange range = null;
        int count = 1;

        if (parts.size() == 2) {
            RarityRange r = parseRange(parts.get(1));
            if (r != null) { range = r; } else { count = intSafe(parts.get(1), 1); }
        } else if (parts.size() >= 3) {
            RarityRange r = parseRange(parts.get(1));
            if (r != null) { range = r; count = intSafe(parts.get(2), 1); }
            else { count = intSafe(parts.get(2), 1); }
        }

        count = Math.max(1, Math.min(10, count));
        return new ParseResult(cat, count, range);
    }

    private ParseResult parseFromArgs(List<String> args) {
        if (args.isEmpty()) return null;
        Category cat = Category.fromUser(args.get(0)).orElse(Category.ALL);
        RarityRange range = null;
        int count = 1;

        if (args.size() >= 2) {
            RarityRange r = parseRange(args.get(1));
            if (r != null) { range = r; } else { count = intSafe(args.get(1), 1); }
        }
        if (args.size() >= 3) {
            count = intSafe(args.get(2), 1);
        }

        count = Math.max(1, Math.min(10, count));
        return new ParseResult(cat, count, range);
    }

    private static RarityRange parseRange(String s) {
        Matcher m = Pattern.compile("^([0-9]+(?:\\.[0-9]+)?)-([0-9]+(?:\\.[0-9]+)?)$").matcher(s.trim());
        if (!m.matches()) return null;
        try { return new RarityRange(Double.parseDouble(m.group(1)), Double.parseDouble(m.group(2))); }
        catch (NumberFormatException e) { return null; }
    }

    private static int intSafe(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception e) { return d; }
    }

    private static int rollWeightedTier(double[] w) {
        double t = 0; for (double x : w) t += x;
        double r = ThreadLocalRandom.current().nextDouble(t), c = 0;
        for (int i = 0; i < w.length; i++) { c += w[i]; if (r < c) return i; }
        return w.length - 1;
    }

    private static GachaEntry pickRandomInTierOrNearest(List<GachaEntry> pool, int tier) {
        Map<Integer, List<GachaEntry>> byTier = new HashMap<>();
        for (GachaEntry e : pool) byTier.computeIfAbsent(e.tierInt(), k -> new ArrayList<>()).add(e);
        if (byTier.containsKey(tier)) {
            List<GachaEntry> l = byTier.get(tier);
            return l.get(ThreadLocalRandom.current().nextInt(l.size()));
        }
        for (int d = 1; d <= 9; d++) {
            if (byTier.containsKey(tier-d)) { List<GachaEntry> l = byTier.get(tier-d); return l.get(ThreadLocalRandom.current().nextInt(l.size())); }
            if (byTier.containsKey(tier+d)) { List<GachaEntry> l = byTier.get(tier+d); return l.get(ThreadLocalRandom.current().nextInt(l.size())); }
        }
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private static List<GachaEntry> loadEntriesFor(Category c) throws IOException {
        String path = "gacha/" + c.fileBase + ".txt";
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = CgachaTgCommand.class.getClassLoader();
        URL url = cl.getResource(path);
        if (url != null) {
            try (InputStream in = url.openStream()) { return parseEntries(in); }
        }
        File f = new File("./" + c.fileBase + ".txt");
        if (f.exists()) {
            try (FileInputStream fis = new FileInputStream(f)) { return parseEntries(fis); }
        }
        return Collections.emptyList();
    }

    private static List<GachaEntry> parseEntries(InputStream in) throws IOException {
        List<GachaEntry> out = new ArrayList<>();
        Pattern header = Pattern.compile("^\\s*\\d+\\.\\s*(.+?)\\s*,\\s*([0-9]+(?:\\.[0-9])?)\\s*$");
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                Matcher m = header.matcher(line);
                if (m.matches()) {
                    String name = m.group(1).trim();
                    double thr = Double.parseDouble(m.group(2));
                    String descLine = br.readLine();
                    String desc = (descLine == null) ? "" : descLine.trim();
                    out.add(new GachaEntry(name, thr, desc));
                }
            }
        }
        out.sort(Comparator.comparingDouble(e -> e.threshold));
        return out;
    }
}
