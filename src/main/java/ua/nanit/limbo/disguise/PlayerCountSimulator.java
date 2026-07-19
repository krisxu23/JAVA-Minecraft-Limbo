package ua.nanit.limbo.disguise;

import java.security.SecureRandom;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simulates realistic player count fluctuations.
 * Ported from minewire/handler.go startPlayerCountSimulator.
 *
 * Smoothly varies the online count to make the server appear
 * to have real player activity with daily patterns.
 */
public final class PlayerCountSimulator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final int min;
    private final int max;
    private final int mid;
    private final AtomicInteger currentOnline;
    private ScheduledExecutorService scheduler;
    private int tickCount = 0;

    public PlayerCountSimulator(int min, int max) {
        this.min = min;
        this.max = max;
        this.mid = (min + max) / 2;
        this.currentOnline = new AtomicInteger(mid);
    }

    public void start() {
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "PlayerCountSim");
            t.setDaemon(true);
            return t;
        });
        // Update every 5-10 minutes for smoother transitions
        scheduler.scheduleAtFixedRate(this::update, 5, 5, TimeUnit.MINUTES);
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
        tickCount++;

        // Base: random walk of ±2
        int change = RANDOM.nextInt(5) - 2;

        // Every ~6 ticks (30 min): larger wave
        if (tickCount % 6 == 0) {
            change += RANDOM.nextInt(7) - 3; // additional -3 to +3
        }

        // Every ~12 ticks (60 min): peak/off-peak swing
        if (tickCount % 12 == 0) {
            // Push toward peak (upper half) or off-peak (lower half)
            if (RANDOM.nextBoolean()) {
                change += 2; // trending up
            } else {
                change -= 2; // trending down
            }
        }

        // Mean reversion: gently pull toward the midpoint
        int current = currentOnline.get();
        if (current > mid + 3) {
            change -= 1;
        } else if (current < mid - 3) {
            change += 1;
        }

        int newVal = current + change;
        newVal = Math.max(min, Math.min(max, newVal));
        currentOnline.set(newVal);
    }
}
