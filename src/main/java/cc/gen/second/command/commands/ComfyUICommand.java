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
import java.util.List;
import java.util.concurrent.*;

public class ComfyUICommand implements ICommand {
    private static final Logger LOGGER = LoggerFactory.getLogger(ComfyUICommand.class);

    private static final String OWNER_ID = "253963350944251915";
    private static final long POLL_INTERVAL_MS = 3000;
    private static final long MAX_WAIT_MS = 600_000; // 10 minutes max

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
        if (args != null && !args.isEmpty() && args.get(0).equalsIgnoreCase("help")) {
            sendHelp(ctx.getChannel());
            return;
        }

        // Parse parameters from args
        WorkflowParams params = parseArgs(args);

        ctx.getChannel().sendMessage("Queuing workflow on ComfyUI...").queue();

        EXECUTOR.submit(() -> {
            try {
                String promptId = queueWorkflow(params);
                ctx.getChannel().sendMessage("Workflow queued. Prompt ID: `" + promptId + "`. Waiting for result...").queue();

                // Poll for completion
                JSONObject result = pollForResult(promptId);
                if (result == null) {
                    ctx.getChannel().sendMessage("Workflow timed out after 10 minutes.").queue();
                    return;
                }

                // Extract output image info
                ImageInfo imageInfo = extractOutputImage(result, promptId);
                if (imageInfo == null) {
                    ctx.getChannel().sendMessage("Workflow completed but no output image found.").queue();
                    return;
                }

                // Download and send image
                byte[] imageBytes = downloadImage(imageInfo);
                if (imageBytes == null || imageBytes.length == 0) {
                    ctx.getChannel().sendMessage("Failed to download the generated image.").queue();
                    return;
                }

                // Build embed with generation info
                EmbedBuilder embed = new EmbedBuilder()
                        .setColor(new Color(138, 43, 226))
                        .setTitle("Generation Complete")
                        .addField("Seed", String.valueOf(params.seed), true)
                        .addField("Steps", String.valueOf(params.steps), true)
                        .addField("CFG", String.valueOf(params.cfg), true)
                        .addField("Mode", getModeLabel(params.mode), true)
                        .addField("Style", params.style == 1 ? "Rindex" : "Desuka", true)
                        .addField("Resolution", params.resolution, true);

                if (params.character != null && !params.character.isEmpty()) {
                    embed.addField("Character", params.character, false);
                }

                ctx.getChannel().sendMessageEmbeds(embed.build())
                        .addFiles(FileUpload.fromData(imageBytes, imageInfo.filename))
                        .queue();

            } catch (Exception e) {
                LOGGER.error("ComfyUI workflow failed", e);
                ctx.getChannel().sendMessage("Workflow failed: " + e.getMessage()).queue();
            }
        });
    }

    private WorkflowParams parseArgs(List<String> args) {
        WorkflowParams params = new WorkflowParams();
        if (args == null) return params;

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
            }
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
                .addField("Usage", "`_comfy [options]`", false)
                .addField("Options", String.join("\n",
                        "`seed:<number>` - Set seed (-1 for random, default: random)",
                        "`steps:<number>` - Set sampling steps (default: 25)",
                        "`cfg:<number>` - Set CFG scale (default: 6.5)",
                        "`mode:<1-4>` - Prompt mode:",
                        "  1 = Normal, 2 = Combo, 3 = Booru (default), 4 = Combo+Auto",
                        "`style:<rindex|desuka>` - Style preset (default: rindex)",
                        "`res:<preset>` - Resolution preset (default: landscape - 1344x768)",
                        "`char:<name>` - Set character (switches to manual mode)",
                        "`random` - Use random character from built-in list"
                ), false)
                .addField("Examples", String.join("\n",
                        "`_comfy` - Generate with defaults",
                        "`_comfy char:1girl,ganyu steps:30`",
                        "`_comfy random mode:1 cfg:7`",
                        "`_comfy seed:12345 style:desuka`"
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
        return "Control ComfyUI workflow from Discord. Usage: `_comfy [options]` - Run `_comfy help` for details.";
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
}
