package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.io.*;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CgachaPrefixCommand implements ICommand {

    private static final DecimalFormat ONE_DEC = new DecimalFormat("0.0");
    private static final int DISCORD_LIMIT = 2000;

    // Rarity tiers 0..9
    private static final String[] TIER_NAMES = {
            "Trash (0.x)", "Common (1.x)", "Uncommon (2.x)", "Rare (3.x)", "Elite (4.x)",
            "Epic (5.x)", "Legendary (6.x)", "Mythical (7.x)", "Divine (8.x)", "Transcendent (9.x)"
    };

    // Drop weights in the order 0..9 (sum = 100)
    private static final double[] TIER_WEIGHTS = {
            5.0, 12.6, 14.0, 17.0, 19.0, 17.0, 11.0, 3.0, 1.0, 0.4
    };

    // Gacha data loaded once at startup, keyed by category
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
        ABILITY("ability"),
        FAMILIAR("familiar"),
        ITEM("item"),
        SKILL("skill"),
        TRAIT("trait"),
        ALL("all");

        final String fileBase;
        Category(String fileBase) { this.fileBase = fileBase; }

        static Optional<Category> fromUser(String s) {
            if (s == null) return Optional.empty();
            String k = s.trim().toLowerCase(Locale.ROOT);
            switch (k) {
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
            Category[] vals = {ABILITY, FAMILIAR, ITEM, SKILL, TRAIT};
            return vals[ThreadLocalRandom.current().nextInt(vals.length)];
        }
    }

    private static class GachaEntry {
        final String name;
        final double threshold;   // e.g., 3.3 -> tier 3
        final String description;
        GachaEntry(String name, double threshold, String description) {
            this.name = name;
            this.threshold = threshold;
            this.description = description;
        }
        int tierInt() { return (int)Math.floor(threshold); }
    }

    private static class RarityRange {
        final double min;
        final double max;

        RarityRange(double min, double max) {
            this.min = Math.max(0.0, Math.min(min, max)); // ensure min <= max and >= 0
            this.max = Math.min(9.9, Math.max(min, max)); // ensure max >= min and <= 9.9
        }

        boolean contains(double value) {
            return value >= min && value <= max;
        }

        @Override
        public String toString() {
            return ONE_DEC.format(min) + "-" + ONE_DEC.format(max);
        }
    }

    private static class ParsedInput {
        final Category category;
        final int count;  // 1..10
        final RarityRange rarityRange; // null means no filter

        ParsedInput(Category category, int count, RarityRange rarityRange) {
            this.category = category;
            this.count = count;
            this.rarityRange = rarityRange;
        }
    }

    @Override
    public void handle(CommandContext ctx) {
        Message msg = ctx.getMessageEvent().getMessage();
        String raw = (msg != null) ? msg.getContentRaw() : null;
        MessageChannel channel = ctx.getChannel();

        // Accept: "_cgacha [Cat][Count]", "_cgacha [Cat][Range][Count]", or legacy "_cgacha [Cat][ignored][Count]"
        ParsedInput input = parseBracketed(raw);
        if (input == null) {
            channel.sendMessage("Usage: `_cgacha [All|Ability|Familiar|Item|Skill|Trait][Count]`\n"
                    + "Or with rarity filter: `_cgacha [Category][min-max][Count]` (e.g., `_cgacha [familiar][5-7][10]`)\n"
                    + "Also supported: `_cgacha [Category][ignored][Count]` (legacy 3-bracket form).").queue();
            return;
        }

        // Build per-request pools from cache (filter in-memory if rarity range specified)
        Map<Category, List<GachaEntry>> data = new EnumMap<>(Category.class);
        for (Category c : new Category[]{Category.ABILITY, Category.FAMILIAR, Category.ITEM, Category.SKILL, Category.TRAIT}) {
            List<GachaEntry> entries = new ArrayList<>(CACHE.getOrDefault(c, Collections.emptyList()));
            if (input.rarityRange != null) {
                entries = entries.stream()
                        .filter(e -> input.rarityRange.contains(e.threshold))
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
            }
            data.put(c, entries);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("**C.C Tier Gacha**\n")
                .append("Category: `").append(input.category.name().toLowerCase(Locale.ROOT)).append("`  •  Rolls: `")
                .append(input.count).append("`");

        if (input.rarityRange != null) {
            sb.append("  •  Range: `").append(input.rarityRange.toString()).append("`");
        }

        sb.append("\n────────────────────────\n");

        for (int i = 1; i <= input.count; i++) {
            Category actual = (input.category == Category.ALL) ? Category.randomSpecific() : input.category;
            List<GachaEntry> pool = data.getOrDefault(actual, Collections.emptyList());

            if (pool.isEmpty()) {
                sb.append("**Roll ").append(i).append("** — `").append(actual.name().toLowerCase(Locale.ROOT))
                        .append("` — ⚠️ _No entries in range");
                if (input.rarityRange != null) {
                    sb.append(" ").append(input.rarityRange.toString());
                }
                sb.append(" (missing file or no matches?)_\n");
                continue;
            }

            // When rarity range is specified, pick directly from filtered pool
            if (input.rarityRange != null) {
                GachaEntry pick = pool.get(ThreadLocalRandom.current().nextInt(pool.size()));

                sb.append("**🎲 Roll ").append(i).append(":** `").append(actual.name().toLowerCase(Locale.ROOT)).append("` — ")
                        .append("Range: **").append(input.rarityRange.toString()).append("**\n")
                        .append("→ **").append(escapeBold(pick.name)).append("** _(").append(ONE_DEC.format(pick.threshold)).append(")_\n");

                if (pick.description != null && !pick.description.isBlank()) {
                    String desc = pick.description.startsWith("#") ? pick.description.substring(1).trim() : pick.description.trim();
                    sb.append("> ").append(desc).append("\n");
                }
            } else {
                // Original logic for normal gacha rolls
                // 1) roll base tier by weights
                int baseTier = rollWeightedTier(TIER_WEIGHTS);

                // 2) d20 swing
                int d20 = 1 + ThreadLocalRandom.current().nextInt(20);
                int finalTier = baseTier;
                String swingNote = "";
                boolean specialPrinted = false;

                if (d20 == 20) {
                    if (baseTier < 9) {
                        finalTier = baseTier + 1;
                        swingNote = "nat20 ⇒ tier up";
                    } else {
                        // Transcendent + nat20 special
                        swingNote = "nat20 @ Transcendent";
                        sb.append("**🎲 Roll ").append(i).append(":** `").append(actual.name().toLowerCase(Locale.ROOT)).append("` ")
                                .append("— **You Win. You have every other perk in the gacha.** ")
                                .append("`base=").append(TIER_NAMES[baseTier]).append(" d20=").append(d20).append("`\n\n");
                        specialPrinted = true;
                    }
                } else if (d20 == 1) {
                    if (baseTier > 0) {
                        finalTier = baseTier - 1;
                        swingNote = "nat1 ⇒ tier down";
                    } else {
                        // Trash + nat1 special
                        swingNote = "nat1 @ Trash";
                        sb.append("**🎲 Roll ").append(i).append(":** `").append(actual.name().toLowerCase(Locale.ROOT)).append("` ")
                                .append("— **You are dead. On the spot, inevitably, irreversibly. Your system is deleted; nothing will help you.** ")
                                .append("`base=").append(TIER_NAMES[baseTier]).append(" d20=").append(d20).append("`\n\n");
                        specialPrinted = true;
                    }
                } else {
                    swingNote = "d20=" + d20 + " ⇒ no change";
                }

                if (specialPrinted) continue; // skip perk pick on the two special cases

                // 3) pick random perk within the FINAL tier; if none, fallback to nearest tier with entries
                GachaEntry pick = pickRandomInTierOrNearest(pool, finalTier);

                sb.append("**🎲 Roll ").append(i).append(":** `").append(actual.name().toLowerCase(Locale.ROOT)).append("` — ")
                        .append("Tier: **").append(TIER_NAMES[finalTier]).append("**  ")
                        .append("`base=").append(TIER_NAMES[baseTier]).append(", ").append(swingNote).append("`\n")
                        .append("→ **").append(escapeBold(pick.name)).append("** _(").append(ONE_DEC.format(pick.threshold)).append(")_\n");

                if (pick.description != null && !pick.description.isBlank()) {
                    String desc = pick.description.startsWith("#") ? pick.description.substring(1).trim() : pick.description.trim();
                    sb.append("> ").append(desc).append("\n");
                }
            }

            if (i != input.count) sb.append("\n");
        }

        // split & send
        for (String chunk : splitForDiscord(sb.toString(), DISCORD_LIMIT)) {
            channel.sendMessage(chunk).queue();
        }
    }

    @Override
    public CommandData getCommandData() { return null; } // prefix command

    @Override
    public String getName() { return "_cgacha"; } // keep your trigger; alias handled in parser

    @Override
    public String getDescription() {
        return "Tiered gacha with d20 swing; picks a random perk from the final tier or specified rarity range.";
    }

    @Override
    public String getHelp() {
        return "Usage: _cgacha [All|Ability|Familiar|Item|Skill|Trait][Count]\n"
                + "With rarity filter: _cgacha [Category][min-max][Count] (e.g., _cgacha [familiar][5.0-7.5][10])\n"
                + "Legacy: _cgacha [Category][ignored][Count]\n"
                + "Files expected in resources or working dir: gacha/ability.txt, familiar.txt, item.txt, skill.txt, trait.txt\n"
                + "Entry format (two lines per entry):\n"
                + "  1. Name,3.3\n"
                + "  #Description";
    }

    /* ------------------------ Helpers ------------------------ */

    private ParsedInput parseBracketed(String raw) {
        if (raw == null) return null;

        String low = raw.toLowerCase(Locale.ROOT);
        boolean starts =
                low.startsWith("_cgacha") ||
                        low.startsWith("c.c_cgacha"); // alias

        if (!starts) return null;

        // Grab all [ ... ] parts
        Matcher m = Pattern.compile("\\[(.+?)\\]").matcher(raw);
        List<String> args = new ArrayList<>();
        while (m.find()) args.add(m.group(1).trim());
        if (args.isEmpty()) return null;

        Category cat = Category.fromUser(args.get(0)).orElse(Category.ALL);
        RarityRange range = null;
        int count = 1;

        if (args.size() == 2) {
            // Could be [Category][Count] or [Category][Range] with implicit count=1
            String second = args.get(1);
            RarityRange parsedRange = parseRarityRange(second);
            if (parsedRange != null) {
                range = parsedRange;
                count = 1;
            } else {
                count = parseIntSafe(second, 1);
            }
        } else if (args.size() >= 3) {
            // [Category][Range][Count] or legacy [Category][ignored][Count]
            String second = args.get(1);
            RarityRange parsedRange = parseRarityRange(second);
            if (parsedRange != null) {
                range = parsedRange;
                count = parseIntSafe(args.get(2), 1);
            } else {
                // legacy three-bracket form: [cat][ignored][count]
                count = parseIntSafe(args.get(2), 1);
            }
        }

        if (count < 1) count = 1;
        if (count > 10) count = 10;

        return new ParsedInput(cat, count, range);
    }

    private RarityRange parseRarityRange(String s) {
        if (s == null || s.trim().isEmpty()) return null;

        // Look for pattern like "5-7", "5.0-7.5", "3.2-9", etc.
        Pattern rangePattern = Pattern.compile("^\\s*([0-9]+(?:\\.[0-9]+)?)\\s*-\\s*([0-9]+(?:\\.[0-9]+)?)\\s*$");
        Matcher matcher = rangePattern.matcher(s);

        if (matcher.matches()) {
            try {
                double min = Double.parseDouble(matcher.group(1));
                double max = Double.parseDouble(matcher.group(2));
                return new RarityRange(min, max);
            } catch (NumberFormatException e) {
                return null;
            }
        }

        return null;
    }

    private int parseIntSafe(String s, int d) {
        try { return Integer.parseInt(s); } catch (Exception e) { return d; }
    }

    private static int rollWeightedTier(double[] weights) {
        double total = 0;
        for (double w : weights) total += w;
        double r = ThreadLocalRandom.current().nextDouble(total);
        double cum = 0;
        for (int i = 0; i < weights.length; i++) {
            cum += weights[i];
            if (r < cum) return i;
        }
        return weights.length - 1; // safety
    }

    private static GachaEntry pickRandomInTierOrNearest(List<GachaEntry> pool, int tier) {
        // Group entries by their integer tier
        Map<Integer, List<GachaEntry>> byTier = new HashMap<>();
        for (GachaEntry e : pool) {
            byTier.computeIfAbsent(e.tierInt(), k -> new ArrayList<>()).add(e);
        }

        if (byTier.containsKey(tier)) {
            List<GachaEntry> inTier = byTier.get(tier);
            return inTier.get(ThreadLocalRandom.current().nextInt(inTier.size()));
        }

        // Fallback: search nearest tiers outward
        for (int delta = 1; delta <= 9; delta++) {
            int down = tier - delta, up = tier + delta;
            boolean hasDown = byTier.containsKey(down);
            boolean hasUp = byTier.containsKey(up);
            if (hasDown && hasUp) {
                // randomly choose between available nearest sides
                if (ThreadLocalRandom.current().nextBoolean()) {
                    List<GachaEntry> list = byTier.get(down);
                    return list.get(ThreadLocalRandom.current().nextInt(list.size()));
                } else {
                    List<GachaEntry> list = byTier.get(up);
                    return list.get(ThreadLocalRandom.current().nextInt(list.size()));
                }
            } else if (hasDown) {
                List<GachaEntry> list = byTier.get(down);
                return list.get(ThreadLocalRandom.current().nextInt(list.size()));
            } else if (hasUp) {
                List<GachaEntry> list = byTier.get(up);
                return list.get(ThreadLocalRandom.current().nextInt(list.size()));
            }
        }

        // Last resort: truly any entry
        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private static String escapeBold(String s) {
        return s.replace("**", "\\*\\*");
    }

    private static List<GachaEntry> loadEntriesFor(Category c) throws IOException {
        String path = "gacha/" + c.fileBase + ".txt";
        List<GachaEntry> list = tryLoadFromClasspath(path);
        if (list == null || list.isEmpty()) {
            File f = new File("./" + c.fileBase + ".txt"); // FS fallback
            if (f.exists()) {
                list = parseEntries(new FileInputStream(f));
            }
        }
        return (list != null) ? list : Collections.emptyList();
    }

    private static List<GachaEntry> tryLoadFromClasspath(String path) throws IOException {
        ClassLoader cl = Thread.currentThread().getContextClassLoader();
        if (cl == null) cl = CgachaPrefixCommand.class.getClassLoader();
        URL url = cl.getResource(path);
        if (url == null) return null;
        try (InputStream in = url.openStream()) {
            return parseEntries(in);
        }
    }

    private static List<GachaEntry> parseEntries(InputStream in) throws IOException {
        List<GachaEntry> out = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            Pattern header = Pattern.compile("^\\s*\\d+\\.\\s*(.+?)\\s*,\\s*([0-9]+(?:\\.[0-9])?)\\s*$");
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
        // Sort not required, but keeps things tidy
        out.sort(Comparator.comparingDouble(e -> e.threshold));
        return out;
    }

    private static List<String> splitForDiscord(String text, int limit) {
        if (text.length() <= limit) return Collections.singletonList(text);
        List<String> parts = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(text.length(), start + limit);
            if (end < text.length()) {
                int nl = text.lastIndexOf('\n', end);
                if (nl > start && end - nl < 200) end = nl + 1;
            }
            parts.add(text.substring(start, end));
            start = end;
        }
        return parts;
    }
}