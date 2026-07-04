package server.agents.capabilities.combat;

import org.junit.jupiter.api.Test;

import java.awt.Point;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AgentMobTouchStateTest {
    @Test
    void remembersPositionOnlyForCurrentMap() {
        AgentMobTouchState state = new AgentMobTouchState();

        assertNull(state.previousCheckPositionOnMap(100));

        state.rememberCheck(new Point(80, 200), 100);

        assertEquals(new Point(80, 200), state.previousCheckPositionOnMap(100));
        assertNull(state.previousCheckPositionOnMap(101));
    }

    @Test
    void returnsDefensivePositionCopy() {
        AgentMobTouchState state = new AgentMobTouchState();
        Point remembered = new Point(80, 200);

        state.rememberCheck(remembered, 100);
        remembered.x = 1;
        Point returned = state.previousCheckPositionOnMap(100);
        returned.x = 2;

        assertEquals(new Point(80, 200), state.previousCheckPositionOnMap(100));
    }

    @Test
    void nullPositionClearsMapAssociation() {
        AgentMobTouchState state = new AgentMobTouchState();

        state.rememberCheck(new Point(80, 200), 100);
        state.rememberCheck(null, 100);

        assertNull(state.lastCheckPosition());
        assertEquals(-1, state.lastCheckMapId());
    }
}
