package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;
import net.dv8tion.jda.api.utils.FileUpload;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.sql.SQLException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;

public class FolderWatchCommand implements ICommand {
    private static final Set<TextChannel> activeChannels = ConcurrentHashMap.newKeySet();
    private static final Map<String, String> lastProcessed = new ConcurrentHashMap<>();
    private static ScheduledExecutorService watcher;

    private static final String PNG = ".png";
    private static final String MP4 = ".mp4";
    private static final String OWNER_ID = "253963350944251915";

    private static final String PNG_FOLDER = System.getenv("IMAGE_FOLDER_PATH") != null
            ? System.getenv("IMAGE_FOLDER_PATH")
            : "D:\\SD_output";
    private static final String MP4_FOLDER = System.getenv("VIDEO_FOLDER_PATH") != null
            ? System.getenv("VIDEO_FOLDER_PATH")
            : "D:\\SD_Main\\ComfyVideo\\ComfyUI_windows_portable\\ComfyUI\\output";

    @Override
    public void handle(@NotNull CommandContext ctx) throws SQLException {
        // owner check (compare ID strings)
        if (!ctx.getAuthor().getId().equals(OWNER_ID)) {
            ctx.getChannel().sendMessage("You are not authorized to activate this command.").queue();
            return;
        }

        TextChannel channel = (TextChannel) ctx.getChannel();
        activeChannels.add(channel);
        channel.sendMessage("Folder monitoring for images and videos is activated for this channel.").queue();

        if (watcher == null || watcher.isShutdown()) {
            watcher = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "FolderWatcher");
                t.setDaemon(true);
                return t;
            });
            watcher.scheduleAtFixedRate(this::monitorFolders, 0, 1, TimeUnit.MINUTES);
        }
    }

    @Override
    public CommandData getCommandData() {
        return null;
    }

    private void monitorFolders() {
        monitorAndSend(PNG_FOLDER, PNG, "New image detected!");
        monitorAndSend(MP4_FOLDER, MP4, "New video detected!");
    }

    private void monitorAndSend(String folderPath, String ext, String message) {
        try {
            File folder = new File(folderPath);
            if (!folder.isDirectory()) return;

            File[] files = folder.listFiles();
            if (files == null || files.length == 0) return;

            File latest = null;
            for (File f : files) {
                if (f.isFile() && f.getName().toLowerCase().endsWith(ext)) {
                    if (latest == null || f.lastModified() > latest.lastModified()) {
                        latest = f;
                    }
                }
            }
            if (latest == null) return;

            String key = folderPath + "|" + ext;
            String last = lastProcessed.get(key);
            if (latest.getName().equals(last)) return;

            lastProcessed.put(key, latest.getName());

            // FileUpload is single-use: create a new one per send
            for (TextChannel ch : activeChannels) {
                ch.sendMessage(message)
                        .addFiles(FileUpload.fromData(latest, latest.getName()))
                        .queue();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public String getName() {
        return "_aigen";
    }

    @Override
    public String getHelp() {
        return "Monitors specified folders for new PNG images and MP4 videos. Usage: `_aigen`";
    }
}
