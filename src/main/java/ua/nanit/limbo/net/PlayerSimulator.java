package ua.nanit.limbo.net;

import ua.nanit.limbo.server.Log;

import java.util.*;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class PlayerSimulator {

    private static final String[] FAKE_NAMES = {
        "Steve", "Alex", "Notch", "Herobrine", "Dinnerbone",
        "jeb_", "Grumm", "Searge", "C418", "Technoblade",
        "Dream", "GeorgeNotFound", "Sapnap", "TommyInnit", "Tubbo",
        "Ranboo", "WilburSoot", "Philza", "Niki", "JackManifold",
        "Fundy", "Eret", "Punz", "Purpled", "KarlJacobs",
        "Quackity", "Skeppy", "BadBoyHalo", "Antfrost", "awesamdude",
        "ConnorEatsPants", "Hbomb94", "vGumiho", "Foolish", "CaptainPuffy",
        "xNestorio", "Fruitberries", "Illumina", "BdoubleO100", "Keralis1",
        "Xisuma", "MumboJumbo", "Grian", "Iskall85", "StressMonster",
        "Welsknight", "Tango", "ImpulseSV", "Zedaph", "Docm77"
    };

    private static final int MIN = 3;
    private static final int MAX = 20;

    private int currentOnline;
    private final List<Map.Entry<UUID, String>> activePlayers = new ArrayList<>();
    private final ScheduledExecutorService scheduler;
    private final Set<String> usedNames = new HashSet<>();

    public PlayerSimulator() {
        this.scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "player-sim");
            t.setDaemon(true);
            return t;
        });
        this.currentOnline = (MIN + MAX) / 2;
        rebuildActivePlayers();
    }

    public void start() {
        scheduler.scheduleAtFixedRate(this::tick, 20, 25, TimeUnit.MINUTES);
        Log.info("[simulator] Player count simulation started (%d-%d players)", MIN, MAX);
    }

    private void tick() {
        int change = ThreadLocalRandom.current().nextInt(7) - 3;
        int newVal = currentOnline + change;
        if (newVal < MIN) newVal = MIN;
        if (newVal > MAX) newVal = MAX;
        currentOnline = newVal;
        rebuildActivePlayers();
        Log.debug("[simulator] Online players adjusted to %d", currentOnline);
    }

    private void rebuildActivePlayers() {
        activePlayers.clear();
        usedNames.clear();
        List<String> pool = new ArrayList<>(Arrays.asList(FAKE_NAMES));
        Collections.shuffle(pool, ThreadLocalRandom.current());
        int count = Math.min(currentOnline, pool.size());
        for (int i = 0; i < count; i++) {
            String name = pool.get(i);
            usedNames.add(name);
            activePlayers.add(new AbstractMap.SimpleEntry<>(UUID.randomUUID(), name));
        }
    }

    public int getOnline() {
        return currentOnline;
    }

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

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void shutdown() {
        scheduler.shutdownNow();
    }
}
