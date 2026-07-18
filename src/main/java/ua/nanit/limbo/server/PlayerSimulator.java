package ua.nanit.limbo.server;

import ua.nanit.limbo.util.Colors;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class PlayerSimulator {
    private static final String[] FAKE_NAMES = {
        "Steve", "Alex", "Notch", "jeb_", "Dinnerbone",
        "Grumm", "Searge", "C418", "Technoblade", "Dream",
        "GeorgeNotFound", "Sapnap", "TommyInnit", "Tubbo", "Ranboo",
        "WilburSoot", "Philza", "Niki", "JackManifold", "Fundy",
        "Eret", "Punz", "Purpled", "KarlJacobs", "Quackity",
        "Skeppy", "BadBoyHalo", "Antfrost", "awesamdude", "ConnorEatsPants",
        "Hbomb94", "vGumiho", "Foolish", "CaptainPuffy", "xNestorio",
        "Fruitberries", "Illumina", "BdoubleO100", "Keralis", "Xisuma",
        "MumboJumbo", "Grian", "Iskall85", "StressMonster", "Welsknight",
        "TangoTek", "impulseSV", "Zedaph", "Docm77", "FalseSymmetry"
    };

    private static final int MIN_PLAYERS = 3;
    private static final int MAX_PLAYERS = 20;

    private int currentOnline;
    private final List<Map.Entry<UUID, String>> activePlayers = new ArrayList<>();
    private final ScheduledExecutorService scheduler;
    private final AtomicInteger tickCounter = new AtomicInteger(0);

    public PlayerSimulator() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "player-sim");
            t.setDaemon(true);
            return t;
        });
        this.currentOnline = (MIN_PLAYERS + MAX_PLAYERS) / 2;
        rebuildActivePlayers();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 0, 5, TimeUnit.MINUTES);
        Log.info("[simulator] Player count simulation started (%d-%d players)", MIN_PLAYERS, MAX_PLAYERS);
    }

    private void tick() {
        int change = ThreadLocalRandom.current().nextInt(5) - 2;
        int newVal = currentOnline + change;
        newVal = Math.max(MIN_PLAYERS, Math.min(MAX_PLAYERS, newVal));
        currentOnline = newVal;
        rebuildActivePlayers();
        int tickNum = tickCounter.incrementAndGet();
        Log.debug("[simulator] Tick #%d: Online players adjusted to %d", tickNum, currentOnline);
    }

    private void rebuildActivePlayers() {
        activePlayers.clear();
        List<String> pool = new ArrayList<>(Arrays.asList(FAKE_NAMES));
        Collections.shuffle(pool, ThreadLocalRandom.current());
        int count = Math.min(currentOnline, pool.size());
        for (int i = 0; i < count; i++) {
            String name = pool.get(i);
            activePlayers.add(new AbstractMap.SimpleEntry<>(UUID.randomUUID(), name));
        }
    }

    public int getOnline() { return currentOnline; }

    public String getSampleJson() {
        if (activePlayers.isEmpty()) return "[]";
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < activePlayers.size(); i++) {
            Map.Entry<UUID, String> p = activePlayers.get(i);
            if (i > 0) sb.append(",");
            sb.append("{\"name\":\"").append(escapeJson(p.getValue()))
              .append("\",\"id\":\"").append(p.getKey()).append("\"}");
        }
        sb.append("]");
        return sb.toString();
    }

    public List<Map.Entry<UUID, String>> getActivePlayers() {
        return Collections.unmodifiableList(activePlayers);
    }

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void shutdown() { scheduler.shutdownNow(); }
}
