package ua.nanit.limbo.server.commands;

import ua.nanit.limbo.server.Command;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

public class CmdStop implements Command {
    @Override
    public void execute(LimboServer server, String[] args) {
        Log.info("Stopping server...");
        server.stop();
        System.exit(0);
    }
    @Override
    public String getName() { return "stop"; }
    @Override
    public String getPermission() { return ""; }
    @Override
    public String getDescription() { return "Stop the server"; }
}
