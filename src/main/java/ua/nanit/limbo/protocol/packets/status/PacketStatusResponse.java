package ua.nanit.limbo.protocol.packets.status;

import com.grack.nanojson.JsonObject;
import com.grack.nanojson.JsonWriter;
import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.PlayerSimulator;

public class PacketStatusResponse implements PacketOut {
    private final LimboServer server;

    public PacketStatusResponse(LimboServer server) { this.server = server; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        JsonObject json = new JsonObject();
        JsonObject versionObj = new JsonObject();
        versionObj.set("name", server.getConfig().getPingData().getVersion());
        versionObj.set("protocol", server.getConfig().getPingData().getProtocol());
        json.set("version", versionObj);

        JsonObject playersObj = new JsonObject();
        int online = server.getConnections().getCount();
        if (online == 0) {
            PlayerSimulator sim = server.getPlayerSimulator();
            if (sim != null) online = sim.getOnline();
        }
        playersObj.set("max", server.getConfig().getMaxPlayers());
        playersObj.set("online", online);
        playersObj.set("sample", server.getPlayerSimulator().getSampleJson().isEmpty() ? "[]" : server.getPlayerSimulator().getSampleJson());
        json.set("players", playersObj);
        json.set("description", server.getConfig().getPingData().getDescription());

        String jsonStr = JsonWriter.string().writer(json).toString();
        msg.writeString(jsonStr);
    }
}
