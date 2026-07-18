package ua.nanit.limbo.disguise;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates realistic player count fluctuations.
 * Ported from minewire/handler.go startPlayerCountSimulator.
 *
 * Smoothly varies the online count every 30 minutes to make
 * the server appear to have real player activity.
 */
public final class PlayerCountSimulator {

    private final int min;
    private final int max;
    private final AtomicInteger currentOnline;
    private ScheduledExecutorService scheduler;

    public PlayerCountSimulator(int min, int max) {
        this.min = min;
        this.max = max;
        this.currentOnline = new AtomicInteger((min + max) / 2);
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PlayerCountSim");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(this::update, 30, 30, TimeUnit.MINUTES);
    }

    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
    }

    public int getOnline() {
        return currentOnline.get();
    }

    private void update() {
        Random rand = new Random();
        int change = rand.nextInt(7) - 3; // -3 to +3
        int newVal = currentOnline.get() + change;
        newVal = Math.max(min, Math.min(max, newVal));
        currentOnline.set(newVal);
    }
}
