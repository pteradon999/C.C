package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import cc.gen.second.config;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class ComfyUICommand implements ICommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComfyUICommand.class);

    private static final String OWNER_ID = "253963350944251915";
    private static final long POLL_INTERVAL_MS = 3000;
    private static final long MAX_WAIT_MS = 600_000; // 10 minutes max
    private static final int MAX_COUNT = 50;

    // LoRA chain node IDs in workflow order (model/clip wiring)
    private static final String[] LORA_CHAIN_NODE_IDS = {
            "1412", "1413", "1414", "1418", "1415", "1417", "1416", "10"
    };

    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    private static final ExecutorService EXECUTOR = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "ComfyUI-Worker");
        t.setDaemon(true);
        return t;
    });

    // Cached workflow template
    private static volatile String workflowTemplate;

    // Active generation loops per channel (for stop command)
    private static final ConcurrentHashMap<String, AtomicBoolean> activeLoops = new ConcurrentHashMap<>();

    // Persistent LoRA weight overrides (survive across commands)
    // Key: node ID, Value: [strength_model, strength_clip]
    private static final ConcurrentHashMap<String, double[]> persistentLoraWeights = new ConcurrentHashMap<>();

    private static String getComfyUrl() {
        String url = config.get("COMFYUI_URL");
        return (url != null && !url.isEmpty()) ? url : "http://127.0.0.1:8188";
    }

    private static String getGelbooruUserId() {
        String id = config.get("GELBOORU_USER_ID");
        return (id != null) ? id : "";
    }

    private static String getGelbooruApiKey() {
        String key = config.get("GELBOORU_API_KEY");
        return (key != null) ? key : "";
    }

    @Override
    public void handle(@NotNull CommandContext ctx) {
        // Owner-only command
        if (!ctx.getAuthor().getId().equals(OWNER_ID)) {
            ctx.getChannel().sendMessage("You are not authorized to use this command.").queue();
            return;
        }

        List<String> args = ctx.getArgs();

        // Route subcommands
        if (args != null && !args.isEmpty()) {
            String sub = args.get(0).toLowerCase();
            switch (sub) {
                case "help":
                    sendHelp(ctx.getChannel());
                    return;
                case "stop":
                    handleStop(ctx);
                    return;
                case "loras":
                    handleListLoras(ctx);
                    return;
                case "lora":
                    handleSetLora(ctx, args.subList(1, args.size()));
                    return;
            }
        }

        // Check if a loop is already running in this channel
        String channelId = ctx.getChannel().getId();
        if (activeLoops.containsKey(channelId)) {
            ctx.getChannel().sendMessage("A generation loop is already running. Use `_comfy stop` first.").queue();
            return;
        }

        // Parse parameters from args
        WorkflowParams params = parseArgs(args);
        executeGeneration(ctx, params);
    }

    private void handleStop(CommandContext ctx) {
        String channelId = ctx.getChannel().getId();
        AtomicBoolean running = activeLoops.get(channelId);
        if (running != null) {
            running.set(false);
            ctx.getChannel().sendMessage("Stopping generation loop...").queue();
        } else {
            ctx.getChannel().sendMessage("No active generation loop in this channel.").queue();
        }
    }

    private void handleListLoras(CommandContext ctx) {
        try {
            List<LoraInfo> loras = getActiveLoraList();
            if (loras.isEmpty()) {
                ctx.getChannel().sendMessage("No active LoRAs found.").queue();
                return;
            }

            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < loras.size(); i++) {
                LoraInfo l = loras.get(i);
                // Show overridden weights if they exist
                double[] override = persistentLoraWeights.get(l.nodeId);
                double modelW = override != null ? override[0] : l.strengthModel;
                double clipW = override != null ? override[1] : l.strengthClip;
                String marker = override != null ? " *" : "";
                sb.append(String.format("**%d.** `%s`\n   model: **%.2f** | clip: **%.2f**%s\n",
                        i + 1, l.name, modelW, clipW, marker));
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(138, 43, 226))
                    .setTitle("Active LoRAs")
                    .setDescription(sb.toString());

            if (!persistentLoraWeights.isEmpty()) {
                embed.setFooter("* = weight overridden via _comfy lora");
            }

            ctx.getChannel().sendMessageEmbeds(embed.build()).queue();
        } catch (IOException e) {
            LOGGER.error("Failed to list LoRAs", e);
            ctx.getChannel().sendMessage("Failed to read workflow template: " + e.getMessage()).queue();
        }
    }

    private void handleSetLora(CommandContext ctx, List<String> args) {
        if (args.isEmpty()) {
            ctx.getChannel().sendMessage("Usage: `_comfy lora 1:0.2 2:0.6` or `_comfy lora 1:0.3:0.4` (model:clip)\n"
                    + "Use `_comfy lora reset` to clear overrides.").queue();
            return;
        }

        // Handle reset
        if (args.get(0).equalsIgnoreCase("reset")) {
            persistentLoraWeights.clear();
            ctx.getChannel().sendMessage("LoRA weight overrides cleared.").queue();
            return;
        }

        try {
            List<LoraInfo> loras = getActiveLoraList();
            StringBuilder result = new StringBuilder();

            for (String arg : args) {
                // Format: index:model_weight or index:model_weight:clip_weight
                String[] parts = arg.split(":");
                if (parts.length < 2) {
                    result.append("Skipped invalid: `").append(arg).append("`\n");
                    continue;
                }

                int index;
                double modelWeight, clipWeight;
                try {
                    index = Integer.parseInt(parts[0]);
                    modelWeight = Double.parseDouble(parts[1]);
                    clipWeight = parts.length >= 3 ? Double.parseDouble(parts[2]) : modelWeight;
                } catch (NumberFormatException e) {
                    result.append("Skipped invalid: `").append(arg).append("`\n");
                    continue;
                }

                if (index < 1 || index > loras.size()) {
                    result.append("LoRA #").append(index).append(" out of range (1-").append(loras.size()).append(")\n");
                    continue;
                }

                LoraInfo lora = loras.get(index - 1);
                persistentLoraWeights.put(lora.nodeId, new double[]{modelWeight, clipWeight});
                result.append(String.format("**%d.** `%s` -> model: **%.2f** | clip: **%.2f**\n",
                        index, lora.name, modelWeight, clipWeight));
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(new Color(138, 43, 226))
                    .setTitle("LoRA Weights Updated")
                    .setDescription(result.toString())
                    .setFooter("Weights persist until reset or bot restart");

            ctx.getChannel().sendMessageEmbeds(embed.build()).queue();
        } catch (IOException e) {
            LOGGER.error("Failed to set LoRA weights", e);
            ctx.getChannel().sendMessage("Failed to read workflow template: " + e.getMessage()).queue();
        }
    }

    private List<LoraInfo> getActiveLoraList() throws IOException {
        String template = loadWorkflowTemplate();
        JSONObject workflow = new JSONObject(template);
        List<LoraInfo> active = new ArrayList<>();

        for (String nodeId : LORA_CHAIN_NODE_IDS) {
            if (!workflow.has(nodeId)) continue;
            JSONObject inputs = workflow.getJSONObject(nodeId).getJSONObject("inputs");
            double strengthModel = inputs.optDouble("strength_model", 0);
            double strengthClip = inputs.optDouble("strength_clip", 0);

            if (strengthModel != 0 || strengthClip != 0) {
                String loraName = inputs.optString("lora_name", "unknown");
                // Extract short name: strip path and extension
                String shortName = loraName;
                int lastBackslash = shortName.lastIndexOf('\\');
                int lastSlash = shortName.lastIndexOf('/');
                int lastSep = Math.max(lastBackslash, lastSlash);
                if (lastSep >= 0) shortName = shortName.substring(lastSep + 1);
                if (shortName.endsWith(".safetensors")) {
                    shortName = shortName.substring(0, shortName.length() - ".safetensors".length());
                }
                active.add(new LoraInfo(nodeId, shortName, strengthModel, strengthClip));
            }
        }
        return active;
    }

    private void executeGeneration(CommandContext ctx, WorkflowParams params) {
        String channelId = ctx.getChannel().getId();
        boolean isLoop = params.infinite || params.count > 1;
        String countLabel = params.infinite ? "infinite" : String.valueOf(params.count);

        ctx.getChannel().sendMessage("Queuing workflow on ComfyUI..."
                + (isLoop ? " (count: " + countLabel + ")" : "")).queue();

        EXECUTOR.submit(() -> {
            AtomicBoolean running = new AtomicBoolean(true);
            if (isLoop) {
                activeLoops.put(channelId, running);
            }

            try {
                int iterations = params.infinite ? Integer.MAX_VALUE : params.count;
                for (int i = 0; i < iterations && running.get(); i++) {
                    try {
                        String promptId = queueWorkflow(params);
                        if (i == 0) {
                            ctx.getChannel().sendMessage("Workflow queued. Prompt ID: `" + promptId + "`. Waiting for result...").queue();
                        }

                        JSONObject result = pollForResult(promptId);
                        if (result == null) {
                            ctx.getChannel().sendMessage("Workflow timed out after 10 minutes.").queue();
                            break;
                        }

                        ImageInfo imageInfo = extractOutputImage(result, promptId);
                        if (imageInfo == null) {
                            ctx.getChannel().sendMessage("Workflow completed but no output image found.").queue();
                            continue;
                        }

                        byte[] imageBytes = downloadImage(imageInfo);
                        if (imageBytes == null || imageBytes.length == 0) {
                            ctx.getChannel().sendMessage("Failed to download the generated image.").queue();
                            continue;
                        }

                        EmbedBuilder embed = buildResultEmbed(params, i + 1, isLoop);
                        ctx.getChannel().sendMessageEmbeds(embed.build())
                                .addFiles(FileUpload.fromData(imageBytes, imageInfo.filename))
                                .queue();

                    } catch (Exception e) {
                        LOGGER.error("ComfyUI workflow failed on iteration " + (i + 1), e);
                        ctx.getChannel().sendMessage("Workflow failed: " + e.getMessage()).queue();
                        if (!isLoop) break;
                    }
                }

                if (isLoop && !running.get()) {
                    ctx.getChannel().sendMessage("Generation loop stopped.").queue();
                }
            } finally {
                activeLoops.remove(channelId);
            }
        });
    }

    private EmbedBuilder buildResultEmbed(WorkflowParams params, int iteration, boolean isLoop) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(138, 43, 226))
                .setTitle("Generation Complete" + (isLoop ? " (#" + iteration + ")" : ""))
                .addField("Seed", String.valueOf(params.seed), true)
                .addField("Steps", String.valueOf(params.steps), true)
                .addField("CFG", String.valueOf(params.cfg), true)
                .addField("Mode", getModeLabel(params.mode), true)
                .addField("Style", params.style == 1 ? "Rindex" : "Desuka", true)
                .addField("Resolution", params.resolution, true);

        if (params.scaleBy != 1.5) {
            embed.addField("Scale", String.valueOf(params.scaleBy), true);
        }
        if (params.character != null && !params.character.isEmpty()) {
            embed.addField("Character", params.character, false);
        }
        if (params.tags != null && !params.tags.isEmpty()) {
            embed.addField("Gelbooru Tags", params.tags, false);
        }
        if (params.generalTags != null && !params.generalTags.isEmpty()) {
            embed.addField("Extra Tags", params.generalTags, false);
        }
        if (!persistentLoraWeights.isEmpty()) {
            embed.setFooter("LoRA weights overridden");
        }
        return embed;
    }

    private WorkflowParams parseArgs(List<String> args) {
        WorkflowParams params = new WorkflowParams();
        if (args == null) return params;

        List<String> generalTagParts = new ArrayList<>();

        for (String arg : args) {
            String lower = arg.toLowerCase();
            if (lower.startsWith("seed:")) {
                try {
                    params.seed = Long.parseLong(arg.substring(5));
                } catch (NumberFormatException ignored) {}
            } else if (lower.startsWith("steps:")) {
                try {
                    params.steps = Integer.parseInt(arg.substring(6));
                } catch (NumberFormatException ignored) {}
            } else if (lower.startsWith("cfg:")) {
                try {
                    params.cfg = Double.parseDouble(arg.substring(4));
                } catch (NumberFormatException ignored) {}
            } else if (lower.startsWith("mode:")) {
                try {
                    params.mode = Integer.parseInt(arg.substring(5));
                    if (params.mode < 1 || params.mode > 4) params.mode = 3;
                } catch (NumberFormatException ignored) {}
            } else if (lower.startsWith("style:")) {
                String val = arg.substring(6).toLowerCase();
                if (val.equals("desuka") || val.equals("2")) {
                    params.style = 2;
                } else {
                    params.style = 1;
                }
            } else if (lower.startsWith("res:") || lower.startsWith("resolution:")) {
                String val = arg.contains(":") ? arg.substring(arg.indexOf(':') + 1) : "";
                if (!val.isEmpty()) params.resolution = val;
            } else if (lower.startsWith("char:") || lower.startsWith("character:")) {
                String val = arg.substring(arg.indexOf(':') + 1);
                params.character = val;
                params.charSwitch = 2; // manual character mode
            } else if (lower.equals("random") || lower.equals("randomchar")) {
                params.charSwitch = 1; // random character mode
            } else if (lower.startsWith("tags:")) {
                String val = arg.substring(5);
                if (!val.isEmpty()) params.tags = val;
            } else if (lower.startsWith("scale:")) {
                try {
                    params.scaleBy = Double.parseDouble(arg.substring(6));
                    if (params.scaleBy < 0.5) params.scaleBy = 0.5;
                    if (params.scaleBy > 4.0) params.scaleBy = 4.0;
                } catch (NumberFormatException ignored) {}
            } else if (lower.startsWith("count:")) {
                String val = arg.substring(6).toLowerCase();
                if (val.equals("inf") || val.equals("infinite") || val.equals("loop")) {
                    params.infinite = true;
                } else {
                    try {
                        params.count = Integer.parseInt(val);
                        if (params.count < 1) params.count = 1;
                        if (params.count > MAX_COUNT) params.count = MAX_COUNT;
                    } catch (NumberFormatException ignored) {}
                }
            } else {
                // Unrecognized arg becomes a general tag
                generalTagParts.add(arg);
            }
        }

        if (!generalTagParts.isEmpty()) {
            params.generalTags = String.join(", ", generalTagParts);
        }

        return params;
    }

    private String loadWorkflowTemplate() throws IOException {
        if (workflowTemplate != null) return workflowTemplate;
        synchronized (ComfyUICommand.class) {
            if (workflowTemplate != null) return workflowTemplate;
            try (InputStream is = getClass().getClassLoader().getResourceAsStream("comfyui_workflow.json")) {
                if (is == null) throw new IOException("comfyui_workflow.json not found in resources");
                workflowTemplate = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }
            return workflowTemplate;
        }
    }

    private String buildWorkflowJson(WorkflowParams params) throws IOException {
        String template = loadWorkflowTemplate();
        JSONObject workflow = new JSONObject(template);

        // Set seed (node 22)
        long seed = params.seed == -1
                ? ThreadLocalRandom.current().nextLong(0, 999999999999999L)
                : params.seed;
        workflow.getJSONObject("22").getJSONObject("inputs").put("seed", seed);

        // Set steps (node 1424)
        workflow.getJSONObject("1424").getJSONObject("inputs").put("Value", params.steps);

        // Set CFG (node 3)
        workflow.getJSONObject("3").getJSONObject("inputs").put("cfg", params.cfg);

        // Set character (node 779) and char switch (node 778)
        workflow.getJSONObject("779").getJSONObject("inputs").put("text", params.character != null ? params.character : "");
        workflow.getJSONObject("778").getJSONObject("inputs").put("Input", params.charSwitch);

        // Set prompt mode (node 785)
        workflow.getJSONObject("785").getJSONObject("inputs").put("Input", params.mode);

        // Set style switch (node 1044)
        workflow.getJSONObject("1044").getJSONObject("inputs").put("Input", params.style);

        // Set resolution (node 1108)
        workflow.getJSONObject("1108").getJSONObject("inputs").put("resolution", params.resolution);

        // Set upscale multiplier (node 11 - Множитель Апскейла)
        workflow.getJSONObject("11").getJSONObject("inputs").put("scale_by", params.scaleBy);

        // Disconnect node 1300 (hardcoded Sailor Moon tags) from prompt pipeline
        workflow.getJSONObject("1345").getJSONObject("inputs").put("text_b", "");

        // Replace ГЕНЕРАЛКА placeholder with user's general tags (or clear it)
        String generalTagValue = (params.generalTags != null && !params.generalTags.isEmpty())
                ? params.generalTags : "";
        // Node 843 (Booru Prompter mode): text_e = "ГЕНЕРАЛКА"
        workflow.getJSONObject("843").getJSONObject("inputs").put("text_e", generalTagValue);
        // Node 794 (Combo+Auto mode): text_d = "ГЕНЕРАЛКА"
        workflow.getJSONObject("794").getJSONObject("inputs").put("text_d", generalTagValue);

        // Inject Gelbooru credentials if configured (nodes 842, 1300)
        String gelbooruUserId = getGelbooruUserId();
        String gelbooruApiKey = getGelbooruApiKey();
        if (!gelbooruUserId.isEmpty()) {
            workflow.getJSONObject("842").getJSONObject("inputs").put("user_id", gelbooruUserId);
            workflow.getJSONObject("1300").getJSONObject("inputs").put("user_id", gelbooruUserId);
        }
        if (!gelbooruApiKey.isEmpty()) {
            workflow.getJSONObject("842").getJSONObject("inputs").put("api_key", gelbooruApiKey);
            workflow.getJSONObject("1300").getJSONObject("inputs").put("api_key", gelbooruApiKey);
        }

        // Set Gelbooru AND_tags if provided (node 842)
        if (params.tags != null && !params.tags.isEmpty()) {
            workflow.getJSONObject("842").getJSONObject("inputs").put("AND_tags", params.tags);
        }

        // Apply persistent LoRA weight overrides
        for (Map.Entry<String, double[]> entry : persistentLoraWeights.entrySet()) {
            String nodeId = entry.getKey();
            double[] weights = entry.getValue();
            if (workflow.has(nodeId)) {
                JSONObject inputs = workflow.getJSONObject(nodeId).getJSONObject("inputs");
                inputs.put("strength_model", weights[0]);
                inputs.put("strength_clip", weights[1]);
            }
        }

        // Randomize DPRandomGenerator seeds for variety
        randomizeDPSeeds(workflow, "1281", "1282", "1283", "1284", "1285", "1286", "1287", "1288");

        // Randomize Gelbooru seeds
        workflow.getJSONObject("842").getJSONObject("inputs")
                .put("seed", ThreadLocalRandom.current().nextLong(0, 999999999999999L));
        workflow.getJSONObject("1300").getJSONObject("inputs")
                .put("seed", ThreadLocalRandom.current().nextLong(0, 999999999999999L));

        // Randomize text random line seed (node 775)
        workflow.getJSONObject("775").getJSONObject("inputs")
                .put("seed", ThreadLocalRandom.current().nextLong(0, 999999999999999L));

        return workflow.toString();
    }

    private void randomizeDPSeeds(JSONObject workflow, String... nodeIds) {
        for (String id : nodeIds) {
            if (workflow.has(id)) {
                workflow.getJSONObject(id).getJSONObject("inputs")
                        .put("seed", ThreadLocalRandom.current().nextInt(0, 99999));
            }
        }
    }

    private String queueWorkflow(WorkflowParams params) throws IOException, InterruptedException {
        String workflowJson = buildWorkflowJson(params);
        String comfyUrl = getComfyUrl();

        JSONObject payload = new JSONObject();
        payload.put("prompt", new JSONObject(workflowJson));

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(comfyUrl + "/prompt"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("ComfyUI returned HTTP " + response.statusCode() + ": " + response.body());
        }

        JSONObject resp = new JSONObject(response.body());
        return resp.getString("prompt_id");
    }

    private JSONObject pollForResult(String promptId) throws IOException, InterruptedException {
        String comfyUrl = getComfyUrl();
        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() - startTime < MAX_WAIT_MS) {
            Thread.sleep(POLL_INTERVAL_MS);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(comfyUrl + "/history/" + promptId))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200) {
                JSONObject history = new JSONObject(response.body());
                if (history.has(promptId)) {
                    return history.getJSONObject(promptId);
                }
            }
        }
        return null;
    }

    private ImageInfo extractOutputImage(JSONObject result, String promptId) {
        try {
            JSONObject outputs = result.getJSONObject("outputs");

            // Check node 1339 (SaveImage) first, then node 1116 (SDPromptSaver)
            String[] outputNodes = {"1339", "1116", "1091"};
            for (String nodeId : outputNodes) {
                if (outputs.has(nodeId)) {
                    JSONObject nodeOutput = outputs.getJSONObject(nodeId);
                    if (nodeOutput.has("images")) {
                        JSONObject img = nodeOutput.getJSONArray("images").getJSONObject(0);
                        return new ImageInfo(
                                img.getString("filename"),
                                img.optString("subfolder", ""),
                                img.optString("type", "output")
                        );
                    }
                }
            }

            // Fallback: search all output nodes for images
            for (String key : outputs.keySet()) {
                JSONObject nodeOutput = outputs.getJSONObject(key);
                if (nodeOutput.has("images")) {
                    JSONObject img = nodeOutput.getJSONArray("images").getJSONObject(0);
                    return new ImageInfo(
                            img.getString("filename"),
                            img.optString("subfolder", ""),
                            img.optString("type", "output")
                    );
                }
            }
        } catch (Exception e) {
            LOGGER.error("Failed to extract output image from result", e);
        }
        return null;
    }

    private byte[] downloadImage(ImageInfo info) throws IOException, InterruptedException {
        String comfyUrl = getComfyUrl();
        String viewUrl = comfyUrl + "/view?filename=" + info.filename
                + "&subfolder=" + info.subfolder
                + "&type=" + info.type;

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(viewUrl))
                .GET()
                .timeout(Duration.ofSeconds(60))
                .build();

        HttpResponse<byte[]> response = HTTP.send(request, HttpResponse.BodyHandlers.ofByteArray());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to download image: HTTP " + response.statusCode());
        }
        return response.body();
    }

    private void sendHelp(MessageChannelUnion channel) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(new Color(138, 43, 226))
                .setTitle("ComfyUI Workflow Controller")
                .setDescription("Generate images using your ComfyUI workflow.")
                .addField("Usage", "`_comfy [tags] [options]`", false)
                .addField("Generation Options", String.join("\n",
                        "`seed:<number>` - Set seed (-1 for random, default: random)",
                        "`steps:<number>` - Set sampling steps (default: 25)",
                        "`cfg:<number>` - Set CFG scale (default: 6.5)",
                        "`scale:<number>` - Upscale multiplier (default: 1.5, range: 0.5-4.0)",
                        "`mode:<1-4>` - Prompt mode:",
                        "  1 = Normal, 2 = Combo, 3 = Booru (default), 4 = Combo+Auto",
                        "`style:<rindex|desuka>` - Style preset (default: rindex)",
                        "`res:<preset>` - Resolution preset (default: landscape - 1344x768)",
                        "`char:<name>` - Set character (switches to manual mode)",
                        "`random` - Use random character from built-in list",
                        "`tags:<tags>` - Set Gelbooru AND_tags (e.g. `tags:rwby,solo`)",
                        "`count:<number|inf>` - Generate multiple images (max " + MAX_COUNT + ", or `inf` for loop)",
                        "",
                        "**Bare text** (no prefix) is appended to the prompt as extra tags.",
                        "Example: `_comfy 1girl red_hair` adds those tags to the prompt."
                ), false)
                .addField("LoRA Commands", String.join("\n",
                        "`_comfy loras` - List active LoRAs and their weights",
                        "`_comfy lora 1:0.2 2:0.6` - Set LoRA weights by index (model weight)",
                        "`_comfy lora 1:0.3:0.4` - Set model and clip weights separately",
                        "`_comfy lora reset` - Clear all weight overrides"
                ), false)
                .addField("Loop Control", String.join("\n",
                        "`_comfy count:5` - Generate 5 images in sequence",
                        "`_comfy count:inf` - Generate until stopped",
                        "`_comfy stop` - Stop running generation loop"
                ), false)
                .addField("Examples", String.join("\n",
                        "`_comfy` - Generate with defaults",
                        "`_comfy 1girl beach sunset` - Generate with extra tags",
                        "`_comfy 1girl tags:rwby scale:2.0 steps:30` - Tags + Gelbooru + upscale",
                        "`_comfy count:inf random mode:1 cfg:7` - Infinite loop with random chars",
                        "`_comfy lora 1:0.5 3:0.8` - Set LoRA weights, then generate normally"
                ), false);

        channel.sendMessageEmbeds(embed.build()).queue();
    }

    private String getModeLabel(int mode) {
        switch (mode) {
            case 1: return "Normal";
            case 2: return "Combo";
            case 3: return "Booru Prompter";
            case 4: return "Combo+Auto";
            default: return "Unknown";
        }
    }

    @Override
    public CommandData getCommandData() {
        return null;
    }

    @Override
    public String getName() {
        return "_comfy";
    }

    @Override
    public List<String> getAliases() {
        return List.of("comfy", "_generate", "_gen");
    }

    @Override
    public String getHelp() {
        return "Control ComfyUI workflow from Discord. Usage: `_comfy [tags] [options]` - Run `_comfy help` for details.";
    }

    // --- Inner classes ---

    private static class WorkflowParams {
        long seed = -1;           // -1 = random
        int steps = 25;
        double cfg = 6.5;
        int mode = 3;             // 1=Normal, 2=Combo, 3=Booru, 4=Combo+Auto
        int style = 1;            // 1=Rindex, 2=Desuka
        String resolution = "landscape - 1344x768 (16:9)";
        String character = "";
        int charSwitch = 1;       // 1=random, 2=manual
        String tags = "";         // Gelbooru AND_tags (empty = use workflow default)
        double scaleBy = 1.5;     // Upscale multiplier (node 11)
        String generalTags = "";  // Extra tags injected into ГЕНЕРАЛКА fields (nodes 843/794)
        int count = 1;            // Number of images to generate
        boolean infinite = false; // Infinite generation loop
    }

    private static class ImageInfo {
        final String filename;
        final String subfolder;
        final String type;

        ImageInfo(String filename, String subfolder, String type) {
            this.filename = filename;
            this.subfolder = subfolder;
            this.type = type;
        }
    }

    private static class LoraInfo {
        final String nodeId;
        final String name;
        final double strengthModel;
        final double strengthClip;

        LoraInfo(String nodeId, String name, double strengthModel, double strengthClip) {
            this.nodeId = nodeId;
            this.name = name;
            this.strengthModel = strengthModel;
            this.strengthClip = strengthClip;
        }
    }
}
