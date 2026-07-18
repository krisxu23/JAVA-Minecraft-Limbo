package ua.nanit.limbo.protocol.registry;

public enum State {
    STATUS(0), LOGIN(1), PLAY(2), CONFIGURATION(3);

    private final int id;
    State(int id) { this.id = id; }
    public int getId() { return id; }
}
