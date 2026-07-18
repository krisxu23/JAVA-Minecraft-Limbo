package ua.nanit.limbo.server.commands;

import ua.nanit.limbo.server.Command;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

public class CmdHelp implements Command {
    @Override
    public void execute(LimboServer server, String[] args) {
        Log.info("Available commands: conn, mem, stop, version, help");
    }
    @Override
    public String getName() { return "help"; }
    @Override
    public String getPermission() { return ""; }
    @Override
    public String getDescription() { return "Show help"; }
}
