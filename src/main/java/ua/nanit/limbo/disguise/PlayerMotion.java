package ua.nanit.limbo.disguise;

import java.security.SecureRandom;

/**
 * Simulates realistic Minecraft player movement using random walk.
 * Ported from minewire/motion.go.
 *
 * Generates varied chunk coordinates so the server appears to have
 * a world with a moving player, not an empty void.
 */
public final class PlayerMotion {

    private static final int FIELD_SIZE = 2000;
    private static final int MIN_Y = 85;
    private static final int MAX_Y = 110;
    private static final SecureRandom RANDOM = new SecureRandom();

    public double x;
    public double y;
    public double z;
    public double angle;
    public double speed;

    public PlayerMotion() {
        this.x = RANDOM.nextInt(FIELD_SIZE);
        this.y = 95.0;
        this.z = RANDOM.nextInt(FIELD_SIZE);
        this.angle = RANDOM.nextDouble() * 2 * Math.PI;
        this.speed = 2.0 + RANDOM.nextDouble() * 3.0;
    }

    /**
     * Update position with random walk logic.
     * Call every ~2 seconds to simulate movement.
     */
    public void update() {
        // Small direction change
        angle += (RANDOM.nextDouble() - 0.5) * 0.3;

        // Occasional dramatic turn (5%)
        if (RANDOM.nextDouble() < 0.05) {
            angle += (RANDOM.nextDouble() - 0.5) * Math.PI;
        }

        // Occasional speed change (10%)
        if (RANDOM.nextDouble() < 0.1) {
            speed = 2.0 + RANDOM.nextDouble() * 3.0;
        }

        x += Math.cos(angle) * speed;
        z += Math.sin(angle) * speed;

        // Bounce off boundaries
        if (x < 0) { x = 0; angle = Math.PI - angle; }
        else if (x > FIELD_SIZE) { x = FIELD_SIZE; angle = Math.PI - angle; }
        if (z < 0) { z = 0; angle = -angle; }
        else if (z > FIELD_SIZE) { z = FIELD_SIZE; angle = -angle; }

        // Terrain height (gentle hills)
        double terrain = generateTerrainHeight(x, z);
        y += (terrain - y) * 0.2;
        if (y < MIN_Y) y = MIN_Y;
        else if (y > MAX_Y) y = MAX_Y;
    }

    public int getChunkX() { return (int) x >> 4; }
    public int getChunkZ() { return (int) z >> 4; }
    public int getBlockX() { return (int) x; }
    public int getBlockY() { return (int) y; }
    public int getBlockZ() { return (int) z; }

    private double generateTerrainHeight(double px, double pz) {
        double scale = 100.0;
        double h = MIN_Y + (MAX_Y - MIN_Y) / 2.0;
        h += Math.sin(px / scale) * 5.0 + Math.cos(pz / scale) * 5.0;
        h += Math.sin(px / (scale * 2)) * 3.0 + Math.cos(pz / (scale * 2)) * 3.0;
        h += Math.sin((px + pz) / (scale * 0.5)) * 2.0;
        return h;
    }
}
