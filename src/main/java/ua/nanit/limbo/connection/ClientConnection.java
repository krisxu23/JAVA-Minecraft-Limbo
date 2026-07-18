package ua.nanit.limbo.connection;

import com.grack.nanojson.JsonObject;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.jetbrains.annotations.NotNull;
import ua.nanit.limbo.LimboConstants;
import ua.nanit.limbo.protocol.ByteMessage;
import ua.nanit.limbo.protocol.PacketOut;
import ua.nanit.limbo.protocol.PacketSnapshot;
import ua.nanit.limbo.protocol.packets.login.PacketDisconnect;
import ua.nanit.limbo.protocol.packets.play.PacketKeepAlive;
import ua.nanit.limbo.protocol.registry.State;
import ua.nanit.limbo.protocol.registry.Version;
import ua.nanit.limbo.server.LimboServer;
import ua.nanit.limbo.server.Log;
import ua.nanit.limbo.util.UuidUtil;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.InvalidKeyException;
import java.security.MessageDigest;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class ClientConnection extends ChannelInboundHandlerAdapter {
    private final LimboServer server;
    private final Channel channel;
    private final GameProfile gameProfile;
    private final ua.nanit.limbo.connection.pipeline.PacketDecoder decoder;
    private final ua.nanit.limbo.connection.pipeline.PacketEncoder encoder;
    private State state;
    private Version clientVersion;
    private SocketAddress address;
    private int velocityLoginMessageId = -1;

    public ClientConnection(Channel channel, LimboServer server,
            ua.nanit.limbo.connection.pipeline.PacketDecoder decoder,
            ua.nanit.limbo.connection.pipeline.PacketEncoder encoder) {
        this.server = server;
        this.channel = channel;
        this.decoder = decoder;
        this.encoder = encoder;
        this.address = channel.remoteAddress();
        this.gameProfile = new GameProfile();
    }

    public UUID getUuid() { return gameProfile.getUuid(); }
    public String getUsername() { return gameProfile.getUsername(); }
    public SocketAddress getAddress() { return address; }
    public Version getClientVersion() { return clientVersion; }
    public GameProfile getGameProfile() { return gameProfile; }

    @Override
    public void channelInactive(@NotNull ChannelHandlerContext ctx) throws Exception {
        if (state == State.PLAY || state == State.CONFIGURATION) {
            server.getConnections().removeConnection(this);
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        if (channel.isActive()) Log.error("Unhandled exception: ", cause);
    }

    @Override
    public void channelRead(@NotNull ChannelHandlerContext ctx, @NotNull Object msg) {
        handlePacket(msg);
    }

    public void handlePacket(Object packet) {
        if (packet instanceof PacketIn pkt) {
            pkt.handle(this, server);
        }
    }

    public void fireLoginSuccess() {
        if (server.getConfig().getInfoForwarding().isModern() && velocityLoginMessageId == -1) {
            disconnectLogin("You need to connect with Velocity");
            return;
        }
        sendPacket(PacketSnapshots.PACKET_LOGIN_SUCCESS);
        server.getConnections().addConnection(this);

        if (clientVersion.moreOrEqual(Version.V1_20_2)) {
            updateEncoderState(State.CONFIGURATION);
            return;
        }
        spawnPlayer();
    }

    public void spawnPlayer() {
        updateState(State.PLAY);
        Runnable sendPlayPackets = () -> {
            writePacket(PacketSnapshots.PACKET_JOIN_GAME);
            writePacket(PacketSnapshots.PACKET_PLAYER_ABILITIES);

            if (clientVersion.less(Version.V1_9)) {
                writePacket(PacketSnapshots.PACKET_PLAYER_POS_AND_LOOK_LEGACY);
            } else {
                writePacket(PacketSnapshots.PACKET_PLAYER_POS_AND_LOOK);
            }

            if (clientVersion.moreOrEqual(Version.V1_19_3))
                writePacket(PacketSnapshots.PACKET_SPAWN_POSITION);

            if (server.getConfig().isUsePlayerList() || clientVersion.equals(Version.V1_16_4))
                writePacket(PacketSnapshots.PACKET_PLAYER_INFO);

            if (clientVersion.moreOrEqual(Version.V1_13)) {
                writePacket(PacketSnapshots.PACKET_PLUGIN_MESSAGE);
            }

            writePacket(PacketSnapshots.PACKET_DECLARE_COMMANDS);
            writePacket(PacketSnapshots.PACKET_START_WAITING_CHUNKS);

            for (PacketSnapshot chunk : PacketSnapshots.PACKETS_EMPTY_CHUNKS) {
                writePacket(chunk);
            }

            if (server.getConfig().isUseBossBar())
                writePacket(PacketSnapshots.PACKET_BOSS_BAR);

            if (server.getConfig().isUseHeaderAndFooter())
                writePacket(PacketSnapshots.PACKET_HEADER_AND_FOOTER);

            if (server.getConfig().isUseTitle()) {
                writePacket(PacketSnapshots.PACKET_TITLE_TITLE);
                writePacket(PacketSnapshots.PACKET_TITLE_SUBTITLE);
                writePacket(PacketSnapshots.PACKET_TITLE_TIMES);
            }

            if (server.getConfig().isUseJoinMessage()) {
                PacketChatMessage joinMessage = new PacketChatMessage();
                joinMessage.setMessage(server.getConfig().getJoinMessage());
                joinMessage.setPosition(PacketChatMessage.PositionLegacy.SYSTEM_MESSAGE);
                joinMessage.setSender(UUID.randomUUID());
                writePacket(PacketSnapshot.of(joinMessage));
            }

            channel.flush();
        };

        if (state == State.PLAY) {
            sendPlayPackets.run();
        } else {
            channel.eventLoop().execute(sendPlayPackets);
        }
    }

    public void onLoginAcknowledgedReceived() {
        updateState(State.CONFIGURATION);

        if (PacketSnapshots.PACKET_PLUGIN_MESSAGE != null)
            writePacket(PacketSnapshots.PACKET_PLUGIN_MESSAGE);

        if (clientVersion.moreOrEqual(Version.V1_20_5)) {
            for (PacketSnapshot packet : PacketSnapshots.PACKETS_REGISTRY_DATA) {
                writePacket(packet);
            }
        } else {
            writePacket(PacketSnapshots.PACKET_REGISTRY_DATA);
        }

        writePacket(PacketSnapshots.PACKET_FINISH_CONFIGURATION);
    }

    public void disconnectLogin(String reason) {
        if (isConnected() && state == State.LOGIN) {
            PacketDisconnect disconnect = new PacketDisconnect();
            disconnect.setReason(reason);
            sendPacketAndClose(disconnect);
        }
    }

    public void writeTitle() {
        if (clientVersion.moreOrEqual(Version.V1_17)) {
            writePacket(PacketSnapshots.PACKET_TITLE_TITLE);
            writePacket(PacketSnapshots.PACKET_TITLE_SUBTITLE);
            writePacket(PacketSnapshots.PACKET_TITLE_TIMES);
        } else {
            writePacket(PacketSnapshots.PACKET_TITLE_LEGACY_TITLE);
            writePacket(PacketSnapshots.PACKET_TITLE_LEGACY_SUBTITLE);
            writePacket(PacketSnapshots.PACKET_TITLE_LEGACY_TIMES);
        }
    }

    public void sendKeepAlive() {
        if (state == State.PLAY) {
            PacketKeepAlive keepAlive = new PacketKeepAlive();
            keepAlive.setId(ThreadLocalRandom.current().nextLong());
            sendPacket(keepAlive);
        }
    }

    public void sendPacket(Object packet) {
        if (isConnected()) channel.writeAndFlush(packet, channel.voidPromise());
    }

    public void sendPacketAndClose(Object packet) {
        if (isConnected()) channel.writeAndFlush(packet).addListener(ChannelFutureListener.CLOSE);
    }

    public void writePacket(Object packet) {
        if (isConnected()) channel.write(packet, channel.voidPromise());
    }

    public boolean isConnected() { return channel.isActive(); }

    public void updateState(State state) {
        this.state = state;
        decoder.updateState(state);
        encoder.updateState(state);
    }

    public void updateEncoderState(State state) { encoder.updateState(state); }

    public void updateVersion(Version version) {
        clientVersion = version;
        decoder.updateVersion(version);
        encoder.updateVersion(version);
    }

    public void setAddress(String host) {
        this.address = new InetSocketAddress(host, ((InetSocketAddress) this.address).getPort());
    }

    boolean checkBungeeGuardHandshake(String handshake) {
        String[] split = handshake.split("\00");
        if (split.length != 4) return false;

        String socketAddressHostname = split[1];
        UUID uuid = UuidUtil.fromString(split[2]);
        JsonObject arr;
        try {
            arr = com.grack.nanojson.JsonParser.parse(JsonObject.class, split[3]);
        } catch (Exception e) { return false; }

        String token = null;
        for (String key : arr.keySet()) {
            JsonObject prop = arr.getObject(key);
            if (prop != null && "bungeeguard-token".equals(prop.getString("name", ""))) {
                token = prop.getString("value");
                break;
            }
        }

        if (!server.getConfig().getInfoForwarding().hasToken(token)) return false;
        setAddress(socketAddressHostname);
        gameProfile.setUuid(uuid);
        Log.debug("Successfully verified BungeeGuard token");
        return true;
    }

    int getVelocityLoginMessageId() { return velocityLoginMessageId; }
    void setVelocityLoginMessageId(int velocityLoginMessageId) { this.velocityLoginMessageId = velocityLoginMessageId; }

    boolean checkVelocityKeyIntegrity(ByteMessage buf) {
        byte[] signature = new byte[32];
        buf.readBytes(signature);
        byte[] data = new byte[buf.readableBytes()];
        buf.getBytes(buf.readerIndex(), data);
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(server.getConfig().getInfoForwarding().getSecretKey(), "HmacSHA256"));
            byte[] mySignature = mac.doFinal(data);
            if (!MessageDigest.isEqual(signature, mySignature)) return false;
        } catch (InvalidKeyException | java.security.NoSuchAlgorithmException e) {
            throw new AssertionError(e);
        }
        int version = buf.readVarInt();
        if (version != 1)
            throw new IllegalStateException("Unsupported forwarding version " + version);
        return true;
    }
}
