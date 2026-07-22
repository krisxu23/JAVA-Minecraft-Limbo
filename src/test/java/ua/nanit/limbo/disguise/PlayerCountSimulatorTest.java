package ua.nanit.limbo.disguise;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlayerCountSimulatorTest {

    @Test
    void onlineCountWithinRange() throws Exception {
        PlayerCountSimulator sim = new PlayerCountSimulator(4, 20);
        sim.start();
        Thread.sleep(100);
        int online = sim.getOnline();
        assertTrue(online >= 4 && online <= 20, "Online " + online + " not in [4,20]");
        sim.stop();
    }

    @Test
    void zeroRange() throws Exception {
        PlayerCountSimulator sim = new PlayerCountSimulator(5, 5);
        sim.start();
        Thread.sleep(50);
        assertEquals(5, sim.getOnline());
        sim.stop();
    }
}