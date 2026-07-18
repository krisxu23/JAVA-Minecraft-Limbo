package ua.nanit.limbo.server.commands;

import ua.nanit.limbo.server.Command;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

import java.util.*;
import java.util.stream.Collectors;

public class CmdHelp implements Command {

    private final LimboServer server;

    public CmdHelp(LimboServer server) {
        this.server = server;
    }

    @Override
    public void execute() {
        Map<String, Command> commands = server.getCommandManager().getCommands();

        Log.info("Available commands:");

        // Group aliases per command to avoid duplicate lines
        Map<Command, List<String>> grouped = new LinkedHashMap<>();
        for (Map.Entry<String, Command> entry : commands.entrySet()) {
            grouped.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        for (Map.Entry<Command, List<String>> entry : grouped.entrySet()) {
            String aliases = entry.getValue().stream().sorted().collect(Collectors.joining(", "));
            Log.info("%s - %s", aliases, entry.getKey().description());
        }
    }

    @Override
    public String description() {
        return "Show this message";
    }
}
