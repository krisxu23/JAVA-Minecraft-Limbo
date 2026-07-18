package ua.nanit.limbo.protocol.packets.play;

import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.world.DimensionRegistry;

public class PacketJoinGame implements PacketOut {
    private int entityId;
    private int gameMode;
    private String worldName;
    private long hashedSeed;
    private int maxPlayers;
    private int viewDistance;
    private int simulationDistance;
    private boolean reducedDebugInfo;
    private boolean enableRespawnScreen;
    private boolean isDebug;
    private boolean isFlat;
    private byte[] registryData;
    private int portalCooldown;

    public void setEntityId(int entityId) { this.entityId = entityId; }
    public void setGameMode(int gameMode) { this.gameMode = gameMode; }
    public void setWorldName(String worldName) { this.worldName = worldName; }
    public void setHashedSeed(long hashedSeed) { this.hashedSeed = hashedSeed; }
    public void setMaxPlayers(int maxPlayers) { this.maxPlayers = maxPlayers; }
    public void setViewDistance(int viewDistance) { this.viewDistance = viewDistance; }
    public void setReducedDebugInfo(boolean reducedDebugInfo) { this.reducedDebugInfo = reducedDebugInfo; }
    public void setEnableRespawnScreen(boolean enableRespawnScreen) { this.enableRespawnScreen = enableRespawnScreen; }
    public void setDebug(boolean debug) { this.isDebug = debug; }
    public void setFlat(boolean flat) { this.isFlat = flat; }
    public void setDimensionRegistry(DimensionRegistry registry) {
        this.registryData = registry.getRegistryData();
        this.simulationDistance = viewDistance;
    }
    public void setPortalCooldown(int portalCooldown) { this.portalCooldown = portalCooldown; }

    @Override
    public void encode(ByteMessage msg, Version version) {
        msg.writeInt(entityId);
        msg.writeByte(gameMode);

        if (version.moreOrEqual(Version.V1_20_2)) {
            // 1.20.2+ uses named registry
            msg.writeLong(hashedSeed);
            msg.writeVarInt(maxPlayers);
            msg.writeVarInt(viewDistance);
            msg.writeVarInt(simulationDistance);
            msg.writeBoolean(reducedDebugInfo);
            msg.writeBoolean(isDebug);
            msg.writeBoolean(isFlat);

            // Write named dimension registry
            if (registryData != null) {
                msg.writeString(worldName);
                msg.writeVarInt(registryData.length);
                msg.writeBytes(registryData);
            }

            msg.writeVarInt(0); // allowed behaviors
            msg.writeBoolean(enableRespawnScreen);
            msg.writeVarInt(portalCooldown);
        } else if (version.moreOrEqual(Version.V1_19)) {
            msg.writeLong(hashedSeed);
            msg.writeVarInt(maxPlayers);
            msg.writeByte(gameMode);
            msg.writeByte(-1); // previous game mode
            msg.writeString(worldName);
            msg.writeByte(version.getMcVersion()); // dimension type id
            msg.writeLong(0);
            msg.writeBoolean(reducedDebugInfo);
            msg.writeBoolean(enableRespawnScreen);
            msg.writeBoolean(isFlat);
            msg.writeBoolean(false); // debug
            msg.writeVarInt(portalCooldown);
        } else if (version.moreOrEqual(Version.V1_16)) {
            msg.writeLong(hashedSeed);
            msg.writeVarInt(maxPlayers);
            msg.writeByte(gameMode);
            msg.writeByte(-1);
            msg.writeString(worldName);
            msg.writeByte(version.getMcVersion());
            msg.writeLong(0);
            msg.writeBoolean(reducedDebugInfo);
            msg.writeBoolean(enableRespawnScreen);
            msg.writeBoolean(isFlat);
            msg.writeBoolean(false);
        } else {
            msg.writeLong(hashedSeed);
            msg.writeVarInt(maxPlayers);
            msg.writeByte(gameMode);
            msg.writeByte(-1);
            msg.writeString(worldName);
            msg.writeByte(version.getMcVersion());
            msg.writeLong(0);
            msg.writeBoolean(reducedDebugInfo);
            msg.writeBoolean(enableRespawnScreen);
            msg.writeBoolean(isFlat);
        }

        msg.writeString("overworld");
        msg.writeString(worldName);
        msg.writeLong(0);
        msg.writeBoolean(false);
        msg.writeBoolean(false);
        msg.writeBoolean(false);
        msg.writeBoolean(false);
    }
}
