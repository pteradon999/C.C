package cc.gen.second.command;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.unions.MessageChannelUnion;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.List;

public class CommandContext {
    private final MessageReceivedEvent messageEvent;
    private final SlashCommandInteractionEvent slashEvent;
    private final List<String> args;

    // For prefix commands
    public CommandContext(MessageReceivedEvent messageEvent, List<String> args) {
        this.messageEvent = messageEvent;
        this.slashEvent = null;
        this.args = args;
    }

    // For slash commands
    public CommandContext(SlashCommandInteractionEvent slashEvent) {
        this.messageEvent = null;
        this.slashEvent = slashEvent;
        this.args = null;
    }

    public boolean isSlash() {
        return slashEvent != null;
    }

    public MessageReceivedEvent getMessageEvent() {
        return messageEvent;
    }

    public SlashCommandInteractionEvent getSlashEvent() {
        return slashEvent;
    }

    public List<String> getArgs() {
        return args;
    }

    /** Author of the message / interaction */
    public User getAuthor() {
        return isSlash() ? slashEvent.getUser() : messageEvent.getAuthor();
    }

    /** Channel the message / interaction came from */
    public MessageChannelUnion getChannel() {
        return isSlash() ? slashEvent.getChannel() : messageEvent.getChannel();
    }
}
