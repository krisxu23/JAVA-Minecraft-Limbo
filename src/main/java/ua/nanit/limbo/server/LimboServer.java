package ua.nanit.limbo.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.ServerChannel;
import io.netty.channel.epoll.Epoll;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.util.ResourceLeakDetector;
import ua.nanit.limbo.configuration.LimboConfig;
import ua.nanit.limbo.connection.ClientChannelInitializer;
import ua.nanit.limbo.connection.PacketHandler;
import ua.nanit.limbo.connection.PacketSnapshots;
import ua.nanit.limbo.protocol.packets.play.PacketKeepAlive;
import ua.nanit.limbo.world.DimensionRegistry;

import java.nio.file.Paths;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public final class LimboServer {
    private LimboConfig config;
    private PacketHandler packetHandler;
    private Connections connections;
    private DimensionRegistry dimensionRegistry;
    private ScheduledFuture<?> keepAliveTask;
    private ScheduledFuture<?> motdRotatorTask;
    private ScheduledFuture<?> playerSimTask;

    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private CommandManager commandManager;
    private PlayerSimulator playerSimulator;

    private static final String[] MOTD_POOL_RAW = {
        "{\"text\": \"&aSurvival &7| &bSkyBlock &7| &eBedWars\"}",
        "{\"text\": \"&7Welcome to &aMyNetwork\"}",
        "{\"text\": \"&e&lMyServer &7| &a1.21.x &7| &dJoin Now!\"}",
        "{\"text\": \"&6play.mynet.org &7| &a1.21.4\"}",
        "{\"text\": \"&b&m------&r &aFun &b&m------\"}",
        "{\"text\": \"&a&lNetwork &7| &eSurvival &7| &6Creative\"}",
        "{\"text\": \"&d&lDiamond &a&lNetwork &7| &bplay.example.net\"}",
        "{\"text\": \"&7[&a1.21.x&7] &eSurvival &7| &bSkyBlock\"}",
    };
    private static final String[] MOTD_POOL;
    private static final int[] MAX_PLAYERS_OPTIONS = {50, 80, 100, 120, 150, 200, 250, 300, 500};

    static {
        MOTD_POOL = new String[MOTD_POOL_RAW.length];
        for (int i = 0; i < MOTD_POOL_RAW.length; i++) {
            MOTD_POOL[i] = ua.nanit.limbo.util.Colors.of(MOTD_POOL_RAW[i]);
        }
    }

    public LimboConfig getConfig() { return config; }
    public PacketHandler getPacketHandler() { return packetHandler; }
    public Connections getConnections() { return connections; }
    public DimensionRegistry getDimensionRegistry() { return dimensionRegistry; }
    public CommandManager getCommandManager() { return commandManager; }
    public PlayerSimulator getPlayerSimulator() { return playerSimulator; }

    public void start() throws Exception {
        long startTime = System.currentTimeMillis();
        config = new LimboConfig(Paths.get("./"));
        config.load();

        Log.setLevel(config.getDebugLevel());
        Log.info("Starting server...");
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);

        packetHandler = new PacketHandler(this);
        dimensionRegistry = new DimensionRegistry(this);
        dimensionRegistry.load(config.getDimensionType());
        connections = new Connections();
        PacketSnapshots.initPackets(this);

        startBootstrap();
        simulateStartupProgress();

        keepAliveTask = workerGroup.scheduleAtFixedRate(this::broadcastKeepAlive, 0L, 5L, TimeUnit.SECONDS);

        playerSimulator = new PlayerSimulator();
        playerSimulator.start();
        playerSimTask = workerGroup.scheduleAtFixedRate(() -> {
            try { startMotdCamouflage(); } catch (Exception e) {
                Log.debug("MOTD rotation skipped: %s", e.getMessage());
            }
        }, 120L, 180L, TimeUnit.SECONDS);

        commandManager = new CommandManager(this);
        commandManager.start();
    }

    private void simulateStartupProgress() {
        long startTime = System.currentTimeMillis();
        Log.info("Preparing level \"world\"");
        Log.info("Preparing start region for dimension minecraft:overworld");

        String[] progressMessages = {
            "Preparing spawn area: 1%", "Preparing spawn area: 2%",
            "Preparing spawn area: 5%", "Preparing spawn area: 8%",
            "Preparing spawn area: 15%", "Preparing spawn area: 20%",
            "Preparing spawn area: 35%", "Preparing spawn area: 60%",
            "Preparing spawn area: 80%", "Preparing spawn area: 99%",
            "Preparing spawn area: 100%", "Running delayed init tasks"
        };
        for (int i = 0; i < progressMessages.length; i++) {
            int delay = 200 + (i * 300);
            String msg = progressMessages[i];
            workerGroup.schedule(() -> Log.info(msg), delay, TimeUnit.MILLISECONDS);
        }
        int finalDelay = 200 + progressMessages.length * 300;
        workerGroup.schedule(() -> {
            double elapsed = (System.currentTimeMillis() - startTime) / 1000.0;
            Log.info(String.format("Done (%.3fs)! For help, type \"help\"", elapsed));
        }, finalDelay, TimeUnit.MILLISECONDS);
    }

    private void startBootstrap() {
        Class<? extends ServerChannel> channelClass;
        if (config.isUseEpoll() && Epoll.isAvailable()) {
            bossGroup = new EpollEventLoopGroup(config.getBossGroupSize());
            workerGroup = new EpollEventLoopGroup(config.getWorkerGroupSize());
            channelClass = EpollServerSocketChannel.class;
            Log.debug("Using Epoll transport type");
        } else {
            bossGroup = new NioEventLoopGroup(config.getBossGroupSize());
            workerGroup = new NioEventLoopGroup(config.getWorkerGroupSize());
            channelClass = NioServerSocketChannel.class;
            Log.debug("Using Java NIO transport type");
        }

        new ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(channelClass)
            .childHandler(new ClientChannelInitializer(this))
            .childOption(ChannelOption.TCP_NODELAY, true)
            .localAddress(config.getAddress())
            .bind();
    }

    private void broadcastKeepAlive() {
        connections.getAllConnections().forEach(c -> {
            if (c.isConnected()) {
                PacketKeepAlive ka = new PacketKeepAlive();
                ka.setId(ThreadLocalRandom.current().nextLong());
                c.sendPacket(ka);
            }
        });
    }

    private void startMotdCamouflage() {
        int randomMax = MAX_PLAYERS_OPTIONS[ThreadLocalRandom.current().nextInt(MAX_PLAYERS_OPTIONS.length)];
        config.setMaxPlayers(randomMax);
        rotateMotd();
    }

    private void rotateMotd() {
        String motd = MOTD_POOL[ThreadLocalRandom.current().nextInt(MOTD_POOL.length)];
        config.getPingData().setDescription(motd);
    }

    public void stop() {
        Log.info("Stopping server...");
        if (keepAliveTask != null) keepAliveTask.cancel(true);
        if (motdRotatorTask != null) motdRotatorTask.cancel(true);
        if (playerSimTask != null) playerSimTask.cancel(true);
        if (playerSimulator != null) playerSimulator.shutdown();
        if (bossGroup != null) bossGroup.shutdownGracefully();
        if (workerGroup != null) workerGroup.shutdownGracefully();
        Log.info("Server stopped, Goodbye!");
    }
}
