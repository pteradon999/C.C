package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WaifuGachaPrefixCommand implements ICommand {

    private static final int DISCORD_LIMIT = 2000;
    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Tier names for waifus (1-10)
    private static final String[] TIER_NAMES = {
            "Trash (1)", "Common (2)", "Uncommon (3)", "Rare (4)", "Elite (5)",
            "Epic (6)", "Legendary (7)", "Mythical (8)", "Divine (9)", "Transcendent (10)"
    };

    // Tier emojis for fancy display
    private static final String[] TIER_EMOJIS = {
            "💩", "🟫", "⚪", "🟢", "🔵",
            "🟣", "🟠", "🔴", "🟡", "⭐"
    };

    // Drop weights for tiers 1-10 (exponentially decreasing for higher tiers)
    private static final double[] TIER_WEIGHTS = {
            10.0, 10.0, 10.0, 10.0, 10.0,
            10.0,  10.0,  10.0,  10.0, 10.0
    };

    // characters.json loaded once at startup
    private static final List<Waifu> WAIFU_CACHE;

    static {
        List<Waifu> loaded = Collections.emptyList();
        try {
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if (cl == null) cl = WaifuGachaPrefixCommand.class.getClassLoader();
            URL u = cl.getResource("characters.json");
            if (u != null) {
                try (InputStream in = u.openStream()) {
                    loaded = parseWaifusStatic(in);
                }
            } else {
                File f = new File("./characters.json");
                if (f.exists()) {
                    try (FileInputStream fis = new FileInputStream(f)) {
                        loaded = parseWaifusStatic(fis);
                    }
                }
            }
        } catch (IOException ignored) {}
        WAIFU_CACHE = Collections.unmodifiableList(loaded);
    }

    private static List<Waifu> parseWaifusStatic(InputStream in) throws IOException {
        List<Waifu> out = new ArrayList<>();
        JsonNode root = MAPPER.readTree(in);
        if (root.isArray()) for (JsonNode n : root) out.add(new Waifu(n));
        return out;
    }

    private static class Waifu {
        final int id;
        final String name;
        final int tier;
        final String world;
        final List<String> badges;

        Waifu(JsonNode node) {
            this.id = node.has("u") ? node.get("u").asInt() : 0;
            this.name = node.has("n") ? node.get("n").asText() : "Unknown";
            this.tier = node.has("t") ? node.get("t").asInt() : 1;
            this.world = node.has("w") ? node.get("w").asText() : "Unknown";
            this.badges = new ArrayList<>();
            if (node.has("b") && node.get("b").isArray()) {
                for (JsonNode b : node.get("b")) badges.add(b.asText().toUpperCase(Locale.ROOT));
            }
        }
        String getTierDisplay() {
            int idx = Math.max(0, Math.min(tier-1, TIER_NAMES.length-1));
            return TIER_EMOJIS[idx]+" **"+TIER_NAMES[idx]+"**";
        }
    }

    private static class TierRange {
        final int min, max;
        TierRange(int min, int max) {
            this.min = Math.max(1, Math.min(min, max));
            this.max = Math.min(10, Math.max(min, max));
        }
        boolean contains(int t) { return t>=min && t<=max; }
        public String toString() { return min+"-"+max; }
    }

    private static class ParsedInput {
        final TierRange tierRange;
        final int count;
        final String badgeFilter;
        ParsedInput(TierRange r, int c, String f) {
            tierRange=r; count=c; badgeFilter=f;
        }
    }

    @Override
    public void handle(CommandContext ctx) {
        Message msg = ctx.getMessageEvent().getMessage();
        String raw = msg!=null?msg.getContentRaw():null;
        MessageChannel channel = ctx.getChannel();

        ParsedInput input = parseBracketed(raw);
        if (input==null) {
            channel.sendMessage(
                    "Usage: `C.C_waifugacha [tier-range][count][F/M/All]`\n" +
                            "Example: `C.C_waifugacha [3-7][5][F]`"
            ).queue();
            return;
        }

        List<Waifu> all = WAIFU_CACHE;
        if (all.isEmpty()) {
            channel.sendMessage("❌ Error loading `characters.json`").queue();
            return;
        }

        // filter by tier
        List<Waifu> pool = new ArrayList<>();
        for (Waifu w: all) if (input.tierRange.contains(w.tier)) pool.add(w);

        // filter by badge
        if (!input.badgeFilter.equals("ALL")) {
            pool.removeIf(w->!w.badges.contains(input.badgeFilter));
        }
        if (pool.isEmpty()) {
            channel.sendMessage(
                    "❌ No waifus in tier "+input.tierRange+" with badge "+input.badgeFilter
            ).queue();
            return;
        }

        StringBuilder sb = new StringBuilder()
                .append("🎀 **Waifu Gacha** 🎀\n")
                .append("Range:`").append(input.tierRange).append("` • ")
                .append("Count:`").append(input.count).append("` • ")
                .append("Badge:`").append(input.badgeFilter).append("`\n")
                .append("══════════════════\n");

        for (int i=1;i<=input.count;i++) {
            int tier = rollTier(input.tierRange);
            List<Waifu> tierList = new ArrayList<>();
            for (Waifu w: pool) if (w.tier==tier) tierList.add(w);
            if (tierList.isEmpty()) tierList = Collections.singletonList(
                    pool.get(ThreadLocalRandom.current().nextInt(pool.size()))
            );
            Waifu pick = tierList.get(ThreadLocalRandom.current().nextInt(tierList.size()));

            sb.append("🎲 Roll ").append(i).append(": ")
                    .append(pick.getTierDisplay()).append("\n")
                    .append("💖 **").append(escapeBold(pick.name)).append("**")
                    .append(" _(").append(escapeBold(pick.world)).append(")_\n");
            if (pick.badges.size()>0) {
                sb.append("🏷️ ");
                for (int j=0;j<pick.badges.size();j++) {
                    if(j>0) sb.append(" • ");
                    sb.append("`").append(pick.badges.get(j)).append("`");
                }
                sb.append("\n");
            }
            if (pick.tier>=9) sb.append("✨ Legendary Pull! ✨\n");
            else if(pick.tier>=7) sb.append("🌟 Rare Find! 🌟\n");
            if(i<input.count) sb.append("\n");
        }

        for (String chunk: splitForDiscord(sb.toString(),DISCORD_LIMIT))
            channel.sendMessage(chunk).queue();
    }

    private ParsedInput parseBracketed(String raw) {
        if (raw==null) return null;
        String lc = raw.toLowerCase(Locale.ROOT).trim();
        String cmd="c.c_waifugacha";
        if (!lc.startsWith(cmd)) return null;
        String args = raw.substring(cmd.length()).trim();
        Pattern p = Pattern.compile("\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]\\s*\\[([^\\]]+)\\]");
        Matcher m = p.matcher(args);
        if (!m.find()) return null;
        TierRange r = parseTierRange(m.group(1).trim());
        if (r==null) return null;
        int c;
        try { c=Integer.parseInt(m.group(2).trim()); }
        catch(Exception e){return null;}
        c=Math.max(1,Math.min(10,c));
        String b = m.group(3).trim().toUpperCase(Locale.ROOT);
        if (!b.equals("F")&&!b.equals("M")) b="ALL";
        return new ParsedInput(r,c,b);
    }

    private TierRange parseTierRange(String s) {
        Matcher m=Pattern.compile("(\\d+)-(\\d+)").matcher(s);
        if(!m.find())return null;
        try{
            return new TierRange(Integer.parseInt(m.group(1)),
                    Integer.parseInt(m.group(2)));
        }catch(Exception e){return null;}
    }

    private int rollTier(TierRange range) {
        double[] w=new double[10]; double total=0;
        for(int i=1;i<=10;i++){
            if(range.contains(i)){
                w[i-1]=TIER_WEIGHTS[i-1];
                total+=w[i-1];
            }
        }
        if(total==0){
            int size=range.max-range.min+1;
            return range.min+ThreadLocalRandom.current().nextInt(size);
        }
        double r=ThreadLocalRandom.current().nextDouble(total),cum=0;
        for(int i=1;i<=10;i++){
            if(range.contains(i)){
                cum+=w[i-1];
                if(r<cum) return i;
            }
        }
        return range.max;
    }


    private static String escapeBold(String s) {
        return s.replace("**", "\\*\\*");
    }

    private static List<String> splitForDiscord(String t,int lim){
        if(t.length()<=lim) return Collections.singletonList(t);
        List<String> p=new ArrayList<>();
        int st=0;
        while(st<t.length()){
            int e=Math.min(t.length(),st+lim);
            if(e<t.length()){
                int nl=t.lastIndexOf('\n',e);
                if(nl>st&&e-nl<200) e=nl+1;
            }
            p.add(t.substring(st,e));
            st=e;
        }
        return p;
    }

    @Override public CommandData getCommandData(){return null;}
    @Override public String getName(){return "_waifugacha";}
    @Override public String getDescription(){
        return "Waifu gacha with tier, count, gender filter from characters.json";
    }
    @Override public String getHelp(){
        return "Usage: C.C_waifugacha [tier-range][count][F/M/All]";
    }
}
