package ua.nanit.limbo.connection;

import net.kyori.adventure.nbt.BinaryTagIo;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import ua.nanit.limbo.LimboConstants;
import ua.nanit.limbo.protocol.PacketSnapshot;
import ua.nanit.limbo.protocol.packets.configuration.PacketFinishConfiguration;
import ua.nanit.limbo.protocol.packets.configuration.PacketRegistryData;
import ua.nanit.limbo.protocol.packets.login.PacketLoginSuccess;
import ua.nanit.limbo.protocol.packets.play.*;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.data.Title;
import ua.nanit.limbo.util.NbtMessageUtil;
import ua.nanit.limbo.util.UuidUtil;
import ua.nanit.limbo.world.Dimension;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class PacketSnapshots {
    public static PacketSnapshot PACKET_LOGIN_SUCCESS;
    public static PacketSnapshot PACKET_JOIN_GAME;
    public static PacketSnapshot PACKET_SPAWN_POSITION;
    public static PacketSnapshot PACKET_PLUGIN_MESSAGE;
    public static PacketSnapshot PACKET_PLAYER_ABILITIES;
    public static PacketSnapshot PACKET_PLAYER_INFO;
    public static PacketSnapshot PACKET_DECLARE_COMMANDS;
    public static PacketSnapshot PACKET_JOIN_MESSAGE;
    public static PacketSnapshot PACKET_BOSS_BAR;
    public static PacketSnapshot PACKET_HEADER_AND_FOOTER;
    public static PacketSnapshot PACKET_PLAYER_POS_AND_LOOK_LEGACY;
    public static PacketSnapshot PACKET_PLAYER_POS_AND_LOOK;
    public static PacketSnapshot PACKET_TITLE_TITLE;
    public static PacketSnapshot PACKET_TITLE_SUBTITLE;
    public static PacketSnapshot PACKET_TITLE_TIMES;
    public static PacketSnapshot PACKET_TITLE_LEGACY_TITLE;
    public static PacketSnapshot PACKET_TITLE_LEGACY_SUBTITLE;
    public static PacketSnapshot PACKET_TITLE_LEGACY_TIMES;
    public static PacketSnapshot PACKET_REGISTRY_DATA;
    public static List<PacketSnapshot> PACKETS_REGISTRY_DATA;
    public static PacketSnapshot PACKET_FINISH_CONFIGURATION;
    public static List<PacketSnapshot> PACKETS_EMPTY_CHUNKS;
    public static PacketSnapshot PACKET_START_WAITING_CHUNKS;

    private PacketSnapshots() {}

    public static void initPackets(LimboServer server) {
        final String username = server.getConfig().getPingData().getVersion();
        final UUID uuid = UuidUtil.getOfflineModeUuid(username);

        // Login success
        PacketLoginSuccess loginSuccess = new PacketLoginSuccess();
        loginSuccess.setUsername(username);
        loginSuccess.setUuid(uuid);
        PACKET_LOGIN_SUCCESS = PacketSnapshot.of(loginSuccess);

        // Join game
        PacketJoinGame joinGame = new PacketJoinGame();
        String worldName = "minecraft:" + server.getConfig().getDimensionType().toLowerCase();
        joinGame.setEntityId(0);
        joinGame.setEnableRespawnScreen(true);
        joinGame.setFlat(false);
        joinGame.setGameMode(server.getConfig().getGameMode());
        joinGame.setDimensionRegistry(server.getDimensionRegistry());
        joinGame.setMaxPlayers(server.getConfig().getMaxPlayers());
        joinGame.setViewDistance(8);
        joinGame.setReducedDebugInfo(true);
        joinGame.setDebug(false);
        joinGame.setPortalCooldown(300);
        PACKET_JOIN_GAME = PacketSnapshot.of(joinGame);

        // Player abilities
        PacketPlayerAbilities playerAbilities = new PacketPlayerAbilities();
        playerAbilities.setFlyingSpeed(0.0F);
        playerAbilities.setFlags(0x02);
        playerAbilities.setFieldOfView(0.1F);
        PACKET_PLAYER_ABILITIES = PacketSnapshot.of(playerAbilities);

        // Position and look
        int teleportId = ThreadLocalRandom.current().nextInt();
        PACKET_PLAYER_POS_AND_LOOK = PacketSnapshot.of(
            new PacketPlayerPositionAndLook(0, 400, 0, 0, 0, teleportId));
        PACKET_PLAYER_POS_AND_LOOK_LEGACY = PacketSnapshot.of(
            new PacketPlayerPositionAndLook(0, 64, 0, 0, 0, teleportId));
        PACKET_SPAWN_POSITION = PacketSnapshot.of(new PacketSpawnPosition(0, 400, 0));

        // Plugin message (brand channel)
        if (server.getConfig().isUseBrandName()) {
            PacketPluginMessage pluginMsg = new PacketPluginMessage();
            pluginMsg.setChannel(LimboConstants.BRAND_CHANNEL);
            pluginMsg.setData(server.getConfig().getBrandName().getBytes());
            PACKET_PLUGIN_MESSAGE = PacketSnapshot.of(pluginMsg);
        }

        // Player info
        if (server.getConfig().isUsePlayerList()) {
            PacketPlayerInfo playerInfo = new PacketPlayerInfo();
            playerInfo.setUsername(server.getConfig().getPlayerListUsername());
            playerInfo.setUuid(uuid);
            playerInfo.setGameMode(server.getConfig().getGameMode());
            PACKET_PLAYER_INFO = PacketSnapshot.of(playerInfo);
        }

        // Declare commands (empty)
        PACKET_DECLARE_COMMANDS = PacketSnapshot.of(new PacketDeclareCommands());

        // Join message
        if (server.getConfig().isUseJoinMessage()) {
            PacketChatMessage joinMessage = new PacketChatMessage();
            joinMessage.setMessage(server.getConfig().getJoinMessage());
            joinMessage.setPosition(PacketChatMessage.PositionLegacy.SYSTEM_MESSAGE);
            PACKET_JOIN_MESSAGE = PacketSnapshot.of(joinMessage);
        }

        // Boss bar
        if (server.getConfig().isUseBossBar()) {
            PacketBossBar bossBar = new PacketBossBar();
            bossBar.setBossBar(server.getConfig().getBossBar());
            bossBar.setUuid(UUID.randomUUID());
            PACKET_BOSS_BAR = PacketSnapshot.of(bossBar);
        }

        // Header and footer
        if (server.getConfig().isUseHeaderAndFooter()) {
            PacketPlayerListHeader headerFooter = new PacketPlayerListHeader();
            headerFooter.setHeader(server.getConfig().getPlayerListHeader());
            headerFooter.setFooter(server.getConfig().getPlayerListFooter());
            PACKET_HEADER_AND_FOOTER = PacketSnapshot.of(headerFooter);
        }

        // Titles
        if (server.getConfig().isUseTitle()) {
            Title title = server.getConfig().getTitle();
            PacketTitleSetTitle pktTitle = new PacketTitleSetTitle();
            pktTitle.setTitle(title.getTitle());
            PACKET_TITLE_TITLE = PacketSnapshot.of(pktTitle);

            PacketTitleSetSubTitle pktSubtitle = new PacketTitleSetSubTitle();
            pktSubtitle.setSubtitle(title.getSubtitle());
            PACKET_TITLE_SUBTITLE = PacketSnapshot.of(pktSubtitle);

            PacketTitleTimes pktTimes = new PacketTitleTimes();
            pktTimes.setFadeIn(title.getFadeIn());
            pktTimes.setStay(title.getStay());
            pktTimes.setFadeOut(title.getFadeOut());
            PACKET_TITLE_TIMES = PacketSnapshot.of(pktTimes);

            // Legacy titles (1.8-1.16)
            PacketTitleLegacy legacyTitle = new PacketTitleLegacy(title);
            legacyTitle.setAction(PacketTitleLegacy.Action.SET_TITLE);
            PacketTitleLegacy legacySubtitle = new PacketTitleLegacy(title);
            legacySubtitle.setAction(PacketTitleLegacy.Action.SET_SUBTITLE);
            PacketTitleLegacy legacyTimes = new PacketTitleLegacy(title);
            legacyTimes.setAction(PacketTitleLegacy.Action.SET_TIMES_AND_DISPLAY);

            PACKET_TITLE_LEGACY_TITLE = PacketSnapshot.of(legacyTitle);
            PACKET_TITLE_LEGACY_SUBTITLE = PacketSnapshot.of(legacySubtitle);
            PACKET_TITLE_LEGACY_TIMES = PacketSnapshot.of(legacyTimes);
        }

        // Registry data
        PacketRegistryData packetRegistryData = new PacketRegistryData();
        packetRegistryData.setDimensionRegistry(server.getDimensionRegistry());
        PACKET_REGISTRY_DATA = PacketSnapshot.of(packetRegistryData);

        // Finish configuration
        PACKET_FINISH_CONFIGURATION = PacketSnapshot.of(new PacketFinishConfiguration());

        // Empty chunks (3x3 grid)
        List<PacketSnapshot> emptyChunks = new ArrayList<>();
        int chunkEdgeSize = 1;
        for (int cx = -chunkEdgeSize; cx <= chunkEdgeSize; ++cx) {
            for (int cz = -chunkEdgeSize; cz <= chunkEdgeSize; ++cz) {
                PacketEmptyChunk packetEmptyChunk = new PacketEmptyChunk();
                packetEmptyChunk.setX(cx);
                packetEmptyChunk.setZ(cz);
                emptyChunks.add(PacketSnapshot.of(packetEmptyChunk));
            }
        }
        PACKETS_EMPTY_CHUNKS = emptyChunks;

        // Waiting for chunks event
        PacketGameEvent packetGameEvent = new PacketGameEvent();
        packetGameEvent.setType((byte) 13);
        packetGameEvent.setValue(0);
        PACKET_START_WAITING_CHUNKS = PacketSnapshot.of(packetGameEvent);
    }
}
