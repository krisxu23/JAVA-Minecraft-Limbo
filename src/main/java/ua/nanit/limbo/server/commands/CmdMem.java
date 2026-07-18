package ua.nanit.limbo.server.commands;

import ua.nanit.limbo.server.Command;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;

public class CmdMem implements Command {
    @Override
    public void execute(LimboServer server, String[] args) {
        Runtime rt = Runtime.getRuntime();
        long used = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
        long total = rt.totalMemory() / 1024 / 1024;
        long max = rt.maxMemory() / 1024 / 1024;
        Log.info("Memory: %dMB / %dMB (max: %dMB)", used, total, max);
    }
    @Override
    public String getName() { return "mem"; }
    @Override
    public String getPermission() { return ""; }
    @Override
    public String getDescription() { return "Show memory usage"; }
}
