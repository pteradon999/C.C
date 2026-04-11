package cc.gen.second.command.commands;

import cc.gen.second.command.CommandContext;
import cc.gen.second.command.ICommand;
import cc.gen.second.utils.UserListStore;
import net.dv8tion.jda.api.interactions.commands.build.CommandData;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class PickUserCommand implements ICommand {

    @Override
    public void handle(CommandContext ctx) {
        List<String> args = ctx.isSlash() ? null : ctx.getArgs();

        if (args != null && !args.isEmpty()) {
            String sub = args.get(0).toLowerCase();

            if (sub.equals("add") && args.size() >= 2) {
                String name = String.join(" ", args.subList(1, args.size())).trim();
                if (name.isEmpty()) {
                    reply(ctx, "Укажи имя: `C.C_pickuser add <имя>`");
                    return;
                }
                boolean added = UserListStore.add(name);
                reply(ctx, added
                        ? "✅ Добавлен: **" + name + "**"
                        : "⚠️ **" + name + "** уже в списке");
                return;
            }

            if (sub.equals("remove") && args.size() >= 2) {
                String name = String.join(" ", args.subList(1, args.size())).trim();
                boolean removed = UserListStore.remove(name);
                reply(ctx, removed
                        ? "🗑 Удалён: **" + name + "**"
                        : "⚠️ **" + name + "** не найден в списке");
                return;
            }

            if (sub.equals("list")) {
                List<String> all = UserListStore.getAll();
                if (all.isEmpty()) {
                    reply(ctx, "Список пуст.");
                } else {
                    reply(ctx, "Список (" + all.size() + "): " + String.join(", ", all));
                }
                return;
            }
        }

        // Default: random pick
        List<String> users = UserListStore.getAll();
        if (users.isEmpty()) {
            reply(ctx, "Список пуст. Добавь кого-нибудь: `C.C_pickuser add <имя>`");
            return;
        }
        String picked = users.get(ThreadLocalRandom.current().nextInt(users.size()));
        reply(ctx, "🎲 " + picked);
    }

    private void reply(CommandContext ctx, String text) {
        if (ctx.isSlash()) {
            ctx.getSlashEvent().reply(text).queue();
        } else {
            ctx.getChannel().sendMessage(text).queue();
        }
    }

    @Override
    public CommandData getCommandData() {
        return null;
    }

    @Override
    public String getName() {
        return "_pickuser";
    }

    @Override
    public List<String> getAliases() {
        return List.of("pickuser", "_randuser");
    }

    @Override
    public String getDescription() {
        return "Pick a random user from the list";
    }

    @Override
    public String getHelp() {
        return "Usage:\n" +
                "`C.C_pickuser` — случайный юзер из списка\n" +
                "`C.C_pickuser add <имя>` — добавить юзера\n" +
                "`C.C_pickuser remove <имя>` — удалить юзера\n" +
                "`C.C_pickuser list` — показать весь список";
    }
}
