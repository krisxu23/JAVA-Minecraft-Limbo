package ua.nanit.limbo.server.commands;

import ua.nanit.limbo.server.Command;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

public class CmdConn implements Command {
    @Override
    public void execute(LimboServer server, String[] args) {
        Log.info("Connected players: %d", server.getConnections().getCount());
    }
    @Override
    public String getName() { return "conn"; }
    @Override
    public String getPermission() { return ""; }
    @Override
    public String getDescription() { return "Show connected players count"; }
}
