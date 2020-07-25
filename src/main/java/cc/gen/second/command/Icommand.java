package cc.gen.second.command;

import java.sql.SQLException;
import java.util.List;

public interface Icommand {
    String handle(CommandContext ctx) throws SQLException;

    String getName();

    default List<String> getAliases(){
        return List.of();
    }
}
