package ua.nanit.limbo.server;

import ua.nanit.limbo.server.commands.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

public class CommandManager {
    private final LimboServer server;
    private final Map<String, Command> commands = new HashMap<>();
    private Thread commandThread;

    public CommandManager(LimboServer server) {
        this.server = server;
        register(new CmdHelp());
        register(new CmdConn());
        register(new CmdMem());
        register(new CmdStop());
        register(new CmdVersion());
    }

    private void register(Command cmd) {
        commands.put(cmd.getName().toLowerCase(), cmd);
    }

    public void start() {
        commandThread = new Thread(this::readCommands, "command-reader");
        commandThread.setDaemon(true);
        commandThread.start();
    }

    private void readCommands() {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(System.in))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) continue;
                StringTokenizer st = new StringTokenizer(line);
                String cmdName = st.nextToken().toLowerCase();
                Command cmd = commands.get(cmdName);
                if (cmd != null) {
                    String[] args = new String[st.countTokens()];
                    int i = 0;
                    while (st.hasMoreTokens()) args[i++] = st.nextToken();
                    try { cmd.execute(server, args); }
                    catch (Exception e) { Log.error("Error executing command: ", e); }
                } else {
                    Log.warn("Unknown command: %s (type help for list)", cmdName);
                }
            }
        } catch (Exception e) {
            Log.error("Command reader error: ", e);
        }
    }
}
