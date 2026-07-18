package ua.nanit.limbo.server.commands;

import ua.nanit.limbo.server.Command;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

public class CmdVersion implements Command {
    @Override
    public void execute(LimboServer server, String[] args) {
        Log.info("NanoLimbo Proxy Wrapper v1.0");
    }
    @Override
    public String getName() { return "version"; }
    @Override
    public String getPermission() { return ""; }
    @Override
    public String getDescription() { return "Show version"; }
}
