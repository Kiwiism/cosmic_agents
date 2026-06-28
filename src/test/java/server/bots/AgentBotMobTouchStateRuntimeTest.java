package server.bots;

import org.junit.jupiter.api.Test;
import server.agents.integration.AgentBotMobTouchStateRuntime;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentBotMobTouchStateRuntimeTest {
    @Test
    void adaptsSameMapMobTouchCheckpointState() {
        BotEntry entry = new BotEntry(null, null, null);

        assertNull(AgentBotMobTouchStateRuntime.previousCheckPositionOnMap(entry, 100));

        AgentBotMobTouchStateRuntime.rememberCheck(entry, new Point(80, 200), 100);

        assertEquals(new Point(80, 200), AgentBotMobTouchStateRuntime.previousCheckPositionOnMap(entry, 100));
        assertNull(AgentBotMobTouchStateRuntime.previousCheckPositionOnMap(entry, 101));
    }

    @Test
    void returnsDefensivePositionCopy() {
        BotEntry entry = new BotEntry(null, null, null);
        Point remembered = new Point(80, 200);

        AgentBotMobTouchStateRuntime.rememberCheck(entry, remembered, 100);
        remembered.x = 1;
        Point returned = AgentBotMobTouchStateRuntime.previousCheckPositionOnMap(entry, 100);
        returned.x = 2;

        assertEquals(new Point(80, 200), AgentBotMobTouchStateRuntime.previousCheckPositionOnMap(entry, 100));
    }
}
