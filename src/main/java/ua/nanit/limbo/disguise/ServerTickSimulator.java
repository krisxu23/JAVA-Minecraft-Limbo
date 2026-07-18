package ua.nanit.limbo.disguise;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import ua.nanit.limbo.connection.ClientConnection;
import ua.nanit.limbo.connection.Connections;
import ua.nanit.limbo.protocol.packets.play.PacketKeepAlive;
import ua.nanit.limbo.server.Log;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Sends realistic server tick packets to all connected players.
 * Ported from minewire/handler.go keepalive + time update + motion tickers.
 *
 * A real Minecraft server sends:
 * - Keep-Alive every ~15 seconds
 * - Time Update every ~1 second (20 ticks)
 * - Player position updates
 *
 * This makes the server appear authentic to DPI and monitoring tools.
 */
public final class ServerTickSimulator {

    private final Connections connections;
    private final PlayerMotion motion;
    private ScheduledExecutorService scheduler;
    private long worldTime = 0;

    public ServerTickSimulator(Connections connections) {
        this.connections = connections;
        this.motion = new PlayerMotion();
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ServerTickSim");
            t.setDaemon(true);
            return t;
        });

        // Keep-Alive every 15 seconds (vanilla behavior)
        scheduler.scheduleAtFixedRate(this::sendKeepAlive, 15, 15, TimeUnit.SECONDS);

        // Motion update every 2 seconds (independent of time)
        scheduler.scheduleAtFixedRate(this::updateMotion, 2, 2, TimeUnit.SECONDS);

        Log.info("[Disguise] Server tick simulator started (keepalive=15s, motion=2s)");
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public PlayerMotion getMotion() {
        return motion;
    }

    private void sendKeepAlive() {
        try {
            PacketKeepAlive pkt = new PacketKeepAlive();
            pkt.setId(ThreadLocalRandom.current().nextLong());
            for (ClientConnection conn : connections.getAllConnections()) {
                conn.sendPacket(pkt);
            }
        } catch (Exception e) {
            // Ignore
        }
    }

    private void updateMotion() {
        motion.update();
    }
}
