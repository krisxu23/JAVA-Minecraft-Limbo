package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.util.UuidUtil;

import java.util.UUID;

public class PacketPlayerInfo implements PacketOut {
    private int gameMode = 3;
    private String username = "";
    private UUID uuid;

    public void setGameMode(int gameMode) { this.gameMode = gameMode; }
    public void setUsername(String username) { this.username = username; }
    public void setUuid(UUID uuid) { this.uuid = uuid; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        if (version.moreOrEqual(Version.V1_19_3)) {
            java.util.EnumSet<Action> actions = java.util.EnumSet.noneOf(Action.class);
            actions.add(Action.ADD_PLAYER);
            actions.add(Action.UPDATE_LISTED);
            actions.add(Action.UPDATE_GAMEMODE);
            msg.writeEnumSet(actions, Action.class);
            msg.writeVarInt(1);
            msg.writeUuid(uuid);
            msg.writeString(username);
            msg.writeVarInt(0);
            msg.writeBoolean(true);
            msg.writeVarInt(gameMode);
        } else if (version.moreOrEqual(Version.V1_19)) {
            msg.writeVarInt(0);
            msg.writeVarInt(1);
            msg.writeUuid(uuid);
            msg.writeString(username);
            msg.writeVarInt(0);
            msg.writeVarInt(gameMode);
            msg.writeVarInt(60);
            msg.writeBoolean(false);
            msg.writeBoolean(false);
        } else if (version.moreOrEqual(Version.V1_8)) {
            msg.writeVarInt(0);
            msg.writeVarInt(1);
            msg.writeUuid(uuid);
            msg.writeString(username);
            msg.writeVarInt(0);
            msg.writeVarInt(gameMode);
            msg.writeVarInt(60);
        } else {
            msg.writeString(username);
            msg.writeBoolean(true);
            msg.writeShort(0);
        }
    }

    public enum Action { ADD_PLAYER, UPDATE_LISTED, UPDATE_GAMEMODE }
}
