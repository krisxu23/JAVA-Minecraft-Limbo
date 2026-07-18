package ua.nanit.limbo.server;

public interface Command {
    void execute(LimboServer server, String[] args);
    String getName();
    String getPermission();
    String getDescription();
}
